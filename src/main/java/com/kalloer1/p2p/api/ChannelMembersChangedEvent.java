package com.kalloer1.p2p.api;

import com.kalloer1.p2p.channel.Channel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

/**
 * 频道的成员集合（增/删/角色/朝向/优先级）在服务端刚被改动后派发，服务主线程。
 * 覆盖路径：{@code ChannelManager} 的 toggleEndpoint / bindChain / unbindChain /
 * unbindPositions / removeAirMembers，以及 {@code ChannelActionC2SPacket.apply} 的
 * SET_ROLE / SET_FACE / REMOVE_MEMBER / SET_PRIORITY 分支（GUI 直改 members 的路径）。
 *
 * <p>语义是"该频道的 members 可能变了，请重算"，不是精确 diff：附属按
 * {@code channel.members} 终态做 prune（剔除孤儿键）即可，多种 kind 同时到达也应幂等。
 *
 * <p>注册方式：附属在 mod 构造函数里 {@code MinecraftForge.EVENT_BUS.addListener(...)}（游戏总线）。
 */
public class ChannelMembersChangedEvent extends Event {
    /** 变更来源（仅作提示，附属按重算处理即可；未来新增动作会归入 {@link ChangeKind#OTHER}）。 */
    public enum ChangeKind {
        ENDPOINT_TOGGLE, CHAIN_BIND, CHAIN_UNBIND, BATCH_UNBIND, CLEAN_AIR,
        ROLE_CHANGED, FACE_CHANGED, MEMBER_REMOVED, PRIORITY_CHANGED, OTHER
    }

    /** 触发变更的玩家；命令/内部路径触发时为 null。 */
    @Nullable
    public final ServerPlayer actor;
    /** 变更发生所在的层级；无玩家上下文（批量解绑）时可为 null。 */
    @Nullable
    public final Level level;
    /** 变更后的频道，members 已是终态。 */
    public final Channel channel;
    public final ChangeKind kind;

    public ChannelMembersChangedEvent(@Nullable ServerPlayer actor, @Nullable Level level,
                                      Channel channel, ChangeKind kind) {
        this.actor = actor;
        this.level = level;
        this.channel = channel;
        this.kind = kind;
    }
}
