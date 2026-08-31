package com.kalloer1.p2p.command;

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
        send(src, "§6[P2P]§r 网络共享指令：", null);
        send(src, "§7/p2p invite <玩家>§r  邀请某玩家共享你的网络（对方需在线）", null);
        send(src, "§7/p2p accept [玩家]§r  接受邀请（不填玩家则接受最早一个）", null);
        send(src, "§7/p2p deny [玩家]§r    拒绝邀请", null);
        send(src, "§7/p2p leave <玩家>§r   退出对方的网络（双向断开）", null);
        send(src, "§7/p2p kick <玩家>§r    把某玩家踢出你的网络（双向断开，仅网络拥有者可用）", null);
        send(src, "§7/p2p members§r        查看你可见的网络与成员", null);
        send(src, "§7/p2p invites§r        查看待处理的邀请", null);
        return 1;
    }

    private static int invite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer me = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        if (target == me) {
            send(me, "不能邀请自己加入网络。", ChatFormatting.RED);
            return 0;
        }
        ChannelManager cm = ChannelManager.INSTANCE;
        Network mine = cm.getOrCreateNetwork(me);
        String targetUuid = target.getStringUUID();
        String targetName = target.getName().getString();
        if (mine.members.contains(targetUuid)) {
            send(me, targetName + " 已经在你的网络中了。", ChatFormatting.YELLOW);
            return 0;
        }
        if (mine.invited.contains(targetUuid)) {
            send(me, "已向 " + targetName + " 发过邀请，正在等对方接受。", ChatFormatting.YELLOW);
            return 0;
        }
        mine.invited.add(targetUuid);
        cm.setDirty();
        send(me, "已邀请 " + targetName + " 加入网络「" + mine.name + "」，等待对方接受。", ChatFormatting.GREEN);
        // 对方收到可点击的 接受 / 拒绝
        MutableComponent notice = Component.literal("§6[P2P]§r ")
                .append(Component.literal(me.getName().getString() + " 邀请你共享网络「" + mine.name + "」  ")
                        .withStyle(ChatFormatting.YELLOW))
                .append(clickable("§a[接受]§r", "/p2p accept " + me.getName().getString(), "接受邀请，双方互相可见并可修改对方网络"))
                .append(Component.literal("  "))
                .append(clickable("§c[拒绝]§r", "/p2p deny " + me.getName().getString(), "拒绝邀请"));
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
                send(me, "你目前没有待处理的网络共享邀请。", ChatFormatting.YELLOW);
                return 0;
            }
            Network first = pending.get(0);
            inviterUuid = first.owner;
            inviterName = nameOf(server, first.owner);
        }
        if (meUuid.equals(inviterUuid)) {
            send(me, "不能接受自己的邀请。", ChatFormatting.RED);
            return 0;
        }

        Network theirs = cm.findByOwner(inviterUuid);
        if (theirs == null || !theirs.invited.contains(meUuid)) {
            send(me, "没有来自 " + inviterName + " 的待接受邀请。", ChatFormatting.RED);
            return 0;
        }
        // 双向加入：对方网络加我，我的网络加对方
        cm.establishMutualShare(inviterUuid, inviterName, meUuid, me.getName().getString());
        Network mine = cm.findByOwner(meUuid);
        send(me, "已加入「" + theirs.name + "」，" + inviterName + " 也加入了你的网络，现在双方可以互相查看和修改。", ChatFormatting.GREEN);

        ServerPlayer inviter = server.getPlayerList().getPlayer(UUID.fromString(inviterUuid));
        if (inviter != null) {
            send(inviter, me.getName().getString() + " 接受了你的邀请：现在双方可以互相查看和修改彼此的网络。", ChatFormatting.GREEN);
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
                send(me, "你目前没有待处理的网络共享邀请。", ChatFormatting.YELLOW);
                return 0;
            }
            inviterUuid = pending.get(0).owner;
            inviterName = nameOf(server, inviterUuid);
        }
        Network theirs = cm.findByOwner(inviterUuid);
        if (theirs == null || !theirs.invited.remove(meUuid)) {
            send(me, "没有来自 " + inviterName + " 的待处理邀请。", ChatFormatting.RED);
            return 0;
        }
        cm.setDirty();
        send(me, "已拒绝 " + inviterName + " 的网络共享邀请。", ChatFormatting.YELLOW);
        ServerPlayer inviter = server.getPlayerList().getPlayer(UUID.fromString(inviterUuid));
        if (inviter != null) send(inviter, me.getName().getString() + " 拒绝了你的网络共享邀请。", ChatFormatting.YELLOW);
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
            send(me, "用法：/p2p " + (self ? "leave" : "kick") + " <玩家>", ChatFormatting.RED);
            return 0;
        }
        ChannelManager cm = ChannelManager.INSTANCE;
        String meUuid = me.getStringUUID();
        String otherUuid = other.getId().toString();
        String otherName = other.getName();
        if (meUuid.equals(otherUuid)) {
            send(me, self ? "不能退出自己的网络。" : "不能把自己踢出网络。", ChatFormatting.RED);
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
            send(me, self
                    ? "你还没有加入 " + otherName + " 的网络。"
                    : otherName + " 不在你管理的网络里（只有网络拥有者可以踢人）。", ChatFormatting.RED);
            return 0;
        }
        cm.setDirty();
        send(me, self
                ? "已退出 " + otherName + " 的网络，对方也不再共享你的网络。"
                : "已将 " + otherName + " 踢出你的网络，双方共享关系已解除。", ChatFormatting.GREEN);
        ServerPlayer target = server.getPlayerList().getPlayer(UUID.fromString(otherUuid));
        if (target != null) {
            send(target, self
                    ? me.getName().getString() + " 退出了你的网络，双方共享关系已解除。"
                    : "你已被 " + me.getName().getString() + " 移出网络，双方共享关系已解除。", ChatFormatting.YELLOW);
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
            send(me, "§6# " + n.name + "§r " + (mine ? "§a（你的网络）" : "§b（他人共享）"), null);
            for (String uuid : n.members) {
                String tag = uuid.equals(n.owner) ? "§7[拥有者]§r" : "§7[成员]§r";
                send(me, "  " + tag + " " + nameOf(server, uuid), null);
            }
            if (!n.invited.isEmpty()) {
                StringBuilder sb = new StringBuilder("  §8待接受:§r");
                for (String uuid : n.invited) sb.append(" ").append(nameOf(server, uuid));
                send(me, sb.toString(), null);
            }
        }
        if (!any) send(me, "你还没有网络。创建一个频道或组后会自动生成。", ChatFormatting.GRAY);
        return 1;
    }

    private static int invites(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer me = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        ChannelManager cm = ChannelManager.INSTANCE;
        String meUuid = me.getStringUUID();

        List<Network> received = cm.pendingInvitesFor(meUuid);
        if (received.isEmpty()) {
            send(me, "没有待处理的邀请。", ChatFormatting.GRAY);
        } else {
            for (Network n : received) {
                String ownerName = nameOf(server, n.owner);
                MutableComponent line = Component.literal("§6[P2P]§r ")
                        .append(Component.literal(ownerName + " 邀请你共享网络「" + n.name + "」  ").withStyle(ChatFormatting.YELLOW))
                        .append(clickable("§a[接受]§r", "/p2p accept " + ownerName, "接受邀请"))
                        .append(Component.literal("  "))
                        .append(clickable("§c[拒绝]§r", "/p2p deny " + ownerName, "拒绝邀请"));
                me.displayClientMessage(line, false);
            }
        }
        boolean sentAny = false;
        for (Network n : cm.getAllNetworks()) {
            if (!n.canManage(meUuid) || n.invited.isEmpty()) continue;
            sentAny = true;
            StringBuilder sb = new StringBuilder("§8已发出:§r");
            for (String uuid : n.invited) sb.append(" ").append(nameOf(server, uuid));
            send(me, sb.toString(), null);
        }
        if (!sentAny && received.isEmpty()) send(me, "用 §7/p2p invite <玩家>§r 邀请别人共享网络。", ChatFormatting.GRAY);
        return 1;
    }

    // ============================================================ 工具

    private static GameProfile firstProfile(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, arg);
        return profiles.isEmpty() ? null : profiles.iterator().next();
    }

    private static MutableComponent clickable(String text, String command, String hover) {
        return Component.literal(text)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    private static void send(CommandSourceStack src, String text, ChatFormatting color) {
        ChatFormatting fmt = color == null ? ChatFormatting.GRAY : color;
        src.sendSuccess(() -> Component.literal(text).withStyle(fmt), false);
    }

    private static void send(ServerPlayer player, String text, ChatFormatting color) {
        player.displayClientMessage(Component.literal(text).withStyle(color == null ? ChatFormatting.GRAY : color), false);
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
