package com.kalloer1.p2p.network;

import com.kalloer1.p2p.P2P;
import com.kalloer1.p2p.client.ClientChannelCache;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> Client: deliver the serialized channel/network/group snapshot. */
public class ChannelSyncS2CPacket {
    private final CompoundTag data;

    public ChannelSyncS2CPacket(CompoundTag data) {
        this.data = data;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(data);
    }

    public ChannelSyncS2CPacket(FriendlyByteBuf buf) {
        this.data = buf.readNbt();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientChannelCache.INSTANCE.setData(data);
            P2P.LOGGER.info("[p2p] ChannelSyncS2CPacket (client): nets={} groups={} channels={}",
                    data.getList("networks", net.minecraft.nbt.Tag.TAG_COMPOUND).size(),
                    data.getList("groups", net.minecraft.nbt.Tag.TAG_COMPOUND).size(),
                    data.getList("channels", net.minecraft.nbt.Tag.TAG_COMPOUND).size());
        });
        ctx.get().setPacketHandled(true);
    }
}
