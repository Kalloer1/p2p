package com.kalloer1.p2p.network;

import com.kalloer1.p2p.Lang;
import com.kalloer1.p2p.P2P;
import com.kalloer1.p2p.P2PConfig;
import com.kalloer1.p2p.channel.Channel;
import com.kalloer1.p2p.channel.ChannelManager;
import com.kalloer1.p2p.channel.Filter;
import com.kalloer1.p2p.channel.Group;
import com.kalloer1.p2p.channel.Member;
import com.kalloer1.p2p.channel.Network;
import com.kalloer1.p2p.channel.Role;
import com.kalloer1.p2p.medium.Mediums;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Client -> Server: mutate a channel/network/group. One packet carries an action + the fields it needs,
 * keeping the network protocol small. After applying, the server re-pushes a sync to the actor.
 */
public class ChannelActionC2SPacket {
    public enum Action {
        CREATE, DELETE, SET_RATE, SET_SPEED, TOGGLE_REDSTONE, CYCLE_DISTRIBUTION, SET_GROUP, SET_FILTER, RENAME,
        CREATE_GROUP, DELETE_GROUP, SET_ROLE, SET_TYPE, CLEAN_AIR, REMOVE_MEMBER, SET_PRIORITY, SET_FACE,
        TOGGLE_CHUNKLOAD
    }

    /** Upper bound for the rate/speed fields. Integer.MAX_VALUE lets a channel move everything it can, like Pipez' creative upgrade. */
    public static final int RATE_MAX = Integer.MAX_VALUE;

    private final int channelId;
    private final Action action;
    private final String createType;     // CREATE: ChannelType name | SET_TYPE: new ChannelType name
    private final int intVal;            // SET_RATE / SET_SPEED / SET_GROUP / SET_ROLE / SET_PRIORITY / SET_FACE value
    private final String memberKey;      // SET_FILTER / SET_ROLE / REMOVE_MEMBER / SET_PRIORITY / SET_FACE: Member.key()
    private final boolean extract;       // SET_FILTER: true=extractFilter, false=insertFilter
    private final CompoundTag filterTag; // SET_FILTER payload
    private final String name;           // CREATE initial name / RENAME / CREATE_GROUP name

    public ChannelActionC2SPacket(int channelId, Action action, String createType, int intVal,
                                  String memberKey, boolean extract, CompoundTag filterTag, String name) {
        this.channelId = channelId;
        this.action = action;
        this.createType = createType;
        this.intVal = intVal;
        this.memberKey = memberKey;
        this.extract = extract;
        this.filterTag = filterTag;
        this.name = name;
    }

