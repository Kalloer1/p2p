package com.kalloer1.p2p.network;

import com.kalloer1.p2p.P2P;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> Client: deliver the serialized channel/network/group snapshot. */
public record ChannelSyncS2CPacket(CompoundTag data) implements CustomPacketPayload {
    public static final Type<ChannelSyncS2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(P2P.MODID, "channel_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChannelSyncS2CPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.COMPOUND_TAG, ChannelSyncS2CPacket::data,
                    ChannelSyncS2CPacket::new
            );

    @Override
    public Type<ChannelSyncS2CPacket> type() {
        return TYPE;
    }
}
