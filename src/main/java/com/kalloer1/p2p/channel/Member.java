package com.kalloer1.p2p.channel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * One endpoint: a specific face of a specific block in a specific dimension,
 * wired as INPUT or OUTPUT, with optional per-role filters and a routing priority
 * (used by the PRIORITY distribution mode; higher = preferred first).
 */
public class Member {
    public final ResourceLocation dim;
    public final BlockPos pos;
    public final Direction face;
    public final Role role;
    public Filter extractFilter;
    public Filter insertFilter;
    public int priority = 0;   // routing priority, higher value is preferred in PRIORITY distribution

    public Member(ResourceLocation dim, BlockPos pos, Direction face, Role role,
                  Filter extractFilter, Filter insertFilter) {
        this(dim, pos, face, role, extractFilter, insertFilter, 0);
    }

    public Member(ResourceLocation dim, BlockPos pos, Direction face, Role role,
                  Filter extractFilter, Filter insertFilter, int priority) {
        this.dim = dim;
        this.pos = pos;
        this.face = face;
        this.role = role;
        this.extractFilter = extractFilter;
        this.insertFilter = insertFilter;
        this.priority = priority;
    }

    /** Stable key for "one face can only belong to one channel of a given type". */
    public String key() {
        return dim + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "|" + face.getName();
    }

    /** A member with a different face on the same block: same key except the face segment. */
    public Member withFace(Direction newFace) {
        return new Member(dim, pos, newFace, role, extractFilter, insertFilter, priority);
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", dim.toString());
        tag.putLong("pos", pos.asLong());
        tag.putString("face", face.getName());
        tag.putString("role", role.name());
        tag.putInt("priority", priority);
        if (extractFilter != null) tag.put("extract", extractFilter.serializeNBT());
        if (insertFilter != null) tag.put("insert", insertFilter.serializeNBT());
        return tag;
    }

    public static Member deserializeNBT(CompoundTag tag) {
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("dim"));
        if (dim == null) dim = new ResourceLocation("minecraft", "overworld");
        BlockPos pos = BlockPos.of(tag.getLong("pos"));
        Direction face = Direction.byName(tag.getString("face"));
        Role role = Role.valueOf(tag.getString("role"));
        Filter extract = tag.contains("extract") ? Filter.deserializeNBT(tag.getCompound("extract")) : null;
        Filter insert = tag.contains("insert") ? Filter.deserializeNBT(tag.getCompound("insert")) : null;
        int priority = tag.getInt("priority");   // 0 when absent (old saves)
        return new Member(dim, pos, face, role, extract, insert, priority);
    }
}