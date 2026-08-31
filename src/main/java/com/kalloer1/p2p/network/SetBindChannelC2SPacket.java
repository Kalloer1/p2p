package com.kalloer1.p2p.network;

import com.kalloer1.p2p.P2P;
import com.kalloer1.p2p.channel.ChannelManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> Server: 把某个频道设为该玩家的"当前绑定频道"
 * （绑定模式下空手左右键操作的目标频道，替代原来的"绑定到扳手"）。
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

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ChannelManager cm = ChannelManager.INSTANCE;
            if (channelId >= 0 && !cm.canEditChannel(player, cm.getChannel(channelId))) {
                player.displayClientMessage(
                        Component.literal("频道 #" + channelId + " 不在你可以访问的网络中。").withStyle(ChatFormatting.RED), true);
                return;
            }
            cm.setBoundChannel(player, channelId);
            P2P.LOGGER.info("[p2p] 当前绑定频道 -> {} (player={})", channelId, player.getName().getString());
            player.displayClientMessage(channelId < 0
                    ? Component.literal("已清除当前绑定频道。").withStyle(ChatFormatting.YELLOW)
                    : Component.literal("当前绑定频道已设为 #" + channelId + "，按绑定模式键后空手右键=输入 / 左键=输出")
                            .withStyle(ChatFormatting.GREEN), true);
            cm.pushSync(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
