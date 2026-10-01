package com.kalloer1.p2p.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/** Server -> Client: deliver the serialized channel/network/group snapshot. */
public class ChannelSyncS2CPacket {
    private final CompoundTag data;

    public ChannelSyncS2CPacket(CompoundTag data) {
        this.data = data;
    }

    public ChannelSyncS2CPacket(FriendlyByteBuf buf) {
        this.data = buf.readNbt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(data);
    }

    public CompoundTag data() {
        return data;
    }

    public void handle(Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ModMessages.handleChannelSync(this));
    }
}
