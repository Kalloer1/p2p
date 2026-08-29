package com.kalloer1.p2p.transfer;

import com.kalloer1.p2p.channel.Channel;
import com.kalloer1.p2p.channel.Member;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

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
                               List<Member> inputs, List<Member> outputs, int budget) {
        if (inputs.isEmpty() || outputs.isEmpty() || budget <= 0) return 0;

        List<Cap> inCaps = resolve(cache, levels, inputs);
        if (inCaps.isEmpty()) return 0;
        // Cheap all-sources-empty gate before resolving outputs: a drained network costs ~0/tick.
        if (!anyInputHasEnergy(inCaps)) return 0;
        List<Cap> outCaps = resolve(cache, levels, outputs);
        if (outCaps.isEmpty()) return 0;
        int outSize = outCaps.size();

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
            IEnergyStorage h = cache.get(level, m.pos, m.face, ForgeCapabilities.ENERGY);
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