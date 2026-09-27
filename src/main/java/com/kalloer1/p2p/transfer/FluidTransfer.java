package com.kalloer1.p2p.transfer;

import com.kalloer1.p2p.channel.Channel;
import com.kalloer1.p2p.channel.Filter;
import com.kalloer1.p2p.channel.Member;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

        List<Cap> inCaps = resolve(cache, levels, inputs, Capabilities.FluidHandler.BLOCK, true);
        if (inCaps.isEmpty()) return 0;
        // Cheap all-sources-empty gate before resolving outputs: dry networks cost ~nothing per tick.
        if (!anyInputHasFluid(inCaps)) return 0;
        List<Cap> outCaps = resolve(cache, levels, outputs, Capabilities.FluidHandler.BLOCK, false);
        if (outCaps.isEmpty()) return 0;
        int outSize = outCaps.size();

        // 均分模式：本轮可抽取量按输出端数量均分，同时输出到所有输出端。
        if (channel.distribution == Channel.Distribution.SPLIT_EVENLY) {
            return splitEvenly(inCaps, outCaps, budget, globalTick);
        }

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

    // ============================ 均分模式 (SPLIT_EVENLY) ============================

    /**
     * 流体均分：与物品版同构 —— 逐个输入端取本轮可抽取量，按"剩余待发量 / 剩余可接收输出端数"
     * 配额同时推向所有输出端；某端满了就把份额让给其余端。每个输入端独立计算（不同输入端可能是不同流体）。
     */
    private static int splitEvenly(List<Cap> inCaps, List<Cap> outCaps, int budget, int globalTick) {
        int outSize = outCaps.size();
        if (outSize == 0 || budget <= 0) return 0;

        int budgetLeft = budget;
        int totalMoved = 0;
        Set<Cap> dirty = new HashSet<>();
        int offset = (int) Math.floorMod(globalTick, (long) outSize);

        for (Cap src : inCaps) {
            if (budgetLeft <= 0) break;
            FluidStack avail = src.handler.drain(budgetLeft, IFluidHandler.FluidAction.SIMULATE);
            if (avail.isEmpty()) continue;
            if (src.filter != null && !src.filter.test(avail)) continue;

            int pool = avail.getAmount();
            boolean[] closed = new boolean[outSize];
            while (pool > 0) {
                int open = 0;
                for (boolean c : closed) if (!c) open++;
                if (open == 0) break;
                int openLeft = open;
                boolean progressed = false;
                for (int k = 0; k < outSize && pool > 0; k++) {
                    int idx = (offset + k) % outSize;
                    if (closed[idx]) continue;
                    int want = Math.min(pool, (pool + openLeft - 1) / openLeft);
                    openLeft--;
                    int moved = pushEven(src, outCaps.get(idx), avail, want, dirty);
                    if (moved > 0) { pool -= moved; budgetLeft -= moved; totalMoved += moved; progressed = true; }
                    else closed[idx] = true;
                }
                if (!progressed) break;
            }
            dirty.add(src);
        }

        for (Cap c : dirty) c.markChanged();
        return totalMoved;
    }

    /** 从 {@code src} 抽取至多 {@code want} mB 并灌入 {@code out}，返回实际转移量。 */
    private static int pushEven(Cap src, Cap out, FluidStack template, int want, Set<Cap> dirty) {
        if (want <= 0) return 0;
        if (out.filter != null && !out.filter.test(template)) return 0;
        FluidStack probe = template.copy();
        probe.setAmount(want);
        int canAccept = out.handler.fill(probe, IFluidHandler.FluidAction.SIMULATE);
        if (canAccept <= 0) return 0;

        FluidStack drained = src.handler.drain(canAccept, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) return 0;
        int filled = out.handler.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        if (filled < drained.getAmount()) {
            // 容量在模拟之后变化：把没灌进去的部分退回源，避免丢流体
            FluidStack back = drained.copy();
            back.setAmount(drained.getAmount() - filled);
            src.handler.fill(back, IFluidHandler.FluidAction.EXECUTE);
        }
        if (filled > 0) dirty.add(out);
        return filled;
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
            if (FluidStack.isSameFluidSameComponents(s, stack)
                    && s.getAmount() < h.getTankCapacity(i)) return false;
        }
        return true;
    }

    private static List<Cap> resolve(TransferCapabilityCache cache, Map<ResourceLocation, Level> levels,
                                     List<Member> members, BlockCapability<IFluidHandler, Direction> cap, boolean isInput) {
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
