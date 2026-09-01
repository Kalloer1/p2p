package com.kalloer1.p2p.command;

import com.kalloer1.p2p.Lang;
import com.kalloer1.p2p.P2P;
import com.kalloer1.p2p.channel.ChannelManager;
import com.kalloer1.p2p.channel.Network;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * /p2p … 网络共享指令。
 *
 * <p>设计要点：
 * <ol>
 *   <li>共享是<b>双向</b>的：A 邀请 B 且 B 接受后，B 加入 A 的网络，A 同时加入 B 的网络，
 *       双方在 GUI 里都能看到并改动对方的网络（新建/删除频道与组、改配置、绑端点）。</li>
 *   <li>踢人与退出都是<b>双向断开</b>：一次操作同时清掉两个方向的成员关系。</li>
 *   <li>服务器管理员（OP）没有任何后门：本指令只处理玩家自己的网络，
 *       不能列出或改动其他玩家创建的网络。</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = P2P.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class P2PCommands {

    private P2PCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("p2p")
                        .executes(P2PCommands::usage)
                        .then(Commands.literal("invite")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(P2PCommands::invite)))
                        .then(Commands.literal("accept")
                                .executes(ctx -> accept(ctx, null))
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(ctx -> accept(ctx, firstProfile(ctx, "player")))))
                        .then(Commands.literal("deny")
                                .executes(ctx -> deny(ctx, null))
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(ctx -> deny(ctx, firstProfile(ctx, "player")))))
                        .then(Commands.literal("leave")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(ctx -> unlink(ctx, firstProfile(ctx, "player"), true))))
                        .then(Commands.literal("kick")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(ctx -> unlink(ctx, firstProfile(ctx, "player"), false))))
                        .then(Commands.literal("members").executes(P2PCommands::members))
                        .then(Commands.literal("invites").executes(P2PCommands::invites))
        );
        P2P.LOGGER.info("[p2p] 已注册 /p2p 指令（invite / accept / deny / leave / kick / members / invites）");
    }

    // ============================================================ 子指令

    private static int usage(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        send(src, Lang.t(Lang.CMD_USAGE_HEADER), null);
        send(src, Lang.t(Lang.CMD_USAGE_INVITE), null);
        send(src, Lang.t(Lang.CMD_USAGE_ACCEPT), null);
        send(src, Lang.t(Lang.CMD_USAGE_DENY), null);
        send(src, Lang.t(Lang.CMD_USAGE_LEAVE), null);
        send(src, Lang.t(Lang.CMD_USAGE_KICK), null);
        send(src, Lang.t(Lang.CMD_USAGE_MEMBERS), null);
        send(src, Lang.t(Lang.CMD_USAGE_INVITES), null);
        return 1;
    }

    private static int invite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer me = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        if (target == me) {
            send(me, Lang.t(Lang.CMD_INVITE_SELF), ChatFormatting.RED);
            return 0;
        }
        ChannelManager cm = ChannelManager.INSTANCE;
        Network mine = cm.getOrCreateNetwork(me);
        String targetUuid = target.getStringUUID();
        String targetName = target.getName().getString();
        if (mine.members.contains(targetUuid)) {
            send(me, Lang.t(Lang.CMD_INVITE_ALREADY, targetName), ChatFormatting.YELLOW);
            return 0;
        }
        if (mine.invited.contains(targetUuid)) {
            send(me, Lang.t(Lang.CMD_INVITE_PENDING, targetName), ChatFormatting.YELLOW);
            return 0;
        }
        mine.invited.add(targetUuid);
        cm.setDirty();
        send(me, Lang.t(Lang.CMD_INVITE_SENT, targetName, mine.name), ChatFormatting.GREEN);
        // 对方收到可点击的 接受 / 拒绝
        MutableComponent acceptLabel = Component.literal("§a[").append(Lang.t(Lang.CMD_ACCEPT_LABEL)).append("]§r");
        MutableComponent denyLabel = Component.literal("§c[").append(Lang.t(Lang.CMD_DENY_LABEL)).append("]§r");
        MutableComponent notice = Component.literal("§6[P2P]§r ")
                .append(Lang.t(Lang.CMD_INVITE_NOTICE, me.getName().getString(), mine.name).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("  "))
                .append(clickable(acceptLabel, "/p2p accept " + me.getName().getString(), Lang.t(Lang.CMD_ACCEPT_HOVER)))
                .append(Component.literal("  "))
                .append(clickable(denyLabel, "/p2p deny " + me.getName().getString(), Lang.t(Lang.CMD_DENY_HOVER)));
        target.displayClientMessage(notice, false);
        return 1;
    }

    private static int accept(CommandContext<CommandSourceStack> ctx, GameProfile inviterProfile) throws CommandSyntaxException {
        ServerPlayer me = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        ChannelManager cm = ChannelManager.INSTANCE;
        String meUuid = me.getStringUUID();

        String inviterUuid;
        String inviterName;
        if (inviterProfile != null) {
            inviterUuid = inviterProfile.getId().toString();
            inviterName = inviterProfile.getName();
        } else {
            List<Network> pending = cm.pendingInvitesFor(meUuid);
            if (pending.isEmpty()) {
                send(me, Lang.t(Lang.CMD_ACCEPT_NO_PENDING), ChatFormatting.YELLOW);
                return 0;
            }
            Network first = pending.get(0);
            inviterUuid = first.owner;
            inviterName = nameOf(server, first.owner);
        }
        if (meUuid.equals(inviterUuid)) {
            send(me, Lang.t(Lang.CMD_ACCEPT_SELF), ChatFormatting.RED);
            return 0;
        }

        Network theirs = cm.findByOwner(inviterUuid);
        if (theirs == null || !theirs.invited.contains(meUuid)) {
            send(me, Lang.t(Lang.CMD_ACCEPT_NONE_FROM, inviterName), ChatFormatting.RED);
            return 0;
        }
        // 双向加入：对方网络加我，我的网络加对方
        cm.establishMutualShare(inviterUuid, inviterName, meUuid, me.getName().getString());
        Network mine = cm.findByOwner(meUuid);
        send(me, Lang.t(Lang.CMD_ACCEPT_JOINED, theirs.name, inviterName), ChatFormatting.GREEN);

        ServerPlayer inviter = server.getPlayerList().getPlayer(UUID.fromString(inviterUuid));
        if (inviter != null) {
            send(inviter, Lang.t(Lang.CMD_ACCEPT_ACCEPTED, me.getName().getString()), ChatFormatting.GREEN);
        }
        cm.pushSyncTo(server, meUuid);
        cm.pushSyncTo(server, inviterUuid);
        P2P.LOGGER.info("[p2p] 网络共享建立: {} <-> {}", me.getName().getString(), inviterName);
        return 1;
    }

    private static int deny(CommandContext<CommandSourceStack> ctx, GameProfile inviterProfile) throws CommandSyntaxException {
        ServerPlayer me = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        ChannelManager cm = ChannelManager.INSTANCE;
        String meUuid = me.getStringUUID();

        String inviterUuid;
        String inviterName;
        if (inviterProfile != null) {
            inviterUuid = inviterProfile.getId().toString();
            inviterName = inviterProfile.getName();
        } else {
            List<Network> pending = cm.pendingInvitesFor(meUuid);
            if (pending.isEmpty()) {
                send(me, Lang.t(Lang.CMD_ACCEPT_NO_PENDING), ChatFormatting.YELLOW);
                return 0;
            }
            inviterUuid = pending.get(0).owner;
            inviterName = nameOf(server, inviterUuid);
        }
        Network theirs = cm.findByOwner(inviterUuid);
        if (theirs == null || !theirs.invited.remove(meUuid)) {
            send(me, Lang.t(Lang.CMD_DENY_NONE_FROM, inviterName), ChatFormatting.RED);
            return 0;
        }
        cm.setDirty();
        send(me, Lang.t(Lang.CMD_DENY_DONE, inviterName), ChatFormatting.YELLOW);
        ServerPlayer inviter = server.getPlayerList().getPlayer(UUID.fromString(inviterUuid));
        if (inviter != null) send(inviter, Lang.t(Lang.CMD_DENY_DENIED, me.getName().getString()), ChatFormatting.YELLOW);
        return 1;
    }

    /**
     * 断开两个玩家之间的共享。self=true 表示主动退出对方网络（谁都能退），
     * self=false 表示踢人（只有自己拥有/管理的网络才能踢）。
     */
    private static int unlink(CommandContext<CommandSourceStack> ctx, GameProfile other, boolean self) throws CommandSyntaxException {
        ServerPlayer me = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        if (other == null) {
            send(me, Lang.t(Lang.CMD_UNLINK_USAGE, self ? "leave" : "kick"), ChatFormatting.RED);
            return 0;
        }
        ChannelManager cm = ChannelManager.INSTANCE;
        String meUuid = me.getStringUUID();
        String otherUuid = other.getId().toString();
        String otherName = other.getName();
        if (meUuid.equals(otherUuid)) {
            send(me, Lang.t(self ? Lang.CMD_UNLINK_SELF_LEAVE : Lang.CMD_UNLINK_SELF_KICK), ChatFormatting.RED);
            return 0;
        }

        int removed = 0;
        if (self) {
            // 主动退出：双向断开，不需要对方同意
            for (Network n : cm.getAllNetworks()) {
                if (n.hasAdmin(otherUuid) && n.members.remove(meUuid)) removed++;
                if (n.hasAdmin(meUuid) && !otherUuid.equals(n.owner) && n.members.remove(otherUuid)) removed++;
            }
        } else {
            // 踢人：只能从自己拥有/管理的网络里踢，同时断开对方网络里我的成员身份
            for (Network n : cm.getAllNetworks()) {
                if (n.canManage(meUuid) && !otherUuid.equals(n.owner) && n.members.remove(otherUuid)) removed++;
                if (n.hasAdmin(otherUuid) && n.members.remove(meUuid)) removed++;
            }
        }
        if (removed == 0) {
            send(me, Lang.t(self ? Lang.CMD_UNLINK_NOT_JOINED : Lang.CMD_UNLINK_NOT_MANAGED, otherName), ChatFormatting.RED);
            return 0;
        }
        cm.setDirty();
        send(me, Lang.t(self ? Lang.CMD_UNLINK_LEFT : Lang.CMD_UNLINK_KICKED, otherName), ChatFormatting.GREEN);
        ServerPlayer target = server.getPlayerList().getPlayer(UUID.fromString(otherUuid));
        if (target != null) {
            send(target, Lang.t(self ? Lang.CMD_UNLINK_LEFT_NOTIFY : Lang.CMD_UNLINK_KICKED_NOTIFY, me.getName().getString()), ChatFormatting.YELLOW);
        }
        cm.pushSyncTo(server, meUuid);
        cm.pushSyncTo(server, otherUuid);
        P2P.LOGGER.info("[p2p] 网络共享解除: {} <-> {} (self={})", me.getName().getString(), otherName, self);
        return 1;
    }

    private static int members(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer me = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        ChannelManager cm = ChannelManager.INSTANCE;
        String meUuid = me.getStringUUID();
        boolean any = false;
        for (Network n : cm.getAllNetworks()) {
            if (!n.isVisibleTo(meUuid)) continue;
            any = true;
            boolean mine = n.canManage(meUuid);
            send(me, Lang.t(mine ? Lang.CMD_MEMBERS_HEADER_MINE : Lang.CMD_MEMBERS_HEADER_SHARED, n.name), null);
            for (String uuid : n.members) {
                String tagKey = uuid.equals(n.owner) ? Lang.CMD_MEMBERS_TAG_OWNER : Lang.CMD_MEMBERS_TAG_MEMBER;
                send(me, Component.literal("  ").append(Lang.t(tagKey)).append(" ").append(Component.literal(nameOf(server, uuid))), null);
            }
            if (!n.invited.isEmpty()) {
                MutableComponent sb = Lang.t(Lang.CMD_MEMBERS_PENDING_PREFIX);
                for (String uuid : n.invited) sb.append(" ").append(Component.literal(nameOf(server, uuid)));
                send(me, sb, null);
            }
        }
        if (!any) send(me, Lang.t(Lang.CMD_MEMBERS_NONE), ChatFormatting.GRAY);
        return 1;
    }

    private static int invites(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer me = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        ChannelManager cm = ChannelManager.INSTANCE;
        String meUuid = me.getStringUUID();

        List<Network> received = cm.pendingInvitesFor(meUuid);
        if (received.isEmpty()) {
            send(me, Lang.t(Lang.CMD_INVITES_NONE_RECEIVED), ChatFormatting.GRAY);
        } else {
            MutableComponent acceptLabel = Component.literal("§a[").append(Lang.t(Lang.CMD_ACCEPT_LABEL)).append("]§r");
            MutableComponent denyLabel = Component.literal("§c[").append(Lang.t(Lang.CMD_DENY_LABEL)).append("]§r");
            for (Network n : received) {
                String ownerName = nameOf(server, n.owner);
                MutableComponent line = Component.literal("§6[P2P]§r ")
                        .append(Lang.t(Lang.CMD_INVITES_LINE, ownerName, n.name).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("  "))
                        .append(clickable(acceptLabel, "/p2p accept " + ownerName, Lang.t(Lang.CMD_ACCEPT_HOVER)))
                        .append(Component.literal("  "))
                        .append(clickable(denyLabel, "/p2p deny " + ownerName, Lang.t(Lang.CMD_DENY_HOVER)));
                me.displayClientMessage(line, false);
            }
        }
        boolean sentAny = false;
        for (Network n : cm.getAllNetworks()) {
            if (!n.canManage(meUuid) || n.invited.isEmpty()) continue;
            sentAny = true;
            MutableComponent sb = Lang.t(Lang.CMD_INVITES_SENT_PREFIX);
            for (String uuid : n.invited) sb.append(" ").append(Component.literal(nameOf(server, uuid)));
            send(me, sb, null);
        }
        if (!sentAny && received.isEmpty()) send(me, Lang.t(Lang.CMD_INVITES_HINT), ChatFormatting.GRAY);
        return 1;
    }

    // ============================================================ 工具

    private static GameProfile firstProfile(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, arg);
        return profiles.isEmpty() ? null : profiles.iterator().next();
    }

    private static MutableComponent clickable(Component label, String command, Component hover) {
        return label.copy().withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    private static void send(CommandSourceStack src, MutableComponent text, ChatFormatting color) {
        MutableComponent out = color != null ? text.withStyle(color) : text;
        src.sendSuccess(() -> out, false);
    }

    private static void send(ServerPlayer player, MutableComponent text, ChatFormatting color) {
        if (color != null) text = text.withStyle(color);
        player.displayClientMessage(text, false);
    }

    /** UUID -> 玩家名：先查在线玩家，再查服务器档案缓存；都查不到就退回 UUID 前 8 位。 */
    private static String nameOf(MinecraftServer server, String uuid) {
        try {
            UUID id = UUID.fromString(uuid);
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online != null) return online.getName().getString();
            Optional<GameProfile> cached = server.getProfileCache().get(id);
            if (cached.isPresent()) {
                String name = cached.get().getName();
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (IllegalArgumentException ignored) {
            // 存档里的非法 UUID，退回短串
        }
        return uuid.length() > 8 ? uuid.substring(0, 8) : uuid;
    }
}
