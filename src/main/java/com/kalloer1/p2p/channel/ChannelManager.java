package com.kalloer1.p2p.channel;

import com.kalloer1.p2p.Lang;
import com.kalloer1.p2p.P2P;
import com.kalloer1.p2p.network.ChannelSyncS2CPacket;
import com.kalloer1.p2p.network.ModMessages;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server-side authority for all channels, groups and networks.
 * One logical store per world, persisted on the OVERWORLD level via Forge SavedData, so everything
 * (networks / channels / bound endpoints / filters) survives a game restart. The static INSTANCE is
 * replaced with the loaded SavedData on ServerStartedEvent.
 */
public class ChannelManager extends SavedData {
    public static ChannelManager INSTANCE = new ChannelManager();

    private final Map<Integer, Network> networks = new HashMap<>();
    private final Map<Integer, Channel> channels = new HashMap<>();
    private final Map<Integer, Group> groups = new HashMap<>();
    // 每个玩家的"当前绑定频道"（持久化）。绑定模式下空手左右键操作的就是这个频道。
    private final Map<String, Integer> boundChannel = new HashMap<>();
    // 绑定模式开关（玩家 UUID -> 是否开启）。纯运行时状态，不写入存档：重进游戏默认关闭。
    private final Map<String, Boolean> bindModeActive = new HashMap<>();
    private int nextNetworkId = 1;
    private int nextGroupId = 1;

    public ChannelManager() { super(); }

    /** Only used as a hard reset; never called during normal shutdown (SavedData is saved by the level). */
    public void clear() {
        networks.clear();
        channels.clear();
        groups.clear();
        boundChannel.clear();
        bindModeActive.clear();
        nextNetworkId = 1;
    }

    // ---- 绑定模式（按键进入，空手左右键绑定端点） ----
    /** 当前绑定频道 id，未设置返回 -1。 */
    public int getBoundChannel(ServerPlayer player) {
        return boundChannel.getOrDefault(player.getStringUUID(), -1);
    }

    public void setBoundChannel(ServerPlayer player, int channelId) {
        if (channelId < 0) boundChannel.remove(player.getStringUUID());
        else boundChannel.put(player.getStringUUID(), channelId);
        setDirty();
    }

    /** 绑定模式是否开启（服务端权威状态，防止客户端伪造点击包）。 */
    public boolean isBindModeActive(ServerPlayer player) {
        return Boolean.TRUE.equals(bindModeActive.get(player.getStringUUID()));
    }

    public void setBindModeActive(ServerPlayer player, boolean active) {
        if (active) bindModeActive.put(player.getStringUUID(), Boolean.TRUE);
        else bindModeActive.remove(player.getStringUUID());
    }

    // ---- Network lifecycle ----
    public Network getOrCreateNetwork(ServerPlayer player) {
        return getOrCreateNetworkByUuid(player.getStringUUID(), player.getName().getString());
    }

    /**
     * 按 UUID 取得（没有就新建）网络。离线的被邀请方也要能建网络，所以入口放在 UUID 层。
     * displayName 只用于生成默认网络名。
     */
    public Network getOrCreateNetworkByUuid(String uuid, String displayName) {
        Network existing = findByOwner(uuid);
        if (existing != null) return existing;
        String who = (displayName == null || displayName.isEmpty()) ? "玩家" : displayName;
        Network n = new Network(nextNetworkId++, who + " 的网络", uuid);
        networks.put(n.id, n);
        setDirty();
        return n;
    }

    public Network getNetwork(int id) { return networks.get(id); }

    /** 只读视图：命令与权限判定用。 */
    public Collection<Network> getAllNetworks() { return networks.values(); }

    /** 按拥有者 UUID 找网络（一个玩家最多拥有一个网络）。 */
    public Network findByOwner(String uuid) {
        if (uuid == null) return null;
        for (Network n : networks.values()) if (n.owner != null && n.owner.equals(uuid)) return n;
        return null;
    }

    public Group getGroup(int id) { return groups.get(id); }

    // ---- 权限判定 ----
    /**
     * 该玩家能否改动这个频道：频道所属网络的拥有者、管理员或被邀请加入的成员都可以。
     * 旧存档中 networkId 指向不存在的网络时返回 true（保持历史行为，不打断老世界的正常使用）。
     */
    public boolean canEditChannel(ServerPlayer player, Channel c) {
        if (c == null) return false;
        Network n = networks.get(c.networkId);
        return n == null || n.isVisibleTo(player.getStringUUID());
    }

