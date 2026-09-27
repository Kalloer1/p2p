package com.kalloer1.p2p.transfer;

import com.kalloer1.p2p.channel.Channel;
import com.kalloer1.p2p.channel.Member;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Point-to-point energy transfer in FE. Energy has no filter. To avoid silently losing FE,
 * we only extract up to the combined receive capacity of the OUTPUT faces for this tick.
 *
 * <p>Perf model: each output's receive capacity is simulated ONCE per call and tracked as it is
 * consumed, instead of re-querying every output for every input (old behaviour was O(in × out)
 * simulate calls per tick). Only outputs that actually accepted FE are marked dirty, and each
 * touched endpoint is dirtied at most once per call.
 *
 * Returns the amount of FE actually moved this call, so the engine can drive backoff.
 */
public final class EnergyTransfer {
    private EnergyTransfer() {}

    public static int transfer(TransferCapabilityCache cache, Channel channel, Map<ResourceLocation, Level> levels,
                               List<Member> inputs, List<Member> outputs, int budget, int globalTick) {
        if (inputs.isEmpty() || outputs.isEmpty() || budget <= 0) return 0;

        List<Cap> inCaps = resolve(cache, levels, inputs);
        if (inCaps.isEmpty()) return 0;
        // Cheap all-sources-empty gate before resolving outputs: a drained network costs ~0/tick.
        if (!anyInputHasEnergy(inCaps)) return 0;
        List<Cap> outCaps = resolve(cache, levels, outputs);
        if (outCaps.isEmpty()) return 0;
        int outSize = outCaps.size();

        // 均分模式：本轮能量按输出端数量均分，同时输出到所有输出端。
        if (channel.distribution == Channel.Distribution.SPLIT_EVENLY) {
            return splitEvenly(inCaps, outCaps, budget, globalTick);
        }

        // PRIORITY: stable-sort outputs by endpoint priority once per call (highest first).
        if (channel.distribution == Channel.Distribution.PRIORITY) {
            outCaps.sort(Comparator.comparingInt((Cap c) -> c.priority).reversed());
        }

        // Pre-compute each output's current receive capacity once; decrement as it is filled.
        int[] outCap = new int[outSize];
        int totalCap = 0;
        for (int k = 0; k < outSize; k++) {
            Cap out = outCaps.get(k);
            if (!out.handler.canReceive()) continue;
            int cap = out.handler.receiveEnergy(Integer.MAX_VALUE, true);
            outCap[k] = cap;
            totalCap += cap;
        }
        if (totalCap <= 0) return 0;

        int remaining = budget;
        int totalMoved = 0;
        Set<Cap> dirty = new HashSet<>();
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
            if (!cap.handler.canExtract()) continue;
            int toExtract = Math.min(remaining, cap.handler.extractEnergy(remaining, true));
            toExtract = Math.min(toExtract, totalCap);
            if (toExtract <= 0) continue;
            int pulled = cap.handler.extractEnergy(toExtract, false);
            if (pulled <= 0) continue;
            int left = pulled;
            for (int k = 0; k < outSize && left > 0; k++) {
                if (outCap[k] <= 0) continue;
                Cap out = outCaps.get(k);
                if (!out.handler.canReceive()) continue;
                int filled = out.handler.receiveEnergy(left, false);
                if (filled > 0) {
                    outCap[k] -= filled;
                    totalCap -= filled;
                    dirty.add(out);
                    left -= filled;
                }
            }
            int moved = pulled - left;
            if (moved > 0) {
                dirty.add(cap);
                remaining -= moved;
                totalMoved += moved;
                if (remaining <= 0) break outer;
            }
        }

        for (Cap c : dirty) c.markChanged();
        return totalMoved;
    }

    // ============================ 均分模式 (SPLIT_EVENLY) ============================

    /**
     * 能量均分：先一次性测出每个输出端的接收上限，然后按"剩余待发量 / 剩余可接收输出端数"
     * 配额把本轮能量同时推向所有输出端；某端满了就把份额让给其余端，绝不超发（避免丢 FE）。
     */
    private static int splitEvenly(List<Cap> inCaps, List<Cap> outCaps, int budget, int globalTick) {
        int outSize = outCaps.size();
        if (outSize == 0 || budget <= 0) return 0;

        int[] outCap = new int[outSize];
        int totalCap = 0;
        for (int k = 0; k < outSize; k++) {
            Cap out = outCaps.get(k);
            if (!out.handler.canReceive()) continue;
            int c = out.handler.receiveEnergy(Integer.MAX_VALUE, true);
            outCap[k] = c;
            totalCap += c;
        }
        if (totalCap <= 0) return 0;

        int budgetLeft = budget;
        int totalMoved = 0;
        Set<Cap> dirty = new HashSet<>();
        int offset = (int) Math.floorMod(globalTick, (long) outSize);

        for (Cap src : inCaps) {
            if (budgetLeft <= 0) break;
            if (!src.handler.canExtract()) continue;
            int pool = Math.min(budgetLeft, src.handler.extractEnergy(budgetLeft, true));
            boolean[] closed = new boolean[outSize];
            while (pool > 0) {
                int open = 0;
                for (int k = 0; k < outSize; k++) if (!closed[k] && outCap[k] > 0) open++;
                if (open == 0) break;
                int openLeft = open;
                boolean progressed = false;
                for (int k = 0; k < outSize && pool > 0; k++) {
                    int idx = (offset + k) % outSize;
                    if (closed[idx] || outCap[idx] <= 0) continue;
                    int want = Math.min(pool, Math.min(outCap[idx], (pool + openLeft - 1) / openLeft));
                    openLeft--;
                    if (want <= 0) { closed[idx] = true; continue; }
                    int pulled = src.handler.extractEnergy(want, false);
                    if (pulled <= 0) { pool = 0; break; }
                    int filled = outCaps.get(idx).handler.receiveEnergy(pulled, false);
                    if (filled < pulled) {
                        // 接收端容量在模拟之后变化：把没进去的 FE 退回源，避免丢能量
                        src.handler.receiveEnergy(pulled - filled, false);
                        pool = 0;
                        break;
                    }
                    outCap[idx] -= filled;
                    dirty.add(outCaps.get(idx));
                    dirty.add(src);
                    pool -= filled;
                    budgetLeft -= filled;
                    totalMoved += filled;
                    progressed = true;
                }
                if (!progressed) break;
            }
        }

        for (Cap c : dirty) c.markChanged();
        return totalMoved;
    }

    /** True when any input can extract at least 1 FE (simulate-only cheap gate). */
    private static boolean anyInputHasEnergy(List<Cap> inCaps) {
        for (Cap cap : inCaps) {
            if (cap.handler.canExtract() && cap.handler.extractEnergy(1, true) > 0) return true;
        }
        return false;
    }

    private static List<Cap> resolve(TransferCapabilityCache cache, Map<ResourceLocation, Level> levels, List<Member> members) {
        List<Cap> out = new ArrayList<>();
        for (Member m : members) {
            Level level = levels.get(m.dim);
            if (level == null) continue;
            IEnergyStorage h = cache.get(level, m.pos, m.face, Capabilities.EnergyStorage.BLOCK);
            if (h == null) continue;
            out.add(new Cap(h, level, m.pos, m.priority));
        }
        return out;
    }

    private static final class Cap {
        final IEnergyStorage handler;
        final Level level;
        final BlockPos pos;
        final int priority;
        Cap(IEnergyStorage handler, Level level, BlockPos pos, int priority) {
            this.handler = handler; this.level = level; this.pos = pos; this.priority = priority;
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