    public ChannelActionC2SPacket(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readEnum(Action.class), buf.readUtf(), buf.readInt(),
                buf.readUtf(), buf.readBoolean(), buf.readNbt(), buf.readUtf());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(channelId);
        buf.writeEnum(action);
        buf.writeUtf(createType == null ? "" : createType);
        buf.writeInt(intVal);
        buf.writeUtf(memberKey == null ? "" : memberKey);
        buf.writeBoolean(extract);
        buf.writeNbt(filterTag);
        buf.writeUtf(name == null ? "" : name);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            apply(player);
            ChannelManager.INSTANCE.getOrCreateNetwork(player);
            ChannelSyncS2CPacket sync = new ChannelSyncS2CPacket(ChannelManager.INSTANCE.serializeFor(player));
            ModMessages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), sync);
        });
        ctx.get().setPacketHandled(true);
    }

    private void apply(ServerPlayer player) {
        P2P.LOGGER.info("[p2p] ChannelAction: action={} channel={} intVal={} name='{}' memberKey={} extract={}",
                action, channelId, intVal, name, memberKey, extract);
        ChannelManager cm = ChannelManager.INSTANCE;

        // 权限闸门：频道级操作只允许该频道的网络拥有者 / 管理员 / 被邀请加入的成员执行。
        // 网络共享之后这一点尤其重要——否则任何玩家都能靠猜频道 id 改动别人的网络。
        // CREATE / CREATE_GROUP 作用于玩家自己的网络，无需校验；DELETE_GROUP 在下面单独校验。
        if (action != Action.CREATE && action != Action.CREATE_GROUP && action != Action.DELETE_GROUP) {
            Channel target = cm.getChannel(channelId);
            if (target == null) return;
            if (!cm.canEditChannel(player, target)) {
                player.displayClientMessage(
                        Lang.t(Lang.MSG_NO_PERM, channelId).withStyle(ChatFormatting.RED), true);
                return;
            }
        }
        if (action == Action.DELETE_GROUP) {
            Group g = cm.getGroup(intVal);
            if (g != null && !cm.canEditGroup(player, g)) {
                player.displayClientMessage(
                        Lang.t(Lang.MSG_NO_PERM_GROUP).withStyle(ChatFormatting.RED), true);
                return;
            }
        }

        switch (action) {
            case CREATE -> {
                Channel c = cm.createChannel(cm.getOrCreateNetwork(player), Mediums.resolve(createType));
                if (name != null && !name.isEmpty()) c.name = name;
                if (intVal >= 0) c.groupId = intVal;
            }
            case DELETE -> cm.deleteChannel(channelId);
            case SET_RATE -> {
                Channel c = cm.getChannel(channelId);
                if (c != null) c.rate = Math.max(1, Math.min(RATE_MAX, intVal));
            }
            case SET_SPEED -> {
                Channel c = cm.getChannel(channelId);
                if (c != null) c.speed = Math.max(1, Math.min(RATE_MAX, intVal));
            }
            case TOGGLE_REDSTONE -> {
                Channel c = cm.getChannel(channelId);
                if (c != null) {
                    Channel.RedstoneMode[] vals = Channel.RedstoneMode.values();
                    c.redstoneMode = vals[(c.redstoneMode.ordinal() + 1) % vals.length];
                }
            }
            case CYCLE_DISTRIBUTION -> {
                Channel c = cm.getChannel(channelId);
                if (c != null) {
                    Channel.Distribution[] vals = Channel.Distribution.values();
                    c.distribution = vals[(c.distribution.ordinal() + 1) % vals.length];
                }
            }
            case SET_GROUP -> {
                Channel c = cm.getChannel(channelId);
                if (c != null) {
                    // 组必须与频道同属一个网络，避免把别人的频道挂到自己的组里
                    if (intVal < 0) {
                        c.groupId = intVal;
                    } else {
                        Group g = cm.getGroup(intVal);
                        if (g != null && g.networkId == c.networkId && cm.canEditGroup(player, g)) c.groupId = intVal;
                    }
                }
            }
            case RENAME -> {
                Channel c = cm.getChannel(channelId);
                if (c != null && name != null && !name.isEmpty()) c.name = name;
            }
            case SET_TYPE -> cm.setChannelType(channelId, Mediums.resolve(createType));
            case CREATE_GROUP -> cm.createGroup(cm.getOrCreateNetwork(player), name == null ? "" : name);
            case DELETE_GROUP -> cm.deleteGroup(intVal);   // intVal carries the group id
            case SET_FILTER -> {
                Channel c = cm.getChannel(channelId);
                if (c != null) {
                    Filter f = filterTag == null ? null : Filter.deserializeNBT(filterTag);
                    for (Member m : c.members) {
                        if (m.key().equals(memberKey)) {
                            if (extract) m.extractFilter = f;
                            else m.insertFilter = f;
                        }
                    }
                }
            }
            case SET_ROLE -> {
                Channel c = cm.getChannel(channelId);
                if (c != null) {
                    Role newRole = intVal == 1 ? Role.OUTPUT : Role.INPUT;
                    for (int i = 0; i < c.members.size(); i++) {
                        Member m = c.members.get(i);
                        if (m.key().equals(memberKey))
                            c.members.set(i, new Member(m.dim, m.pos, m.face, newRole, m.extractFilter, m.insertFilter, m.priority, m.blockId));
                    }
                }
            }
            case SET_PRIORITY -> {
                Channel c = cm.getChannel(channelId);
                if (c != null) {
                    for (Member m : c.members) {
                        if (m.key().equals(memberKey)) m.priority = intVal;
                    }
                }
            }
            case SET_FACE -> {
                Channel c = cm.getChannel(channelId);
                if (c != null && memberKey != null && !memberKey.isEmpty()) {
                    Direction face = Direction.from3DDataValue(Math.floorMod(intVal, 6));
                    for (int i = 0; i < c.members.size(); i++) {
                        Member m = c.members.get(i);
                        if (m.key().equals(memberKey))
                            c.members.set(i, m.withFace(face));
                    }
                }
            }
            case REMOVE_MEMBER -> {
                Channel c = cm.getChannel(channelId);
                if (c != null && memberKey != null && !memberKey.isEmpty()) {
                    boolean removed = c.members.removeIf(m -> m.key().equals(memberKey));
                    if (removed) {
                        cm.setDirty();
                        player.displayClientMessage(
                                Lang.t(Lang.MSG_ENDPOINT_REMOVED_KEY, channelId, memberKey)
                                        .withStyle(ChatFormatting.YELLOW), true);
                        P2P.LOGGER.info("[p2p] REMOVE_MEMBER: channel={} memberKey={}", channelId, memberKey);
                    }
                }
            }
            case CLEAN_AIR -> {
                int removed = cm.removeAirMembers(player, channelId);
                if (removed > 0)
                    player.displayClientMessage(Lang.t(Lang.MSG_CLEARED_AIR, removed).withStyle(ChatFormatting.YELLOW), true);
                else
                    player.displayClientMessage(Lang.t(Lang.MSG_NO_AIR).withStyle(ChatFormatting.GRAY), true);
            }
            case TOGGLE_CHUNKLOAD -> {
                // 三道闸：网络编辑权限 → 配置总开关 → 游戏模式（创造默认可用，生存看配置）。
                Channel c = cm.getChannel(channelId);
                Network n = c == null ? null : cm.getNetwork(c.networkId);
                if (n == null) return;
                if (!cm.canEditChannel(player, c)) {
                    player.displayClientMessage(
                            Lang.t(Lang.MSG_NO_PERM, channelId).withStyle(ChatFormatting.RED), true);
                    return;
                }
                if (!P2PConfig.chunkLoadEnabled.get()) {
                    player.displayClientMessage(Lang.t(Lang.MSG_CHUNKLOAD_DISABLED).withStyle(ChatFormatting.RED), true);
                    return;
                }
                if (!player.isCreative() && !P2PConfig.chunkLoadAllowSurvival.get()) {
                    player.displayClientMessage(Lang.t(Lang.MSG_CHUNKLOAD_NO_PERM).withStyle(ChatFormatting.RED), true);
                    return;
                }
                n.chunkLoad = !n.chunkLoad;
                player.displayClientMessage(Lang.t(n.chunkLoad ? Lang.MSG_CHUNKLOAD_ON : Lang.MSG_CHUNKLOAD_OFF, n.name)
                        .withStyle(n.chunkLoad ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
                P2P.LOGGER.info("[p2p] TOGGLE_CHUNKLOAD: network={} ({}) -> {}", n.id, n.name, n.chunkLoad);
            }
        }
        // Persist every mutation above (rate/speed/face/priority/role/filter/redstone/distribution...).
        // Without this the changed values survive only in memory and a save/load cycle reverts them.
        cm.setDirty();
    }
}