package com.kalloer1.p2p.chunkload;

import com.kalloer1.p2p.P2P;
import com.kalloer1.p2p.P2PConfig;
import com.kalloer1.p2p.channel.Channel;
import com.kalloer1.p2p.channel.ChannelManager;
import com.kalloer1.p2p.channel.Member;
import com.kalloer1.p2p.channel.Network;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-network chunk forcing using vanilla {@link ServerLevel#setChunkForced}.
 *
 * <ul>
 *   <li><b>Force/unforce</b>: vanilla persistent forced chunks — survives restart until explicitly removed.</li>
 *   <li><b>Unload condition</b>: all members of the network offline → unforce all chunks next cycle.</li>
 *   <li><b>Toggle chain</b>: config {@code chunkload.enabled} → permission → network {@code chunkLoad} toggle.</li>
 * </ul>
 *
 * Every 20 ticks: full recompute of desired set, diff against current held set, apply additions and removals.
 */
public final class ChunkLoadManager {
    /** dim -> set of forced chunk longs */
    private static final Map<ResourceLocation, Set<Long>> held = new HashMap<>();
    private static int counter = 0;

    private ChunkLoadManager() {}

    /** Called from ServerTickEvent.Post every tick; recalculates every 20 ticks. */
    public static void onServerTick(MinecraftServer server) {
        if (++counter % 20 != 0) return;
        Map<ResourceLocation, Set<Long>> desired = new HashMap<>();

        if (P2PConfig.chunkLoadEnabled.get()) {
            for (Network n : ChannelManager.INSTANCE.getAllNetworks()) {
                if (!n.chunkLoad || !anyMemberOnline(server, n)) continue;
                for (Channel c : ChannelManager.INSTANCE.getAllChannels()) {
                    if (c.networkId != n.id) continue;
                    for (Member m : c.members) {
                        desired.computeIfAbsent(m.dim, k -> new HashSet<>()).add(ChunkPos.asLong(m.pos));
                    }
                }
            }
        }

        // Diff: unforce chunks no longer needed
        for (Map.Entry<ResourceLocation, Set<Long>> e : held.entrySet()) {
            ServerLevel level = level(server, e.getKey());
            if (level == null) continue;
            Set<Long> want = desired.getOrDefault(e.getKey(), Set.of());
            for (Long chunk : e.getValue()) {
                if (!want.contains(chunk)) {
                    level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
                }
            }
        }
        // Diff: force newly needed chunks
        for (Map.Entry<ResourceLocation, Set<Long>> e : desired.entrySet()) {
            ServerLevel level = level(server, e.getKey());
            if (level == null) continue;
            Set<Long> have = held.getOrDefault(e.getKey(), Set.of());
            for (Long chunk : e.getValue()) {
                if (!have.contains(chunk)) {
                    level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true);
                }
            }
        }
        held.clear();
        desired.forEach((k, v) -> held.put(k, new HashSet<>(v)));
    }

    /** Unforce all chunks on server stop. */
    public static void clearAll(MinecraftServer server) {
        for (Map.Entry<ResourceLocation, Set<Long>> e : held.entrySet()) {
            ServerLevel level = level(server, e.getKey());
            if (level == null) continue;
            for (Long chunk : e.getValue()) {
                level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
            }
        }
        held.clear();
    }

    private static ServerLevel level(MinecraftServer server, ResourceLocation dim) {
        return server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dim));
    }

    private static boolean anyMemberOnline(MinecraftServer server, Network n) {
        for (String uuid : n.members) {
            try {
                if (server.getPlayerList().getPlayer(UUID.fromString(uuid)) != null) return true;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return false;
    }
}
