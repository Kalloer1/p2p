package com.kalloer1.p2p.network;

import com.kalloer1.p2p.Lang;
import com.kalloer1.p2p.P2P;
import com.kalloer1.p2p.client.ClientBindState;
import com.kalloer1.p2p.client.ClientChannelCache;
import com.kalloer1.p2p.channel.Channel;
import com.kalloer1.p2p.channel.ChannelManager;
import com.kalloer1.p2p.channel.Role;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = P2P.MODID)
public class ModMessages {

    /** Convenience: send any payload to the server. */
    public static <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void sendToServer(T payload) {
        PacketDistributor.sendToServer(payload);
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent e) {
        e.registrar("1")
                .playToServer(RequestChannelsC2SPacket.TYPE, RequestChannelsC2SPacket.STREAM_CODEC, (pkt, ctx) -> {
                    ctx.enqueueWork(() -> handleRequestChannels(pkt, ctx));
                })
                .playToServer(BindModeC2SPacket.TYPE, BindModeC2SPacket.STREAM_CODEC, (pkt, ctx) -> {
                    ctx.enqueueWork(() -> handleBindMode(pkt, ctx));
                })
                .playToServer(BindClickC2SPacket.TYPE, BindClickC2SPacket.STREAM_CODEC, (pkt, ctx) -> {
                    ctx.enqueueWork(() -> handleBindClick(pkt, ctx));
                })
                .playToServer(SetBindChannelC2SPacket.TYPE, SetBindChannelC2SPacket.STREAM_CODEC, (pkt, ctx) -> {
                    ctx.enqueueWork(() -> handleSetBindChannel(pkt, ctx));
                })
                .playToServer(ChannelActionC2SPacket.TYPE, ChannelActionC2SPacket.STREAM_CODEC, (pkt, ctx) -> {
                    ctx.enqueueWork(() -> handleChannelAction(pkt, ctx));
                })
                .playToClient(ChannelSyncS2CPacket.TYPE, ChannelSyncS2CPacket.STREAM_CODEC, (pkt, ctx) -> {
                    ctx.enqueueWork(() -> handleChannelSync(pkt, ctx));
                });
    }

    // ---- C2S Handlers ----

    static void handleRequestChannels(RequestChannelsC2SPacket pkt, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelManager cm = ChannelManager.INSTANCE;
        cm.getOrCreateNetwork(player);
        var data = cm.serializeFor(player);
        P2P.LOGGER.info("[p2p] RequestChannelsC2SPacket from player={} nets={} groups={} channels={}",
                player.getName().getString(),
                data.getList("networks", Tag.TAG_COMPOUND).size(),
                data.getList("groups", Tag.TAG_COMPOUND).size(),
                data.getList("channels", Tag.TAG_COMPOUND).size());
        PacketDistributor.sendToPlayer(player, new ChannelSyncS2CPacket(data));
    }

    static void handleBindMode(BindModeC2SPacket pkt, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelManager.INSTANCE.setBindModeActive(player, pkt.active());
        P2P.LOGGER.info("[p2p] 绑定模式 {} (player={})", pkt.active() ? "开启" : "关闭", player.getName().getString());
    }

    static void handleBindClick(BindClickC2SPacket pkt, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelManager cm = ChannelManager.INSTANCE;
        // Only players in bind mode can modify endpoints — prevents forged-click exploits.
        if (!cm.isBindModeActive(player)) {
            P2P.LOGGER.warn("[p2p] BindClick 被忽略：{} 未处于绑定模式", player.getName().getString());
            return;
        }
        int channelId = cm.getBoundChannel(player);
        if (channelId < 0) {
            player.displayClientMessage(Lang.t(Lang.MSG_NO_BOUND_CHANNEL)
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        Channel channel = cm.getChannel(channelId);
        if (channel == null) {
            player.displayClientMessage(Lang.t(Lang.MSG_CHANNEL_GONE, channelId)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!cm.canEditChannel(player, channel)) {
            player.displayClientMessage(Lang.t(Lang.MSG_NO_PERM, channelId)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        Role role = pkt.rightButton() ? Role.INPUT : Role.OUTPUT;
        Level level = player.level();
        P2P.LOGGER.info("[p2p] BindClick: player={} pos={} face={} button={} ctrl={} channel={} role={}",
                player.getName().getString(), pkt.pos(), pkt.face(),
                pkt.rightButton() ? "右键" : "左键", pkt.control(), channelId, role);

        if (pkt.control()) {
            // Already bound on that face -> chain unbind; otherwise chain bind.
            ResourceLocation dim = level.dimension().location();
            boolean alreadyBound = channel.members.stream()
                    .anyMatch(m -> m.pos.equals(pkt.pos()) && m.face == pkt.face() && m.dim.equals(dim));
            if (alreadyBound) cm.unbindChain(level, player, pkt.pos(), pkt.face(), channelId, dim);
            else cm.bindChain(level, player, pkt.pos(), pkt.face(), channelId, role);
        } else {
            cm.toggleEndpoint(level, player, pkt.pos(), pkt.face(), channelId, role);
        }
    }

    static void handleSetBindChannel(SetBindChannelC2SPacket pkt, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelManager cm = ChannelManager.INSTANCE;
        if (pkt.channelId() >= 0 && !cm.canEditChannel(player, cm.getChannel(pkt.channelId()))) {
            player.displayClientMessage(
                    Lang.t(Lang.MSG_BIND_NOT_ACCESSIBLE, pkt.channelId()).withStyle(ChatFormatting.RED), true);
            return;
        }
        cm.setBoundChannel(player, pkt.channelId());
        P2P.LOGGER.info("[p2p] 当前绑定频道 -> {} (player={})", pkt.channelId(), player.getName().getString());
        player.displayClientMessage(pkt.channelId() < 0
                ? Lang.t(Lang.MSG_BIND_CLEARED).withStyle(ChatFormatting.YELLOW)
                : Lang.t(Lang.MSG_BIND_SET, pkt.channelId()).withStyle(ChatFormatting.GREEN), true);
        cm.pushSync(player);
    }

    static void handleChannelAction(ChannelActionC2SPacket pkt, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        pkt.apply(player);
        ChannelManager.INSTANCE.getOrCreateNetwork(player);
        PacketDistributor.sendToPlayer(player,
                new ChannelSyncS2CPacket(ChannelManager.INSTANCE.serializeFor(player)));
    }

    // ---- S2C Handler ----

    static void handleChannelSync(ChannelSyncS2CPacket pkt, IPayloadContext ctx) {
        CompoundTag data = pkt.data();
        ClientChannelCache.INSTANCE.setData(data);
        // Sync "current bound channel" to client mirror for HUD / highlight use
        if (data.contains("boundChannel")) ClientBindState.setBoundChannel(data.getInt("boundChannel"));
        P2P.LOGGER.info("[p2p] ChannelSyncS2CPacket (client): nets={} groups={} channels={} boundChannel={}",
                data.getList("networks", Tag.TAG_COMPOUND).size(),
                data.getList("groups", Tag.TAG_COMPOUND).size(),
                data.getList("channels", Tag.TAG_COMPOUND).size(),
                data.contains("boundChannel") ? data.getInt("boundChannel") : "none");
    }
}
