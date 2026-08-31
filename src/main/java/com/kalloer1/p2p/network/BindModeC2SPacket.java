package com.kalloer1.p2p.network;

import com.kalloer1.p2p.P2P;
import com.kalloer1.p2p.channel.ChannelManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> Server: 打开 / 关闭"绑定模式"。
 * 服务端保存这个开关，用于校验随后的 {@link BindClickC2SPacket}（防止未开模式时伪造点击包）。
 */
public class BindModeC2SPacket {
    private final boolean active;

    public BindModeC2SPacket(boolean active) {
        this.active = active;
    }

    public BindModeC2SPacket(FriendlyByteBuf buf) {
        this.active = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ChannelManager.INSTANCE.setBindModeActive(player, active);
            P2P.LOGGER.info("[p2p] 绑定模式 {} (player={})", active ? "开启" : "关闭", player.getName().getString());
        });
        ctx.get().setPacketHandled(true);
    }
}
