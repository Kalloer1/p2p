package com.kalloer1.p2p.network;

import com.kalloer1.p2p.P2P;
import com.kalloer1.p2p.channel.ChannelManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> Server: request a full channel sync (sent when the GUI opens). */
public class RequestChannelsC2SPacket {
    public RequestChannelsC2SPacket() {}

    public void toBytes(FriendlyByteBuf buf) {}

    public RequestChannelsC2SPacket(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player != null) {
                ChannelManager.INSTANCE.getOrCreateNetwork(player);
                var data = ChannelManager.INSTANCE.serializeFor(player);
                P2P.LOGGER.info("[p2p] RequestChannelsC2SPacket from player={} nets={} groups={} channels={}",
                        player.getName().getString(),
                        data.getList("networks", net.minecraft.nbt.Tag.TAG_COMPOUND).size(),
                        data.getList("groups", net.minecraft.nbt.Tag.TAG_COMPOUND).size(),
                        data.getList("channels", net.minecraft.nbt.Tag.TAG_COMPOUND).size());
                ChannelSyncS2CPacket sync = new ChannelSyncS2CPacket(data);
                ModMessages.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), sync);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
