package com.kalloer1.p2p.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/** Client -> Server: request a full channel sync (sent when the GUI opens). */
public class RequestChannelsC2SPacket {

    public RequestChannelsC2SPacket() {}

    public RequestChannelsC2SPacket(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public void handle(Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ModMessages.handleRequestChannels(this, ctx.get().getSender()));
    }
}
