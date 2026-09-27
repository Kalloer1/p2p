package com.kalloer1.p2p.network;

import com.kalloer1.p2p.P2P;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server: set the player's current bound channel.
 * (The channel targeted by empty-hand left/right clicks in bind mode, replacing the old wrench binding.)
 */
public record SetBindChannelC2SPacket(int channelId) implements CustomPacketPayload {
    public static final Type<SetBindChannelC2SPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(P2P.MODID, "set_bind_channel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetBindChannelC2SPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SetBindChannelC2SPacket::channelId,
                    SetBindChannelC2SPacket::new
            );

    @Override
    public Type<SetBindChannelC2SPacket> type() {
        return TYPE;
    }
}
