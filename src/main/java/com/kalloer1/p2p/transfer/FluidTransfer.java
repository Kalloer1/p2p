package com.kalloer1.p2p.transfer;

import com.kalloer1.p2p.channel.Channel;
import com.kalloer1.p2p.channel.Filter;
import com.kalloer1.p2p.channel.Member;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Point-to-point fluid transfer in mB. Pull from INPUT faces, push into OUTPUT faces, no buffer.
 *
 * <p>Same perf model as {@link ItemTransfer}: simulate each output's accepted mB FIRST, then really
 * drain exactly that amount (zero drain-and-return round-trip), skip outputs that are full for the
 * current fluid, and deduplicate markChanged so only endpoints that actually received fluid are
 * dirtied once per call.
 *
 * Returns the amount of fluid (mB) actually moved this call, so the engine can drive backoff.
 */
public final class FluidTransfer {
    private FluidTransfer() {}

    public static int transfer(TransferCapabilityCache cache, Channel channel, Map<ResourceLocation, Level> levels,
                               List<Member> inputs, List<Member> outputs, int budget, int globalTick) {
        if (inputs.isEmpty() || outputs.isEmpty() || budget <= 0) return 0;

        List<Cap> inCaps = resolve(cache, levels, inputs, ForgeCapabilities.FLUID_HANDLER, true);
        if (inCaps.isEmpty()) return 0;
        // Cheap all-sources-empty gate before resolving outputs: dry networks cost ~nothing per tick.
        if (!anyInputHasFluid(inCaps)) return 0;
        List<Cap> outCaps = resolve(cache, levels, outputs, ForgeCapabilities.FLUID_HANDLER, false);
        if (outCaps.isEmpty()) return 0;
        int outSize = outCaps.size();

        // PRIORITY: stable-sort outputs by endpoint priority once per call (highest first).
        if (channel.distribution == Channel.Distribution.PRIORITY) {
            outCaps.sort(Comparator.comparingInt((Cap c) -> c.priority).reversed());
        }

        int remaining = budget;
        int opCount = 0;
        int totalMoved = 0;
        Set<Cap> dirty = new HashSet<>();
        boolean[] outFull = new boolean[outSize];

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
            IFluidHandler h = cap.handler;
            FluidStack available = h.drain(remaining, IFluidHandler.FluidAction.SIMULATE);
            if (available.isEmpty()) continue;
            if (cap.filter != null && !cap.filter.test(available)) continue;

            // ---- Phase 1: simulate how much each open output accepts for this fluid ----
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
                int acc = out.handler.fill(available, IFluidHandler.FluidAction.SIMULATE);
                willAccept[idx] = acc;
                if (acc <= 0 && isFull(out.handler, available)) outFull[idx] = true;
                acceptable += acc;
                if (acceptable >= available.getAmount()) break;
            }
            if (acceptable <= 0) continue;
            if (acceptable > available.getAmount()) acceptable = available.getAmount();
            if (acceptable > remaining) acceptable = remaining;

            // ---- Phase 2: really drain ONLY the accepted amount ----
            FluidStack pulled = h.drain(acceptable, IFluidHandler.FluidAction.EXECUTE);
            if (pulled.isEmpty()) continue;

            // ---- Phase 3: really fill, in the same visiting order ----
            FluidStack left = pulled.copy();
            for (int k = 0; k < outSize && !left.isEmpty(); k++) {
                int idx = (start + k) % outSize;
                if (willAccept[idx] <= 0) continue;
                Cap out = outCaps.get(idx);
                if (out.filter != null && !out.filter.test(left)) continue;
                int filled = out.handler.fill(left, IFluidHandler.FluidAction.EXECUTE);
                if (filled > 0) {
                    dirty.add(out);
                    left.shrink(filled);
                }
            }
            int moved = pulled.getAmount() - left.getAmount();
            if (moved > 0) {
                if (left.getAmount() > 0) {
                    // Defensive: capacity changed mid-tick; restore what wasn't placed.
                    cap.handler.fill(left, IFluidHandler.FluidAction.EXECUTE);
                }
                dirty.add(cap);
                remaining -= moved;
                totalMoved += moved;
                opCount++;
                if (remaining <= 0) break outer;
            }
        }

        for (Cap c : dirty) c.markChanged();
        return totalMoved;
    }

    /** True when any input handler can drain at least 1 mB (simulate-only cheap gate). */
    private static boolean anyInputHasFluid(List<Cap> inCaps) {
        for (Cap cap : inCaps) {
            if (!cap.handler.drain(1, IFluidHandler.FluidAction.SIMULATE).isEmpty()) return true;
        }
        return false;
    }

    /** True when the handler cannot accept any more of {@code stack}: no usable empty tank, no mergeable tank. */
    private static boolean isFull(IFluidHandler h, FluidStack stack) {
        for (int i = 0; i < h.getTanks(); i++) {
            FluidStack s = h.getFluidInTank(i);
            if (s.isEmpty() && h.getTankCapacity(i) > 0) return false;
            if (s.getFluid() == stack.getFluid() && Objects.equals(s.getTag(), stack.getTag())
                    && s.getAmount() < h.getTankCapacity(i)) return false;
        }
        return true;
    }

    private static List<Cap> resolve(TransferCapabilityCache cache, Map<ResourceLocation, Level> levels,
                                     List<Member> members, Capability<IFluidHandler> cap, boolean isInput) {
        List<Cap> out = new ArrayList<>();
        for (Member m : members) {
            Level level = levels.get(m.dim);
            if (level == null) continue;
            IFluidHandler h = cache.get(level, m.pos, m.face, cap);
            if (h == null) continue;
            Filter f = isInput ? m.extractFilter : m.insertFilter;
            out.add(new Cap(h, f, level, m.pos, m.priority));
        }
        return out;
    }

    private static final class Cap {
        final IFluidHandler handler;
        final Filter filter;
        final Level level;
        final BlockPos pos;
        final int priority;
        Cap(IFluidHandler handler, Filter filter, Level level, BlockPos pos, int priority) {
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