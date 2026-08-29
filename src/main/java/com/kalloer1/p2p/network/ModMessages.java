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

        INSTANCE.messageBuilder(BindWrenchC2SPacket.class, id())
                .encoder(BindWrenchC2SPacket::toBytes)
                .decoder(BindWrenchC2SPacket::new)
                .consumerMainThread(BindWrenchC2SPacket::handle)
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

        INSTANCE.messageBuilder(WrenchUseC2SPacket.class, id())
                .encoder(WrenchUseC2SPacket::toBytes)
                .decoder(WrenchUseC2SPacket::new)
                .consumerMainThread(WrenchUseC2SPacket::handle)
                .add();
    }
}
