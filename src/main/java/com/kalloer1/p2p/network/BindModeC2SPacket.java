package com.kalloer1.p2p.network;

import com.kalloer1.p2p.P2P;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server: toggle bind mode on/off.
 * The server stores this flag to validate subsequent {@link BindClickC2SPacket}s
 * (prevents forged click packets when mode is inactive).
 */
public record BindModeC2SPacket(boolean active) implements CustomPacketPayload {
    public static final Type<BindModeC2SPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(P2P.MODID, "bind_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BindModeC2SPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, BindModeC2SPacket::active,
                    BindModeC2SPacket::new
            );

    @Override
    public Type<BindModeC2SPacket> type() {
        return TYPE;
    }
}