    /** 同 {@link #canEditChannel}，判定对象为组。 */
    public boolean canEditGroup(ServerPlayer player, Group g) {
        if (g == null) return false;
        Network n = networks.get(g.networkId);
        return n == null || n.isVisibleTo(player.getStringUUID());
    }

    /** 该玩家收到的、尚未处理的邀请。 */
    public List<Network> pendingInvitesFor(String uuid) {
        List<Network> out = new ArrayList<>();
        if (uuid == null) return out;
        for (Network n : networks.values()) if (n.invited.contains(uuid)) out.add(n);
        return out;
    }

    // ---- 网络共享 ----
    /**
     * 双向建立共享：b 加入 a 的网络、a 也加入 b 的网络。
     * 之后双方都能在 GUI 里看到对方的网络，并拥有与拥有者相同的改动权限（建频道 / 建组 / 改配置 / 绑端点）。
     */
    public void establishMutualShare(String aUuid, String aName, String bUuid, String bName) {
        Network na = getOrCreateNetworkByUuid(aUuid, aName);
        Network nb = getOrCreateNetworkByUuid(bUuid, bName);
        na.invited.remove(bUuid);
        nb.invited.remove(aUuid);
        na.members.add(bUuid);
        nb.members.add(aUuid);
        setDirty();
    }

    /**
     * 断开两个玩家之间的共享关系：把 b 从 a 拥有/管理的网络中移除，同时把 a 从 b 的网络中移除。
     * 拥有者本人永远不会被移出自己的网络。返回被改动的网络数量。
     */
    public int severShare(String aUuid, String bUuid) {
        if (aUuid == null || bUuid == null) return 0;
        int changed = 0;
        for (Network n : networks.values()) {
            if (n.hasAdmin(aUuid) && !bUuid.equals(n.owner) && n.members.remove(bUuid)) changed++;
            if (n.hasAdmin(bUuid) && !aUuid.equals(n.owner) && n.members.remove(aUuid)) changed++;
        }
        if (changed > 0) setDirty();
        return changed;
    }

    /** 把最新的网络快照推给指定 UUID 的在线玩家（成员变更后刷新对方 GUI）。 */
    public void pushSyncTo(MinecraftServer server, String uuid) {
        if (server == null || uuid == null) return;
        try {
            ServerPlayer p = server.getPlayerList().getPlayer(java.util.UUID.fromString(uuid));
            if (p != null) pushSync(p);
        } catch (IllegalArgumentException ignored) {
            // UUID 串不合法（旧数据），忽略
        }
    }

    // ---- Channel ----
    public Channel createChannel(Network net, ChannelType type) {
        int cid = net.allocateChannelId();
        Channel c = new Channel(cid, type, -1);
        c.networkId = net.id;
        channels.put(cid, c);
        setDirty();
        return c;
    }

    public Channel getChannel(int id) { return channels.get(id); }

    /** Read-only view of every channel, for the server tick transfer engine. */
    public Collection<Channel> getAllChannels() { return channels.values(); }

    /** Delete = remove routing only. World endpoints keep their inventories; nothing is lost. */
    public void deleteChannel(int id) {
        Channel c = channels.remove(id);
        if (c != null && c.groupId >= 0) {
            Group g = groups.get(c.groupId);
            if (g != null) g.channelIds.remove((Integer) id);
        }
        setDirty();
    }

    // ---- Group ----
    /** Create a new (global) group. Color is a neutral grey until the player recolors it elsewhere. */
    public Group createGroup(Network net, String name) {
        int gid = nextGroupId++;
        Group g = new Group(gid, name == null || name.isEmpty() ? "新建组" : name, 0x8a8a8a);
        g.networkId = net.id;
        groups.put(gid, g);
        setDirty();
        return g;
    }

    /** Delete a group: remove it and detach every channel that belonged to it (groupId -> -1 = ungrouped). */
    public void deleteGroup(int groupId) {
        groups.remove(groupId);
        for (Channel c : channels.values())
            if (c.groupId == groupId) c.groupId = -1;
        setDirty();
    }

