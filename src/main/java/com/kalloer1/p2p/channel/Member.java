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
    /** 绑定时记录的方块注册 id（服务端建端点时区块必定已加载，所以那一刻一定拿得到）。
     *  老存档里的成员为 null。有了它，GUI 才能给跨维度 / 区块未加载的端点画出方块图标。 */
    public final ResourceLocation blockId;
    public Filter extractFilter;
    public Filter insertFilter;
    public int priority = 0;   // routing priority, higher value is preferred in PRIORITY distribution

    public Member(ResourceLocation dim, BlockPos pos, Direction face, Role role,
                  Filter extractFilter, Filter insertFilter) {
        this(dim, pos, face, role, extractFilter, insertFilter, 0, null);
    }

    public Member(ResourceLocation dim, BlockPos pos, Direction face, Role role,
                  Filter extractFilter, Filter insertFilter, int priority) {
        this(dim, pos, face, role, extractFilter, insertFilter, priority, null);
    }

    public Member(ResourceLocation dim, BlockPos pos, Direction face, Role role,
                  Filter extractFilter, Filter insertFilter, int priority, ResourceLocation blockId) {
        this.dim = dim;
        this.pos = pos;
        this.face = face;
        this.role = role;
        this.extractFilter = extractFilter;
        this.insertFilter = insertFilter;
        this.priority = priority;
        this.blockId = blockId;
    }

    /** Stable key for "one face can only belong to one channel of a given type". */
    public String key() {
        return dim + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "|" + face.getName();
    }

    /** A member with a different face on the same block: same key except the face segment. */
    public Member withFace(Direction newFace) {
        return new Member(dim, pos, newFace, role, extractFilter, insertFilter, priority, blockId);
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", dim.toString());
        tag.putLong("pos", pos.asLong());
        tag.putString("face", face.getName());
        tag.putString("role", role.name());
        tag.putInt("priority", priority);
        if (blockId != null) tag.putString("block", blockId.toString());
        if (extractFilter != null) tag.put("extract", extractFilter.serializeNBT());
        if (insertFilter != null) tag.put("insert", insertFilter.serializeNBT());
        return tag;
    }

    public static Member deserializeNBT(CompoundTag tag) {
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("dim"));
        if (dim == null) dim = new ResourceLocation("minecraft:overworld");
        BlockPos pos = BlockPos.of(tag.getLong("pos"));
        Direction face = Direction.byName(tag.getString("face"));
        Role role = Role.valueOf(tag.getString("role"));
        Filter extract = tag.contains("extract") ? Filter.deserializeNBT(tag.getCompound("extract")) : null;
        Filter insert = tag.contains("insert") ? Filter.deserializeNBT(tag.getCompound("insert")) : null;
        int priority = tag.getInt("priority");   // 0 when absent (old saves)
        // 老存档没有 "block"，此时保持 null，GUI 会退回"同维度实时取方块"的老逻辑
        ResourceLocation blockId = tag.contains("block") ? ResourceLocation.tryParse(tag.getString("block")) : null;
        return new Member(dim, pos, face, role, extract, insert, priority, blockId);
    }
}