package com.kalloer1.p2p.network;

import com.kalloer1.p2p.P2P;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server: a bind-click while bind mode is active.
 * <ul>
 *   <li>{@code rightButton=true} (right-click) = set as INPUT; {@code false} (left-click) = set as OUTPUT.</li>
 *   <li>{@code control=false}: single-click = toggle bind/unbind on that face.</li>
 *   <li>{@code control=true}: flood-fill 6 neighbours from that block, chain bind/unbind identical blocks.</li>
 * </ul>
 */
public record BindClickC2SPacket(BlockPos pos, Direction face, boolean rightButton, boolean control)
        implements CustomPacketPayload {

    public static final Type<BindClickC2SPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(P2P.MODID, "bind_click"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BindClickC2SPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        BlockPos.STREAM_CODEC.encode(buf, pkt.pos);
                        buf.writeVarInt(pkt.face.ordinal());
                        buf.writeBoolean(pkt.rightButton);
                        buf.writeBoolean(pkt.control);
                    },
                    buf -> new BindClickC2SPacket(
                            BlockPos.STREAM_CODEC.decode(buf),
                            Direction.values()[buf.readVarInt()],
                            buf.readBoolean(),
                            buf.readBoolean()
                    )
            );

    @Override
    public Type<BindClickC2SPacket> type() {
        return TYPE;
    }
}