    /**
     * 绑定模式下的单击：把某个方块的某个面在频道里登记为端点（INPUT=抽取 / OUTPUT=注入）。
     * 同一面重复点击即解除绑定（端点本身的世界内容与容器物品不会丢失）。
     */
    public void toggleEndpoint(Level level, ServerPlayer player, BlockPos pos, Direction face,
                                int channelId, Role role) {
        Channel channel = getChannel(channelId);
        if (channel == null) {
            player.displayClientMessage(Lang.t(Lang.MSG_CHANNEL_GONE, channelId).withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!canEditChannel(player, channel)) {
            player.displayClientMessage(Lang.t(Lang.MSG_NO_PERM, channelId).withStyle(ChatFormatting.RED), true);
            return;
        }
        ResourceLocation dim = level.dimension().location();
        String key = dim + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "|" + face.getName();
        Block bound = level.getBlockState(pos).getBlock();
        P2P.LOGGER.info("[p2p] toggleEndpoint: channel={} pos={} face={} role={} block={} dim={}",
                channelId, pos, face, role, bound, dim);

        boolean removed = channel.members.removeIf(m -> m.key().equals(key));
        if (removed) {
            player.displayClientMessage(Lang.t(Lang.MSG_ENDPOINT_REMOVED, channelId).withStyle(ChatFormatting.YELLOW), true);
        } else {
            // 绑定这一刻区块必定已加载，把方块 id 一起记下来，GUI 才能给跨维度端点画图标
            channel.members.add(new Member(dim, pos, face, role, null, null, 0, BuiltInRegistries.BLOCK.getKey(bound)));
            Component roleStr = role == Role.INPUT ? Lang.t(Lang.MSG_ROLE_IN) : Lang.t(Lang.MSG_ROLE_OUT);
            player.displayClientMessage(Lang.t(Lang.MSG_ENDPOINT_ADDED, channelId, roleStr).withStyle(ChatFormatting.GREEN), true);
        }
        P2P.LOGGER.info("[p2p] toggleEndpoint result: channel={} members={} removed={}", channelId, channel.members.size(), removed);
        setDirty();
        pushSync(player);
    }

    /**
     * Ctrl+right-click: flood-fill (6-connected) from the clicked block and bind every same-type block as an
     * endpoint of the current channel with the same face. The clicked block is the BFS root, so it is ALWAYS
     * bound first and the binding then spreads outward to connected neighbours (never a detached cube shell).
     * Bounded by a hard node budget and an endpoint cap so a single click can never scan the whole world.
     */
    public int bindChain(Level level, ServerPlayer player, BlockPos origin, Direction face, int channelId, Role role) {
        Channel channel = getChannel(channelId);
        if (channel == null) {
            player.displayClientMessage(Lang.t(Lang.MSG_CHANNEL_GONE, channelId).withStyle(ChatFormatting.RED), true);
            return 0;
        }
        if (!canEditChannel(player, channel)) {
            player.displayClientMessage(Lang.t(Lang.MSG_NO_PERM, channelId).withStyle(ChatFormatting.RED), true);
            return 0;
        }
        Block target = level.getBlockState(origin).getBlock();
        // 链式绑定的 BFS 只扩散到同类型方块，所以整条链共用一个方块 id
        ResourceLocation targetId = BuiltInRegistries.BLOCK.getKey(target);
        ResourceLocation dim = level.dimension().location();
        P2P.LOGGER.info("[p2p] bindChain: origin={} face={} role={} targetBlock={} channel={}", origin, face, role, target, channelId);
        final int CAP = 64, MAX_NODES = 4096;
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        visited.add(origin);
        int added = 0;
        while (!queue.isEmpty() && visited.size() <= MAX_NODES && added < CAP) {
            BlockPos p = queue.poll();
            String key = dim + "|" + p.getX() + "," + p.getY() + "," + p.getZ() + "|" + face.getName();
            // NEVER delete an already-bound endpoint here: chain-binding must only ADD new blocks,
            // keeping every existing binding intact (previously removeIf deleted them and the block
            // was not re-added, so chaining onto a neighbour silently unbound already-bound machines).
            boolean existed = channel.members.stream().anyMatch(m -> m.key().equals(key));
            if (!existed) {
                channel.members.add(new Member(dim, p, face, role, null, null, 0, targetId));
                added++;
            }
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (visited.add(n) && level.getBlockState(n).getBlock() == target) queue.add(n);
            }
        }
        if (added == 0)
            player.displayClientMessage(Lang.t(Lang.MSG_CHAIN_NONE).withStyle(ChatFormatting.YELLOW), true);
        else
            player.displayClientMessage(Lang.t(Lang.MSG_CHAIN_ADDED, added, channelId).withStyle(ChatFormatting.GREEN), true);
        P2P.LOGGER.info("[p2p] bindChain result: added={} target={} channel={}", added, target, channelId);
        setDirty();
        pushSync(player);
        return added;
    }

