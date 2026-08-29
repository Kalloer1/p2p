package com.kalloer1.p2p.channel;

import com.kalloer1.p2p.P2P;
import com.kalloer1.p2p.network.ChannelSyncS2CPacket;
import com.kalloer1.p2p.network.ModMessages;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
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
    private int nextNetworkId = 1;
    private int nextGroupId = 1;

    public ChannelManager() { super(); }

    /** Only used as a hard reset; never called during normal shutdown (SavedData is saved by the level). */
    public void clear() {
        networks.clear();
        channels.clear();
        groups.clear();
        nextNetworkId = 1;
    }

    // ---- Network lifecycle ----
    public Network getOrCreateNetwork(ServerPlayer player) {
        for (Network n : networks.values()) {
            if (n.owner != null && n.owner.equals(player.getStringUUID())) return n;
        }
        Network n = new Network(nextNetworkId++, player.getName().getString() + " 的网络", player.getStringUUID());
        networks.put(n.id, n);
        setDirty();
        return n;
    }

    public Network getNetwork(int id) { return networks.get(id); }

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

    // ---- Wrench interaction (server) ----
    public void handleWrenchClick(Level level, ServerPlayer player, BlockPos pos, Direction face,
                                   int channelId, Role role) {
        Channel channel = getChannel(channelId);
        if (channel == null) {
            player.displayClientMessage(Component.literal("绑定的频道 #" + channelId + " 已不存在。").withStyle(ChatFormatting.RED), true);
            return;
        }
        ResourceLocation dim = level.dimension().location();
        String key = dim + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "|" + face.getName();
        P2P.LOGGER.info("[p2p] handleWrenchClick: channel={} pos={} face={} role={} block={} dim={}",
                channelId, pos, face, role, level.getBlockState(pos).getBlock(), dim);

        boolean removed = channel.members.removeIf(m -> m.key().equals(key));
        if (removed) {
            player.displayClientMessage(Component.literal("已移出频道 #" + channelId + " 的端点。").withStyle(ChatFormatting.YELLOW), true);
        } else {
            channel.members.add(new Member(dim, pos, face, role, null, null));
            String roleStr = role == Role.INPUT ? "抽取端 (IN)" : "注入端 (OUT)";
            player.displayClientMessage(Component.literal("已设为频道 #" + channelId + " 的" + roleStr).withStyle(ChatFormatting.GREEN), true);
        }
        P2P.LOGGER.info("[p2p] handleWrenchClick result: channel={} members={} removed={}", channelId, channel.members.size(), removed);
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
            player.displayClientMessage(Component.literal("绑定的频道 #" + channelId + " 已不存在。").withStyle(ChatFormatting.RED), true);
            return 0;
        }
        Block target = level.getBlockState(origin).getBlock();
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
                channel.members.add(new Member(dim, p, face, role, null, null));
                added++;
            }
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (visited.add(n) && level.getBlockState(n).getBlock() == target) queue.add(n);
            }
        }
        if (added == 0)
            player.displayClientMessage(Component.literal("附近没有发现可连锁的相同机器。").withStyle(ChatFormatting.YELLOW), true);
        else
            player.displayClientMessage(Component.literal("已连锁绑定 " + added + " 台相同机器到频道 #" + channelId).withStyle(ChatFormatting.GREEN), true);
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
            player.displayClientMessage(Component.literal("绑定的频道 #" + channelId + " 已不存在。").withStyle(ChatFormatting.RED), true);
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
            player.displayClientMessage(Component.literal("附近没有可连锁解绑的相同机器。").withStyle(ChatFormatting.YELLOW), true);
        else
            player.displayClientMessage(Component.literal("已连锁解绑 " + removed + " 台相同机器").withStyle(ChatFormatting.YELLOW), true);
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
        return m;
    }
}
