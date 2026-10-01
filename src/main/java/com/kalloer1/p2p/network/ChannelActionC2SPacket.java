package com.kalloer1.p2p.network;

import com.kalloer1.p2p.Lang;
import com.kalloer1.p2p.P2P;
import com.kalloer1.p2p.P2PConfig;
import com.kalloer1.p2p.api.ChannelMembersChangedEvent;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.Nullable;

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

    /** Upper bound for the rate/speed fields. Integer.MAX_VALUE lets a channel move everything it can. */
    public static final int RATE_MAX = Integer.MAX_VALUE;

    private final int channelId;
    private final Action action;
    private final String createType;     // CREATE: medium id | SET_TYPE: new medium id
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
        this(buf.readInt(), Action.values()[buf.readVarInt()], buf.readUtf(), buf.readInt(),
                buf.readUtf(), buf.readBoolean(), buf.readNbt(), buf.readUtf());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(channelId);
        buf.writeVarInt(action.ordinal());
        buf.writeUtf(createType == null ? "" : createType);
        buf.writeInt(intVal);
        buf.writeUtf(memberKey == null ? "" : memberKey);
        buf.writeBoolean(extract);
        buf.writeNbt(filterTag);
        buf.writeUtf(name == null ? "" : name);
    }

    public void handle(Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            apply(player);
        });
    }

    // ---- record-style accessors（与 1.21.1 record 访问器同名，附属/门面调用不受影响） ----
    public int channelId() { return channelId; }
    public Action action() { return action; }
    public String createType() { return createType; }
    public int intVal() { return intVal; }
    public String memberKey() { return memberKey; }
    public boolean extract() { return extract; }
    public @Nullable CompoundTag filterTag() { return filterTag; }
    public String name() { return name; }

    /**
     * Apply this action on the server side. Called from the handler in {@link ModMessages}.
     */
    public void apply(ServerPlayer player) {
        P2P.LOGGER.info("[p2p] ChannelAction: action={} channel={} intVal={} name='{}' memberKey={} extract={}",
                action, channelId, intVal, name, memberKey, extract);
        ChannelManager cm = ChannelManager.INSTANCE;

        // Permission gate: channel-level ops only for network owner / admin / invited members.
        // CREATE / CREATE_GROUP operate on the player's own network; DELETE_GROUP is checked below.
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

        // members 被 GUI 路径直接改动的动作；switch 结束后据此派发一次 ChannelMembersChangedEvent
        // （CLEAN_AIR 由 ChannelManager.removeAirMembers 自己派发，此处不重复）。
        ChannelMembersChangedEvent.ChangeKind membersTouched = null;

        switch (action) {
            case CREATE -> {
                Channel c = cm.createChannel(cm.getOrCreateNetwork(player), Mediums.resolve(createType));
                if (name != null && !name.isEmpty()) c.name = name;
                // 与 SET_GROUP 同一套校验：组存在、同网络、有编辑权。否则新建频道可把
                // groupId 指向任意组，级联删除（DELETE_GROUP）会连带删掉无权频道。
                if (intVal >= 0) {
                    Group g = cm.getGroup(intVal);
                    if (g != null && g.networkId == c.networkId && cm.canEditGroup(player, g)) c.groupId = intVal;
                }
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
            case DELETE_GROUP -> {
                // 1.4.0：删除组级联删除组内频道（服务端语义，见 ChannelManager.deleteGroup），
                // 不弹确认框，聊天栏回显删除规模。
                int removed = cm.deleteGroup(intVal);
                if (removed > 0)
                    player.displayClientMessage(
                            Lang.t(Lang.MSG_GROUP_DELETED, removed).withStyle(ChatFormatting.YELLOW), false);
                P2P.LOGGER.info("[p2p] DELETE_GROUP: group={} cascadeDeletedChannels={}", intVal, removed);
            }
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
                membersTouched = ChannelMembersChangedEvent.ChangeKind.ROLE_CHANGED;
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
                membersTouched = ChannelMembersChangedEvent.ChangeKind.PRIORITY_CHANGED;
                Channel c = cm.getChannel(channelId);
                if (c != null) {
                    for (Member m : c.members) {
                        if (m.key().equals(memberKey)) m.priority = intVal;
                    }
                }
            }
            case SET_FACE -> {
                membersTouched = ChannelMembersChangedEvent.ChangeKind.FACE_CHANGED;
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
                membersTouched = ChannelMembersChangedEvent.ChangeKind.MEMBER_REMOVED;
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
        // Persist every mutation above
        cm.setDirty();
        if (membersTouched != null) {
            Channel touched = cm.getChannel(channelId);
            if (touched != null) {
                MinecraftForge.EVENT_BUS.post(new ChannelMembersChangedEvent(player, player.level(), touched, membersTouched));
            }
        }
    }
}
