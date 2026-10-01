package com.kalloer1.p2p.chunkload;

import com.kalloer1.p2p.P2P;
import com.kalloer1.p2p.P2PConfig;
import com.kalloer1.p2p.channel.Channel;
import com.kalloer1.p2p.channel.ChannelManager;
import com.kalloer1.p2p.channel.Member;
import com.kalloer1.p2p.channel.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.world.ForgeChunkManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 逐网络强制加载区块。机制（参照通量网络 Flux Networks 的 ForgeChunkManager 用法，按需求调整）：
 *
 * <ul>
 *   <li><b>票据</b>：{@link ForgeChunkManager#forceChunk}（ticking=true，等价等级 31 = 完全加载 +
 *       方块实体刻），owner 用网络 id，多网络可对同一区块叠加声明，互不干扰。</li>
 *   <li><b>卸载条件</b>：该网络的所有成员（含 owner）都离线 → 下一秒全部卸载。任一成员在线就保持加载。
 *       （ForgeChunkManager 的票据本身是持久的，重启后会短暂自动恢复，1 秒内的首个重算周期若无人
 *       在线会立刻移除。）</li>
 *   <li><b>开关链</b>：配置 {@code chunkload.enabled}（默认 false）→ 玩家权限（创造默认可用，
 *       {@code chunkload.allowSurvival}（默认 false）开放给生存）→ 网络 {@code chunkLoad} 开关。</li>
 *   <li><b>加载范围</b>：每个端点所在区块一张票（ticking 票会让该区块完整工作；邻接区块仅以
 *       "边框级"被动加载，不刻——这是原版票制的一部分，无法只加载单块还让它刻）。</li>
 * </ul>
 *
 * 每 20 tick 全量重算期望集合并与当前持有集合做差量增删：删除网络 / 解绑端点 / 掉线都会在
 * 下一个重算周期生效，无需各处单独回调。
 */
public final class ChunkLoadManager {
    /** 当前由我们挂着的票据：dim -> chunkPos(long)。diff 的左侧；owner 一律用声明它的网络 id。 */
    private static final Map<ResourceLocation, Set<Long>> held = new HashMap<>();
    private static int counter = 0;

    private ChunkLoadManager() {}

    /** ServerTickEvent(END)，每 20 tick 重算一次。 */
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

        // 差量应用：先卸不需要的，再挂新需要的（同一区块只挂一张票，多网络共享）。
        for (Map.Entry<ResourceLocation, Set<Long>> e : held.entrySet()) {
            ServerLevel level = level(server, e.getKey());
            if (level == null) continue;
            Set<Long> want = desired.getOrDefault(e.getKey(), Set.of());
            for (Long chunk : e.getValue()) {
                if (!want.contains(chunk)) {
                    ForgeChunkManager.forceChunk(level, P2P.MODID, BlockPos.ZERO,
                            ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false, true);
                }
            }
        }
        for (Map.Entry<ResourceLocation, Set<Long>> e : desired.entrySet()) {
            ServerLevel level = level(server, e.getKey());
            if (level == null) continue;
            Set<Long> have = held.getOrDefault(e.getKey(), Set.of());
            for (Long chunk : e.getValue()) {
                if (!have.contains(chunk)) {
                    // 先加载区块再挂票（Forge 要求挂票时区块可访问；forceChunk 内部也会处理）
                    ForgeChunkManager.forceChunk(level, P2P.MODID, BlockPos.ZERO,
                            ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true, true);
                }
            }
        }
        held.clear();
        desired.forEach((k, v) -> held.put(k, new HashSet<>(v)));
    }

    /** 服务器停止时清掉我们挂的所有票据（存档里已不再引用，重启后不会恢复）。 */
    public static void clearAll(MinecraftServer server) {
        for (Map.Entry<ResourceLocation, Set<Long>> e : held.entrySet()) {
            ServerLevel level = level(server, e.getKey());
            if (level == null) continue;
            for (Long chunk : e.getValue()) {
                ForgeChunkManager.forceChunk(level, P2P.MODID, BlockPos.ZERO,
                        ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false, true);
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
                // 脏数据/调试 UUID 不参与在线判定
            }
        }
        return false;
    }
}
