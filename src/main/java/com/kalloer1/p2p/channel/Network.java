package com.kalloer1.p2p.channel;

import net.minecraft.nbt.CompoundTag;

import java.util.HashSet;
import java.util.Set;

/** Top-level ownership unit. Channels and groups live in the ChannelManager keyed by id. */
public class Network {
    public final int id;
    public String name;
    public String owner;                 // player UUID string
    public final Set<String> admins = new HashSet<>();
    public final Set<String> members = new HashSet<>();
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

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("name", name);
        tag.putString("owner", owner == null ? "" : owner);
        tag.putString("admins", String.join(",", admins));
        tag.putString("members", String.join(",", members));
        tag.putInt("nextChannelId", nextChannelId);
        return tag;
    }

    public static Network deserializeNBT(CompoundTag tag) {
        Network n = new Network(tag.getInt("id"), tag.getString("name"), tag.getString("owner"));
        if (!tag.getString("admins").isEmpty())
            for (String s : tag.getString("admins").split(",")) if (!s.isEmpty()) n.admins.add(s);
        if (!tag.getString("members").isEmpty())
            for (String s : tag.getString("members").split(",")) if (!s.isEmpty()) n.members.add(s);
        n.nextChannelId = tag.getInt("nextChannelId");
        return n;
    }
}
