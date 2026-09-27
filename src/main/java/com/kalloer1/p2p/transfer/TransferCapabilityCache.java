package com.kalloer1.p2p.transfer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-tick cache of resolved block capabilities.
 *
 * <p>Before this cache, the engine re-resolved every member's capability
 * ({@code level.getCapability()}) on every visit. That is O(channels x members)
 * relatively expensive capability lookups per tick and is the main reason throughput collapsed as
 * a network grew. This cache resolves a capability at most once per tick and stores a sentinel for
 * "absent" results, so a missing capability is never re-queried within the same tick.
 *
 * <p>Create one instance per server tick (shared across all channels) and discard it afterwards.
 * Sharing across channels additionally lets a neighbour that is linked by several channels resolve
 * only once per tick. Within a single server tick the world is not mutated between channels, so the
 * cached handler stays valid for the whole tick.
 */
public final class TransferCapabilityCache {
    private final Map<Key, Object> resolved = new HashMap<>();
    private static final Object ABSENT = new Object();

    @SuppressWarnings("unchecked")
    public <T> T get(Level level, BlockPos pos, Direction face, BlockCapability<T, ?> cap) {
        Key key = new Key(level.dimension().location(), pos.asLong(), face, cap);
        Object cached = resolved.get(key);
        if (cached != null) return cached == ABSENT ? null : (T) cached;
        // The wildcard capture ? in BlockCapability<T, ?> prevents direct type inference for the
        // context parameter.  Cast to a concrete Direction-typed capability to satisfy the compiler.
        BlockCapability<T, Direction> dirCap = (BlockCapability<T, Direction>) (BlockCapability<?, ?>) cap;
        T handler = level.getCapability(dirCap, pos, face);
        resolved.put(key, handler != null ? handler : ABSENT);
        return handler;
    }

    /** Composite cache key: dimension + packed position + side + capability type. */
    private record Key(ResourceLocation dim, long pos, Direction face, BlockCapability<?, ?> cap) {
    }
}
