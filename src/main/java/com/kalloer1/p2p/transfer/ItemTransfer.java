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
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

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

        List<Cap> inCaps = resolve(cache, levels, inputs, Capabilities.ItemHandler.BLOCK, true);
        if (inCaps.isEmpty()) return 0;
        // Cheap all-sources-empty gate: when no input holds any item, drop the whole round BEFORE
        // resolving even one output capability. An empty network must cost close to zero per tick.
        if (!anyInputHasItems(inCaps)) return 0;
        List<Cap> outCaps = resolve(cache, levels, outputs, Capabilities.ItemHandler.BLOCK, false);
        if (outCaps.isEmpty()) return 0;
        int outSize = outCaps.size();

        // 均分模式：先汇总本轮输入总量，再按输出端数量均分，一次性同时输出到所有输出端。
        // 走独立实现，避免和轮询/优先级共用一个"填满第一个再填下一个"的遍历顺序。
        if (channel.distribution == Channel.Distribution.SPLIT_EVENLY) {
            return splitEvenly(inCaps, outCaps, budget, globalTick);
        }

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

    // ============================ 均分模式 (SPLIT_EVENLY) ============================

    /**
     * 均分：对每一种物品，先统计本轮所有输入端可抽取的总量 total（受速率预算限制），
     * 再把 total 平均分给全部输出端 —— 每轮把"剩余待发量 / 剩余可接收输出端数"作为当前
     * 输出端的配额（余数摊到前几个端，起始下标按 globalTick 轮转，避免长期偏向同一端），
     * 于是一次 tick 内所有输出端会同时收到各自那一份。
     *
     * <p>某个输出端满了 / 被过滤挡下时它会被标记为关闭，它没吃下的份额会在同一轮里重新摊给
     * 其余仍有余量的输出端（而不是堆给最后一个端），所以只要下游还有空间就仍然是均分。
     *
     * <p>沿用 simulate-first：每个输出端先模拟能收多少，再按实际可收量真实抽取并插入，
     * 不会出现"抽 64 个只塞进去 32 个再退回去"的往返写入。
     */
    private static int splitEvenly(List<Cap> inCaps, List<Cap> outCaps, int budget, int globalTick) {
        int outSize = outCaps.size();
        if (outSize == 0 || budget <= 0) return 0;

        // ---- 1) 汇总货源：按物品类型分组，组内保持槽位顺序 ----
        List<ItemGroup> groups = new ArrayList<>();
        for (Cap cap : inCaps) {
            IItemHandler h = cap.handler;
            for (int slot = 0; slot < h.getSlots(); slot++) {
                ItemStack avail = h.extractItem(slot, budget, true);   // simulate
                if (avail.isEmpty()) continue;
                if (cap.filter != null && !cap.filter.test(avail)) continue;
                ItemGroup g = null;
                for (ItemGroup cand : groups) {
                    if (ItemStack.isSameItemSameComponents(cand.sample, avail)) { g = cand; break; }
                }
                if (g == null) { g = new ItemGroup(avail.copy()); groups.add(g); }
                g.lots.add(new Lot(cap, slot, avail.getCount()));
            }
        }
        if (groups.isEmpty()) return 0;

        int budgetLeft = budget;
        int totalMoved = 0;
        Set<Cap> dirty = new HashSet<>();
        int offset = (int) Math.floorMod(globalTick, (long) outSize);

        // ---- 2) 每种物品独立均分一轮 ----
        for (ItemGroup g : groups) {
            if (budgetLeft <= 0) break;
            int pool = 0;
            for (Lot l : g.lots) pool += l.count;
            if (pool <= 0) continue;
            if (pool > budgetLeft) pool = budgetLeft;

            boolean[] closed = new boolean[outSize];   // 本轮该物品已无法接收的输出端
            int[] cursor = {0};                        // 货源游标：当前抽到哪个槽位
            boolean dry = false;                       // 货源已抽空

            while (pool > 0 && !dry) {
                int open = 0;
                for (boolean c : closed) if (!c) open++;
                if (open == 0) break;
                int openLeft = open;
                boolean progressed = false;
                for (int k = 0; k < outSize && pool > 0; k++) {
                    int idx = (offset + k) % outSize;
                    if (closed[idx]) continue;
                    // 均分配额：剩余量 / 剩余可接收端数（向上取整，余数自然落在前几个端上）
                    int want = Math.min(pool, (pool + openLeft - 1) / openLeft);
                    openLeft--;
                    int moved = pushEven(g, outCaps.get(idx), cursor, want, dirty);
                    if (moved < 0) { dry = true; break; }        // 货源抽空，换下一种物品
                    if (moved > 0) {
                        pool -= moved;
                        budgetLeft -= moved;
                        totalMoved += moved;
                        progressed = true;
                    } else {
                        closed[idx] = true;                      // 满了 / 过滤器不匹配，份额让给别人
                    }
                }
                if (!progressed) break;
            }
        }

        for (Cap c : dirty) c.markChanged();
        return totalMoved;
    }

    /**
     * 为该物品从货源抽取至多 {@code want} 个并插入 {@code out}。
     * 返回实际转移数量；货源已抽空返回 -1；输出端收不下返回 0。
     */
    private static int pushEven(ItemGroup g, Cap out, int[] cursor, int want, Set<Cap> dirty) {
        if (want <= 0) return 0;
        if (out.filter != null && !out.filter.test(g.sample)) return 0;
        int canAccept = simulateAccept(out.handler, g.sample, want);
        if (canAccept <= 0) return 0;

        int need = canAccept;
        int moved = 0;
        while (need > 0 && cursor[0] < g.lots.size()) {
            Lot l = g.lots.get(cursor[0]);
            ItemStack sim = l.cap.handler.extractItem(l.slot, need, true);
            if (sim.isEmpty()) { cursor[0]++; continue; }
            ItemStack real = l.cap.handler.extractItem(l.slot, sim.getCount(), false);
            if (real.isEmpty()) { cursor[0]++; continue; }
            dirty.add(l.cap);
            ItemStack remainder = insertAll(out.handler, real, false);
            int inserted = real.getCount() - remainder.getCount();
            if (inserted < real.getCount()) {
                // 容量在模拟之后发生变化：把没放进去的部分退回原槽位，避免丢物品
                l.cap.handler.insertItem(l.slot, remainder.copy(), false);
            }
            moved += inserted;
            need -= inserted;
            if (inserted <= 0) break;      // 该输出端确实满了
        }
        if (moved > 0) dirty.add(out);
        if (moved == 0 && cursor[0] >= g.lots.size()) return -1;   // 货源耗尽
        return moved;
    }

    /** 模拟 {@code h} 能接收多少个 {@code template}（最多 {@code count} 个）。 */
    private static int simulateAccept(IItemHandler h, ItemStack template, int count) {
        ItemStack probe = template.copy();
        probe.setCount(count);
        ItemStack remainder = insertAll(h, probe, true);
        return count - remainder.getCount();
    }

    /** 同一类物品的所有货源槽位，sample 只用于类型比对与模拟插入。 */
    private static final class ItemGroup {
        final ItemStack sample;
        final List<Lot> lots = new ArrayList<>();
        ItemGroup(ItemStack sample) { this.sample = sample; }
    }

    /** 单个货源槽位；count 是采集阶段模拟出的可抽取量，仅用于估算本轮总量。 */
    private static final class Lot {
        final Cap cap;
        final int slot;
        final int count;
        Lot(Cap cap, int slot, int count) { this.cap = cap; this.slot = slot; this.count = count; }
    }

    /** Two-pass insert: merge into existing same-item stacks first, then use empty slots. */
    private static ItemStack insertAll(IItemHandler h, ItemStack stack, boolean simulate) {
        for (int i = 0; i < h.getSlots() && !stack.isEmpty(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, stack)) continue;
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
            if (ItemStack.isSameItemSameComponents(s, stack)
                    && s.getCount() < Math.min(h.getSlotLimit(i), stack.getMaxStackSize())) return false;
        }
        return true;
    }

    private static List<Cap> resolve(TransferCapabilityCache cache, Map<ResourceLocation, Level> levels,
                                     List<Member> members, BlockCapability<IItemHandler, Direction> cap, boolean isInput) {
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
