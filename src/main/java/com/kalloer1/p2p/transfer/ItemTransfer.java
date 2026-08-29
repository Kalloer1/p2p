package com.kalloer1.p2p.transfer;

import com.kalloer1.p2p.channel.Channel;
import com.kalloer1.p2p.channel.Filter;
import com.kalloer1.p2p.channel.Member;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Point-to-point item transfer: pull from INPUT faces, push into OUTPUT faces, no buffer.
 * Respects per-member filters and the channel's per-tick rate budget.
 *
 * <p>Perf model (compared with the old extract-everything-then-return loop):
 * <ol>
 *   <li><b>Simulate-first, extract-what-fits</b>: each slot is first simulated against every open
 *       output; the source is then REALLY extracted by exactly the amount the outputs accepted.
 *       No "pull 64 then stuff 32 back" round-trip, so each moved item costs one real source
 *       write instead of two.</li>
 *   <li><b>Full-output skipping</b>: an output that cannot take any more of a stack type is marked
 *       closed for the rest of this call, so it is never re-simulated.</li>
 *   <li><b>Merge-then-empty two-pass insert</b>: stacks merge into existing same-item stacks first,
 *       then use empty slots, avoiding slot fragmentation.</li>
 *   <li><b>Deduplicated markChanged</b>: every block that was actually written is marked exactly
 *       once per call, instead of marking ALL outputs after every single slot move.</li>
 * </ol>
 *
 * Returns the number of items actually moved this call, so the engine can drive adaptive backoff.
 */
public final class ItemTransfer {
    private ItemTransfer() {}

    public static int transfer(TransferCapabilityCache cache, Channel channel, Map<ResourceLocation, Level> levels,
                               List<Member> inputs, List<Member> outputs, int budget, int globalTick) {
        if (inputs.isEmpty() || outputs.isEmpty() || budget <= 0) return 0;

        List<Cap> inCaps = resolve(cache, levels, inputs, ForgeCapabilities.ITEM_HANDLER, true);
        if (inCaps.isEmpty()) return 0;
        // Cheap all-sources-empty gate: when no input holds any item, drop the whole round BEFORE
        // resolving even one output capability. An empty network must cost close to zero per tick.
        if (!anyInputHasItems(inCaps)) return 0;
        List<Cap> outCaps = resolve(cache, levels, outputs, ForgeCapabilities.ITEM_HANDLER, false);
        if (outCaps.isEmpty()) return 0;
        int outSize = outCaps.size();

        // PRIORITY: stable-sort outputs by endpoint priority once per call (highest first).
        if (channel.distribution == Channel.Distribution.PRIORITY) {
            outCaps.sort(Comparator.comparingInt((Cap c) -> c.priority).reversed());
        }

        int remaining = budget;
        int opCount = 0;     // successful slot pulls, used to spread round-robin distribution
        int totalMoved = 0;  // items moved this call (return value)
        Set<Cap> dirty = new HashSet<>();
        boolean[] outFull = new boolean[outSize];   // outputs closed for this call (cannot take this stack type)

        outer:
        for (Cap cap : inCaps) {
            // NEAREST / FARTHEST: order outputs by their distance to the current input face.
            if (channel.distribution == Channel.Distribution.NEAREST
                    || channel.distribution == Channel.Distribution.FARTHEST) {
                boolean nearest = channel.distribution == Channel.Distribution.NEAREST;
                                outCaps.sort((a, b) -> {
                                    double da = a.pos.distSqr(cap.pos);
                                    double db = b.pos.distSqr(cap.pos);
                                    return nearest ? Double.compare(da, db) : Double.compare(db, da);
                                });
            }
            IItemHandler h = cap.handler;
            for (int slot = 0; slot < h.getSlots() && remaining > 0; slot++) {
                ItemStack available = h.extractItem(slot, remaining, true); // simulate
                if (available.isEmpty()) continue;
                if (cap.filter != null && !cap.filter.test(available)) continue;

                // ---- Phase 1: simulate how much each open output accepts for this stack ----
                int[] willAccept = new int[outSize];
                int acceptable = 0;
                int start = switch (channel.distribution) {
                    case PRIORITY, NEAREST, FARTHEST -> 0;
                    default -> (globalTick + opCount) % outSize;   // ROUND_ROBIN
                };
                for (int k = 0; k < outSize; k++) {
                    int idx = (start + k) % outSize;
                    Cap out = outCaps.get(idx);
                    if (outFull[idx]) continue;
                    if (out.filter != null && !out.filter.test(available)) { willAccept[idx] = -1; continue; }
                    ItemStack simRemainder = insertAll(out.handler, available, true);
                    int acc = available.getCount() - simRemainder.getCount();
                    willAccept[idx] = acc;
                    if (acc <= 0 && isFull(out.handler, available)) outFull[idx] = true;
                    acceptable += acc;
                    if (acceptable >= available.getCount()) break; // everything fits already
                }
                if (acceptable <= 0) continue;
                if (acceptable > available.getCount()) acceptable = available.getCount();
                if (acceptable > remaining) acceptable = remaining;

                // ---- Phase 2: really extract ONLY what the outputs accepted (zero round-trip) ----
                ItemStack pulled = h.extractItem(slot, acceptable, false);
                if (pulled.isEmpty()) continue;

                // ---- Phase 3: really insert, in the same visiting order ----
                ItemStack left = pulled;
                for (int k = 0; k < outSize && !left.isEmpty(); k++) {
                    int idx = (start + k) % outSize;
                    if (willAccept[idx] <= 0) continue;
                    Cap out = outCaps.get(idx);
                    if (out.filter != null && !out.filter.test(left)) continue;
                    ItemStack remainder = insertAll(out.handler, left, false);
                    if (remainder.getCount() < left.getCount()) dirty.add(out);
                    left = remainder;
                }
                int moved = pulled.getCount() - left.getCount();
                if (moved > 0) {
                    if (!left.isEmpty()) {
                        // Defensive: capacity changed mid-tick (should not happen); restore unplaced items.
                        cap.handler.insertItem(slot, left, false);
                    }
                    dirty.add(cap);
                    remaining -= moved;
                    totalMoved += moved;
                    opCount++;
                    if (remaining <= 0) break outer;
                }
            }
        }

        for (Cap c : dirty) c.markChanged();
        return totalMoved;
    }

