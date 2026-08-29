package com.kalloer1.p2p.network;

import com.kalloer1.p2p.P2P;
import com.kalloer1.p2p.item.ChannelWrench;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> Server: the player right-clicked a block while holding the P2P wrench. Carries the live
 * shift/control modifier state, which the server cannot read from ServerPlayer (PlayerInteractEvent has no
 * modifier API in Forge 1.20.1), so the server can decide whether to toggle mode, bind a single endpoint,
 * or chain-bind all matching machines in radius.
 */
public class WrenchUseC2SPacket {
    private final BlockPos pos;
    private final Direction face;
    private final boolean shift;
    private final boolean control;

    public WrenchUseC2SPacket(BlockPos pos, Direction face, boolean shift, boolean control) {
        this.pos = pos;
        this.face = face;
        this.shift = shift;
        this.control = control;
    }

    public WrenchUseC2SPacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readEnum(Direction.class), buf.readBoolean(), buf.readBoolean());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeEnum(face);
        buf.writeBoolean(shift);
        buf.writeBoolean(control);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!(player.getMainHandItem().getItem() instanceof ChannelWrench)) {
                P2P.LOGGER.warn("[p2p] WrenchUseC2SPacket ignored: sender not holding wrench");
                return;
            }
            P2P.LOGGER.info("[p2p] WrenchUseC2SPacket (server): player={} pos={} face={} shift={} ctrl={}",
                    player.getName().getString(), pos, face, shift, control);
            ChannelWrench.doWrench(player, pos, face, player.getMainHandItem(), shift, control);
        });
        ctx.get().setPacketHandled(true);
    }
}
