package com.kalloer1.p2p.network;

import com.kalloer1.p2p.P2P;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> Server: request a full channel sync (sent when the GUI opens). */
public record RequestChannelsC2SPacket() implements CustomPacketPayload {
    public static final Type<RequestChannelsC2SPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(P2P.MODID, "request_channels"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestChannelsC2SPacket> STREAM_CODEC =
            StreamCodec.unit(new RequestChannelsC2SPacket());

    @Override
    public Type<RequestChannelsC2SPacket> type() {
        return TYPE;
    }
}
