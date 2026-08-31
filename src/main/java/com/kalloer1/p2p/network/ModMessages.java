package com.kalloer1.p2p.network;

import com.kalloer1.p2p.P2P;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModMessages {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(P2P.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;
    private static int id() { return packetId++; }

    public static void register() {
        INSTANCE.messageBuilder(RequestChannelsC2SPacket.class, id())
                .encoder(RequestChannelsC2SPacket::toBytes)
                .decoder(RequestChannelsC2SPacket::new)
                .consumerMainThread(RequestChannelsC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(ChannelSyncS2CPacket.class, id())
                .encoder(ChannelSyncS2CPacket::toBytes)
                .decoder(ChannelSyncS2CPacket::new)
                .consumerMainThread(ChannelSyncS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(ChannelActionC2SPacket.class, id())
                .encoder(ChannelActionC2SPacket::toBytes)
                .decoder(ChannelActionC2SPacket::new)
                .consumerMainThread(ChannelActionC2SPacket::handle)
                .add();

        // —— 绑定模式（取代旧扳手）：按键进入，空手左右键绑定端点 ——
        INSTANCE.messageBuilder(BindModeC2SPacket.class, id())
                .encoder(BindModeC2SPacket::toBytes)
                .decoder(BindModeC2SPacket::new)
                .consumerMainThread(BindModeC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(BindClickC2SPacket.class, id())
                .encoder(BindClickC2SPacket::toBytes)
                .decoder(BindClickC2SPacket::new)
                .consumerMainThread(BindClickC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(SetBindChannelC2SPacket.class, id())
                .encoder(SetBindChannelC2SPacket::toBytes)
                .decoder(SetBindChannelC2SPacket::new)
                .consumerMainThread(SetBindChannelC2SPacket::handle)
                .add();
    }
}