    /**
     * Ctrl+right-click on an already-bound face: flood-fill (6-connected) from the clicked block and unbind every
     * same-type block matching the same face. The clicked block is the BFS root so it is always processed; the
     * unbind then spreads outward to connected neighbours. Bounded by a node budget so a huge connected floor
     * can never hang the tick.
     */
    public int unbindChain(Level level, ServerPlayer player, BlockPos origin, Direction face, int channelId, ResourceLocation dim) {
        Channel channel = getChannel(channelId);
        if (channel == null) {
            player.displayClientMessage(Lang.t(Lang.MSG_CHANNEL_GONE, channelId).withStyle(ChatFormatting.RED), true);
            return 0;
        }
        if (!canEditChannel(player, channel)) {
            player.displayClientMessage(Lang.t(Lang.MSG_NO_PERM, channelId).withStyle(ChatFormatting.RED), true);
            return 0;
        }
        Block target = level.getBlockState(origin).getBlock();
        final int MAX_NODES = 4096;
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        visited.add(origin);
        int removed = 0;
        while (!queue.isEmpty() && visited.size() <= MAX_NODES) {
            BlockPos p = queue.poll();
            if (channel.members.removeIf(m -> m.pos.equals(p) && m.face == face && m.dim.equals(dim))) removed++;
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (visited.add(n) && level.getBlockState(n).getBlock() == target) queue.add(n);
            }
        }
        if (removed == 0)
            player.displayClientMessage(Lang.t(Lang.MSG_UNCHAIN_NONE).withStyle(ChatFormatting.YELLOW), true);
        else
            player.displayClientMessage(Lang.t(Lang.MSG_UNCHAIN_REMOVED, removed).withStyle(ChatFormatting.YELLOW), true);
        P2P.LOGGER.info("[p2p] unbindChain result: removed={} target={} channel={}", removed, target, channelId);
        if (removed > 0) setDirty();
        pushSync(player);
        return removed;
    }

    /**
     * Remove every member of {@code channelId} whose recorded position is now air in its stored dimension.
     * Useful for cleaning up endpoints whose block was broken.
     */
    public int removeAirMembers(ServerPlayer player, int channelId) {
        Channel channel = getChannel(channelId);
        if (channel == null) return 0;
        MinecraftServer server = player.getServer();
        if (server == null) return 0;
        int before = channel.members.size();
        channel.members.removeIf(m -> {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, m.dim);
            ServerLevel lvl = server.getLevel(key);
            if (lvl == null) return false;
            return lvl.getBlockState(m.pos).isAir();
        });
        int removed = before - channel.members.size();
        P2P.LOGGER.info("[p2p] removeAirMembers: channel={} before={} after={}", channelId, before, channel.members.size());
        if (removed > 0) setDirty();
        return removed;
    }

    /**
     * Push the current snapshot to one player. Called after any endpoint mutation (bind / unbind / chain /
     * batch-unbind) so the client's overlay (bound-face glow) refreshes in real time without reopening the GUI.
     */
    public void pushSync(ServerPlayer player) {
        ModMessages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new ChannelSyncS2CPacket(serializeFor(player)));
    }

    /**
     * Collect every block of the same type as {@code target} within a cube of half-side {@code radius} around
     * {@code origin}, bounded by {@code cap} so a single click can never scan the whole world. Used by the
     * wrench's chain-select (Ctrl) action.
     */
    public List<BlockPos> collectSameTypePositions(Level level, BlockPos origin, Block target, int radius, int cap) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++)
            for (int dy = -radius; dy <= radius; dy++)
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = origin.offset(dx, dy, dz);
                    if (!seen.add(p)) continue;
                    if (level.getBlockState(p).getBlock() != target) continue;
                    out.add(p);
                    if (out.size() >= cap) return out;
                }
        return out;
    }

    /** Remove every endpoint of {@code channel} whose block position is in {@code positions} (same dimension). */
    public int unbindPositions(Level level, Channel channel, Collection<BlockPos> positions, ResourceLocation dim) {
        Set<BlockPos> set = new HashSet<>(positions);
        int before = channel.members.size();
        channel.members.removeIf(m -> set.contains(m.pos) && m.dim.equals(dim));
        int removed = before - channel.members.size();
        if (removed > 0) setDirty();
        return removed;
    }

    /** Switch a channel's transport medium at any time (the type is no longer locked at creation). */
    public void setChannelType(int channelId, ChannelType type) {
        Channel c = getChannel(channelId);
        if (c != null) {
            c.type = type;
            setDirty();
        }
    }

    // ---- Serialization for S2C sync ----
    /** Only ship networks/members the player can see, plus the channels & groups that belong to them. */
    public CompoundTag serializeFor(ServerPlayer player) {
        String uuid = player.getStringUUID();
        CompoundTag root = new CompoundTag();
        ListTag netList = new ListTag();
        Set<Integer> visibleNet = new HashSet<>();
        for (Network n : networks.values()) {
            if (n.owner != null && n.owner.equals(uuid) || n.members.contains(uuid) || n.admins.contains(uuid)) {
                netList.add(n.serializeNBT());
                visibleNet.add(n.id);
            }
        }
        root.put("networks", netList);
        ListTag grpList = new ListTag();
        for (Group g : groups.values()) if (visibleNet.contains(g.networkId)) grpList.add(g.serializeNBT());
        root.put("groups", grpList);
        ListTag chList = new ListTag();
        for (Channel c : channels.values()) if (visibleNet.contains(c.networkId)) chList.add(c.serializeNBT());
        root.put("channels", chList);
        // 客户端据此显示"当前绑定频道"，并决定绑定模式下高亮哪个频道
        root.putInt("boundChannel", boundChannel.getOrDefault(uuid, -1));
        return root;
    }

    // ---- Persistence (SavedData) ----
    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("nextNetworkId", nextNetworkId);
        tag.putInt("nextGroupId", nextGroupId);
        ListTag netList = new ListTag();
        for (Network n : networks.values()) netList.add(n.serializeNBT());
        tag.put("networks", netList);
        ListTag grpList = new ListTag();
        for (Group g : groups.values()) grpList.add(g.serializeNBT());
        tag.put("groups", grpList);
        ListTag chList = new ListTag();
        for (Channel c : channels.values()) chList.add(c.serializeNBT());
        tag.put("channels", chList);
        ListTag boundList = new ListTag();
        for (Map.Entry<String, Integer> e : boundChannel.entrySet()) {
            CompoundTag bt = new CompoundTag();
            bt.putString("uuid", e.getKey());
            bt.putInt("channel", e.getValue());
            boundList.add(bt);
        }
        tag.put("boundChannel", boundList);
        return tag;
    }

    public static ChannelManager load(CompoundTag tag) {
        ChannelManager m = new ChannelManager();
        if (tag == null) return m;
        m.nextNetworkId = tag.getInt("nextNetworkId");
        m.nextGroupId = tag.getInt("nextGroupId");
        for (Tag t : tag.getList("networks", Tag.TAG_COMPOUND)) {
            CompoundTag ct = (CompoundTag) t;
            Network n = Network.deserializeNBT(ct);
            m.networks.put(n.id, n);
        }
        for (Tag t : tag.getList("groups", Tag.TAG_COMPOUND)) {
            Group g = Group.deserializeNBT((CompoundTag) t);
            m.groups.put(g.id, g);
        }
        for (Tag t : tag.getList("channels", Tag.TAG_COMPOUND)) {
            Channel c = Channel.deserializeNBT((CompoundTag) t);
            m.channels.put(c.id, c);
        }
        for (Tag t : tag.getList("boundChannel", Tag.TAG_COMPOUND)) {
            CompoundTag bt = (CompoundTag) t;
            String uuid = bt.getString("uuid");
            if (!uuid.isEmpty()) m.boundChannel.put(uuid, bt.getInt("channel"));
        }
        return m;
    }
}
