package com.kalloer1.p2p.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * 本体刚为某玩家生成完整频道快照（{@code ChannelManager.serializeFor}，pushSync /
 * 登录 / RequestChannels / action 回包等所有 S2C 快照的共同漏斗）后派发，服务主线程。
 *
 * <p>用途：附属借"本体即将给该玩家发快照"这一时机搭车推送自己的附加数据
 * （通过附属自己注册的 payload 通道；本体协议不变）。{@link #snapshot} 本体不会
 * 再改动，附属只读即可；向其中写入未约定字段属于未定义行为。
 */
public class ChannelSyncedEvent extends Event {
    public final ServerPlayer player;
    /** 即将发给该玩家的完整快照 NBT（只读约定）。 */
    public final CompoundTag snapshot;

    public ChannelSyncedEvent(ServerPlayer player, CompoundTag snapshot) {
        this.player = player;
        this.snapshot = snapshot;
    }
}
