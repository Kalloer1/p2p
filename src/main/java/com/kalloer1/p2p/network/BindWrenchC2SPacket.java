package com.kalloer1.p2p.network;

import com.kalloer1.p2p.item.ChannelWrench;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

import com.kalloer1.p2p.P2P;

/** Client -> Server: write the bound channel id into the held wrench's NBT. */
public class BindWrenchC2SPacket {
    private final int channelId;

    public BindWrenchC2SPacket(int channelId) {
        this.channelId = channelId;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(channelId);
    }

    public BindWrenchC2SPacket(FriendlyByteBuf buf) {
        this.channelId = buf.readInt();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player != null) {
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof ChannelWrench) {
                    ChannelWrench.setBoundChannelId(stack, channelId);
                    ChannelWrench.pushRecent(stack, channelId);
                    P2P.LOGGER.info("[p2p] BindWrenchC2SPacket: bound channel={} to wrench for player={}", channelId, player.getName().getString());
                } else {
                    P2P.LOGGER.warn("[p2p] BindWrenchC2SPacket ignored: sender not holding wrench");
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
