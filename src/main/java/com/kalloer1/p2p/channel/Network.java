package com.kalloer1.p2p.channel;

import net.minecraft.nbt.CompoundTag;

import java.util.HashSet;
import java.util.Set;

/**
 * Top-level ownership unit. Channels and groups live in the ChannelManager keyed by id.
 *
 * <p>共享模型：网络属于 owner；被 owner 邀请并接受的玩家会进入 members，
 * 从而拥有与该网络拥有者完全相同的查看 / 改动权限（新建频道、建组、改配置、绑端点）。
 * 邀请是双向的：A 邀请 B 成功后，A 也会加入 B 的网络，双方互相可见、互相可改。
 * 服务器管理员（OP）不会因此获得查看他人网络的权限。
 */
public class Network {
    public final int id;
    public String name;
    public String owner;                 // player UUID string
    public final Set<String> admins = new HashSet<>();
    public final Set<String> members = new HashSet<>();
    /** 已收到邀请但尚未接受的玩家 UUID（接受后移入 members）。 */
    public final Set<String> invited = new HashSet<>();
    public int nextChannelId = 1;

    public Network(int id, String name, String owner) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        if (owner != null) members.add(owner);
    }

    /** Monotonic, never-reused channel id (avoids stale wrench NBT pointing at a reused id). */
    public int allocateChannelId() {
        return nextChannelId++;
    }

    public boolean hasAdmin(String uuid) {
        return owner != null && owner.equals(uuid) || admins.contains(uuid);
    }

    /** 是否能看见并使用这个网络：拥有者、管理员、被邀请加入的成员。 */
    public boolean isVisibleTo(String uuid) {
        return hasAdmin(uuid) || members.contains(uuid);
    }

    /** 是否能踢人等管理动作：拥有者与管理员。 */
    public boolean canManage(String uuid) {
        return hasAdmin(uuid);
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("name", name);
        tag.putString("owner", owner == null ? "" : owner);
        tag.putString("admins", String.join(",", admins));
        tag.putString("members", String.join(",", members));
        tag.putString("invited", String.join(",", invited));
        tag.putInt("nextChannelId", nextChannelId);
        return tag;
    }

    public static Network deserializeNBT(CompoundTag tag) {
        Network n = new Network(tag.getInt("id"), tag.getString("name"), tag.getString("owner"));
        n.admins.addAll(split(tag.getString("admins")));
        n.members.addAll(split(tag.getString("members")));
        n.invited.addAll(split(tag.getString("invited")));
        n.nextChannelId = tag.getInt("nextChannelId");
        return n;
    }

    /** 逗号分隔的 UUID 串 -> 集合；兼容旧存档（没有 invited 字段时为空集）。 */
    private static Set<String> split(String csv) {
        Set<String> out = new HashSet<>();
        if (csv != null && !csv.isEmpty())
            for (String s : csv.split(",")) if (!s.isEmpty()) out.add(s);
        return out;
    }
}
