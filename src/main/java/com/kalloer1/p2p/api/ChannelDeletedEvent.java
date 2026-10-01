package com.kalloer1.p2p.api;

import com.kalloer1.p2p.channel.Channel;
import net.minecraftforge.eventbus.api.Event;

/**
 * 频道被删除后在服务主线程派发（P2P 本体 {@code ChannelManager.deleteChannel}）。
 * {@link #channel} 已从路由表移除，但对象仍完整（members / type / id 可读），
 * 附属据此清理自己按 channelId 键控的运行时/持久化状态。
 *
 * <p>注册方式：附属在 mod 构造函数里 {@code MinecraftForge.EVENT_BUS.addListener(...)}（游戏总线）。
 */
public class ChannelDeletedEvent extends Event {
    /** 刚被删除的频道（快照语义：不会再被本体改动）。 */
    public final Channel channel;

    public ChannelDeletedEvent(Channel channel) {
        this.channel = channel;
    }
}
