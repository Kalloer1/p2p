package com.kalloer1.p2p.channel;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/** A production line: groups several typed channels under one manageable entry. */
public class Group {
    public int id;
    public String name;
    public int color;
    public int networkId = 0;          // owning network id (for multi-network filtering)
    public final List<Integer> channelIds = new ArrayList<>();

    public Group(int id, String name, int color) {
        this.id = id;
        this.name = name;
        this.color = color;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("name", name);
        tag.putInt("color", color);
        tag.putInt("networkId", networkId);
        tag.putIntArray("channels", channelIds.stream().mapToInt(Integer::intValue).toArray());
        return tag;
    }

    public static Group deserializeNBT(CompoundTag tag) {
        Group g = new Group(tag.getInt("id"), tag.getString("name"), tag.getInt("color"));
        g.networkId = tag.getInt("networkId");
        for (int c : tag.getIntArray("channels")) g.channelIds.add(c);
        return g;
    }
}
