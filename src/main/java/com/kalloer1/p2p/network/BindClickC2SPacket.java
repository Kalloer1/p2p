package com.kalloer1.p2p.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * Client -> Server: a bind-click while bind mode is active.
 * <ul>
 *   <li>{@code rightButton=true} (right-click) = set as INPUT; {@code false} (left-click) = set as OUTPUT.</li>
 *   <li>{@code control=false}: single-click = toggle bind/unbind on that face.</li>
 *   <li>{@code control=true}: flood-fill 6 neighbours from that block, chain bind/unbind identical blocks.</li>
 * </ul>
 */
public class BindClickC2SPacket {
    private final BlockPos pos;
    private final Direction face;
    private final boolean rightButton;
    private final boolean control;

    public BindClickC2SPacket(BlockPos pos, Direction face, boolean rightButton, boolean control) {
        this.pos = pos;
        this.face = face;
        this.rightButton = rightButton;
        this.control = control;
    }

    public BindClickC2SPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.face = Direction.values()[buf.readVarInt()];
        this.rightButton = buf.readBoolean();
        this.control = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(face.ordinal());
        buf.writeBoolean(rightButton);
        buf.writeBoolean(control);
    }

    public BlockPos pos() { return pos; }
    public Direction face() { return face; }
    public boolean rightButton() { return rightButton; }
    public boolean control() { return control; }

    public void handle(Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ModMessages.handleBindClick(this, ctx.get().getSender()));
    }
}