    /** Two-pass insert: merge into existing same-item stacks first, then use empty slots. */
    private static ItemStack insertAll(IItemHandler h, ItemStack stack, boolean simulate) {
        for (int i = 0; i < h.getSlots() && !stack.isEmpty(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (s.isEmpty() || !ItemStack.isSameItemSameTags(s, stack)) continue;
            stack = h.insertItem(i, stack, simulate);
        }
        for (int i = 0; i < h.getSlots() && !stack.isEmpty(); i++) {
            if (!h.getStackInSlot(i).isEmpty()) continue;
            stack = h.insertItem(i, stack, simulate);
        }
        return stack;
    }

    /** True when any input handler has at least one extractable item (simulate-only cheap gate). */
    private static boolean anyInputHasItems(List<Cap> inCaps) {
        for (Cap cap : inCaps) {
            IItemHandler h = cap.handler;
            for (int slot = 0; slot < h.getSlots(); slot++) {
                if (!h.extractItem(slot, 1, true).isEmpty()) return true;
            }
        }
        return false;
    }

    /** True when the handler cannot accept any more of {@code stack}: no empty slot, no mergeable slot. */
    private static boolean isFull(IItemHandler h, ItemStack stack) {
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (s.isEmpty()) return false;
            if (ItemStack.isSameItemSameTags(s, stack)
                    && s.getCount() < Math.min(h.getSlotLimit(i), stack.getMaxStackSize())) return false;
        }
        return true;
    }

    private static List<Cap> resolve(TransferCapabilityCache cache, Map<ResourceLocation, Level> levels,
                                     List<Member> members, Capability<IItemHandler> cap, boolean isInput) {
        List<Cap> out = new ArrayList<>();
        for (Member m : members) {
            Level level = levels.get(m.dim);
            if (level == null) continue;
            IItemHandler h = cache.get(level, m.pos, m.face, cap);
            if (h == null) continue;
            Filter f = isInput ? m.extractFilter : m.insertFilter;
            out.add(new Cap(h, f, level, m.pos, m.priority));
        }
        return out;
    }

    private static final class Cap {
        final IItemHandler handler;
        final Filter filter;
        final Level level;
        final BlockPos pos;
        final int priority;
        Cap(IItemHandler handler, Filter filter, Level level, BlockPos pos, int priority) {
            this.handler = handler; this.filter = filter; this.level = level; this.pos = pos; this.priority = priority;
        }
        void markChanged() {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) be.setChanged();
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Cap c)) return false;
            return pos.equals(c.pos) && level == c.level;
        }
        @Override
        public int hashCode() {
            return pos.hashCode() * 31 + System.identityHashCode(level);
        }
    }
}