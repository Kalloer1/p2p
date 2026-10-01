package com.kalloer1.p2p.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * Client -> Server: set the player's current bound channel.
 * (The channel targeted by empty-hand left/right clicks in bind mode, replacing the old wrench binding.)
 */
public class SetBindChannelC2SPacket {
    private final int channelId;

    public SetBindChannelC2SPacket(int channelId) {
        this.channelId = channelId;
    }

    public SetBindChannelC2SPacket(FriendlyByteBuf buf) {
        this.channelId = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(channelId);
    }

    public int channelId() {
        return channelId;
    }

    public void handle(Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ModMessages.handleSetBindChannel(this, ctx.get().getSender()));
    }
}
