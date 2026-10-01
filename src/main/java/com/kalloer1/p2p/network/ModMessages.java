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
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络层注册表（1.20.1 SimpleChannel 形态；1.21.1 版同构逻辑的 Forge 适配）。
 * 协议版本 "1" 与旧 1.20.1 通道一致；全部包类的字节布局与 1.21.1 StreamCodec 版对齐
 * （channelId=int、action=varint ordinal、字符串=utf、NBT=writeNbt）。
 */
public class ModMessages {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(P2P.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;
    private static int id() { return packetId++; }

    /** Convenience: send any registered message to the server. */
    public static <T> void sendToServer(T packet) {
        INSTANCE.sendToServer(packet);
    }

    /** Convenience: send any registered message to one player (server side). */
    public static <T> void sendToPlayer(ServerPlayer player, T packet) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void register() {
        INSTANCE.messageBuilder(RequestChannelsC2SPacket.class, id())
                .encoder(RequestChannelsC2SPacket::toBytes)
                .decoder(RequestChannelsC2SPacket::new)
                .consumerMainThread(RequestChannelsC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(ChannelSyncS2CPacket.class, id())
                .encoder(ChannelSyncS2CPacket::toBytes)
                .decoder(ChannelSyncS2CPacket::new)
                .consumerMainThread(ChannelSyncS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(ChannelActionC2SPacket.class, id())
                .encoder(ChannelActionC2SPacket::toBytes)
                .decoder(ChannelActionC2SPacket::new)
                .consumerMainThread(ChannelActionC2SPacket::handle)
                .add();

        // —— 绑定模式（取代旧扳手）：按键进入，空手左右键绑定端点 ——
        INSTANCE.messageBuilder(BindModeC2SPacket.class, id())
                .encoder(BindModeC2SPacket::toBytes)
                .decoder(BindModeC2SPacket::new)
                .consumerMainThread(BindModeC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(BindClickC2SPacket.class, id())
                .encoder(BindClickC2SPacket::toBytes)
                .decoder(BindClickC2SPacket::new)
                .consumerMainThread(BindClickC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(SetBindChannelC2SPacket.class, id())
                .encoder(SetBindChannelC2SPacket::toBytes)
                .decoder(SetBindChannelC2SPacket::new)
                .consumerMainThread(SetBindChannelC2SPacket::handle)
                .add();
    }

    // ---- C2S Handlers（逻辑照搬 1.21.1 版） ----

    static void handleRequestChannels(RequestChannelsC2SPacket pkt, ServerPlayer player) {
        if (player == null) return;
        ChannelManager cm = ChannelManager.INSTANCE;
        cm.getOrCreateNetwork(player);
        var data = cm.serializeFor(player);
        P2P.LOGGER.info("[p2p] RequestChannelsC2SPacket from player={} nets={} groups={} channels={}",
                player.getName().getString(),
                data.getList("networks", Tag.TAG_COMPOUND).size(),
                data.getList("groups", Tag.TAG_COMPOUND).size(),
                data.getList("channels", Tag.TAG_COMPOUND).size());
        sendToPlayer(player, new ChannelSyncS2CPacket(data));
    }

    static void handleBindMode(BindModeC2SPacket pkt, ServerPlayer player) {
        if (player == null) return;
        ChannelManager.INSTANCE.setBindModeActive(player, pkt.active());
        P2P.LOGGER.info("[p2p] 绑定模式 {} (player={})", pkt.active() ? "开启" : "关闭", player.getName().getString());
    }

    static void handleBindClick(BindClickC2SPacket pkt, ServerPlayer player) {
        if (player == null) return;
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

    static void handleSetBindChannel(SetBindChannelC2SPacket pkt, ServerPlayer player) {
        if (player == null) return;
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

    static void handleChannelAction(ChannelActionC2SPacket pkt, ServerPlayer player) {
        if (player == null) return;
        pkt.apply(player);
        ChannelManager.INSTANCE.getOrCreateNetwork(player);
        sendToPlayer(player, new ChannelSyncS2CPacket(ChannelManager.INSTANCE.serializeFor(player)));
    }

    // ---- S2C Handler ----

    static void handleChannelSync(ChannelSyncS2CPacket pkt) {
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
