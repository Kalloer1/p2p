package com.kalloer1.p2p.harness;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * 代表性假数据快照（结构与 ChannelManager.serializeFor 的 ChannelSyncS2CPacket 载荷一致）：
 * 2 个网络；网络 1 内两个分组、4 条频道（物品×2 / 流体 / 能量），端点覆盖
 * IN/OUT、不同优先级、overworld（VALID，实时图标=箱子）/ 下界末地（UNKNOWN，blockId 兜底图标）；
 * 两个分组展开、选中频道 1。网络 2 带 1 条频道（切页可见，不影响首屏）。
 */
public final class HSnap {

    public static CompoundTag build() {
        CompoundTag root = new CompoundTag();

        ListTag nets = new ListTag();
        nets.add(net(1, "主生产线", "uuid-aaa", "uuid-aaa", true, 6));
        nets.add(net(2, "副网络", "uuid-bbb", "uuid-bbb", false, 2));
        root.put("networks", nets);

        ListTag groups = new ListTag();
        groups.add(group(1, "熔炉线", 0xFF8a5a2b, 1, new int[]{1, 2}));
        groups.add(group(2, "仓储", 0xFF2f5aa8, 1, new int[]{3}));
        root.put("groups", groups);

        ListTag channels = new ListTag();
        // 频道 1：物品——矿石处理（选中），箱子输入 → 熔炉输出（overworld，VALID）
        CompoundTag c1 = channel(1, "item", "矿石处理", 1, 1, 64, 1, "ALWAYS", "ROUND_ROBIN");
        ListTag m1 = new ListTag();
        m1.add(member("minecraft:overworld", BlockPos.asLong(64, 70, -200), "north", "INPUT", 0, "minecraft:chest"));
        m1.add(member("minecraft:overworld", BlockPos.asLong(70, 70, -200), "south", "OUTPUT", 2, "minecraft:furnace"));
        c1.put("members", m1);
        channels.add(c1);

        // 频道 2：物品——熔渣回收（红石 HIGH、PRIORITY），熔炉输入
        CompoundTag c2 = channel(2, "item", "熔渣回收", 1, 1, 16, 1, "HIGH", "PRIORITY");
        ListTag m2 = new ListTag();
        m2.add(member("minecraft:overworld", BlockPos.asLong(72, 69, -198), "east", "INPUT", 1, "minecraft:furnace"));
        c2.put("members", m2);
        channels.add(c2);

        // 频道 3：流体——岩浆供应（跨维度端点 UNKNOWN）
        CompoundTag c3 = channel(3, "fluid", "岩浆供应", 2, 1, 1000, 1, "ALWAYS", "ROUND_ROBIN");
        ListTag m3 = new ListTag();
        m3.add(member("minecraft:the_nether", BlockPos.asLong(100, 40, 30), "up", "INPUT", 0, "minecraft:barrel"));
        m3.add(member("minecraft:overworld", BlockPos.asLong(60, 70, -205), "west", "OUTPUT", 0, "minecraft:barrel"));
        c3.put("members", m3);
        channels.add(c3);

        // 频道 4：能量——主供能（无分组、SPLIT_EVENLY、末地端点）
        CompoundTag c4 = channel(4, "energy", "主供能", -1, 1, 65536, 5, "ALWAYS", "SPLIT_EVENLY");
        ListTag m4 = new ListTag();
        m4.add(member("minecraft:overworld", BlockPos.asLong(50, 70, -190), "north", "INPUT", 3, "minecraft:redstone_block"));
        m4.add(member("minecraft:the_end", BlockPos.asLong(0, 80, 0), "north", "OUTPUT", 0, "minecraft:redstone_block"));
        c4.put("members", m4);
        channels.add(c4);

        // 网络 2 的频道（不在首屏）
        CompoundTag c5 = channel(5, "item", "副网络传输", -1, 2, 32, 2, "ALWAYS", "NEAREST");
        ListTag m5 = new ListTag();
        m5.add(member("minecraft:overworld", BlockPos.asLong(10, 64, 10), "north", "INPUT", 0, "minecraft:hopper"));
        c5.put("members", m5);
        channels.add(c5);

        root.put("channels", channels);
        return root;
    }

    private static CompoundTag net(int id, String name, String owner, String members, boolean chunkLoad, int nextCh) {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putString("name", name);
        t.putString("owner", owner);
        t.putString("admins", "");
        t.putString("members", members);
        t.putString("invited", "");
        t.putInt("nextChannelId", nextCh);
        if (chunkLoad) t.putBoolean("chunkLoad", true);
        return t;
    }

    private static CompoundTag group(int id, String name, int color, int netId, int[] channels) {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putString("name", name);
        t.putInt("color", color);
        t.putInt("networkId", netId);
        t.putIntArray("channels", channels);
        return t;
    }

    private static CompoundTag channel(int id, String type, String name, int groupId, int netId,
                                       int rate, int speed, String redstone, String dist) {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putString("type", type);
        t.putString("name", name);
        t.putInt("groupId", groupId);
        t.putInt("networkId", netId);
        t.putInt("rate", rate);
        t.putInt("speed", speed);
        t.putString("redstoneMode", redstone);
        t.putString("distribution", dist);
        t.put("members", new ListTag());
        return t;
    }

    private static CompoundTag member(String dim, long pos, String face, String role, int priority, String block) {
        CompoundTag t = new CompoundTag();
        t.putString("dim", dim);
        t.putLong("pos", pos);
        t.putString("face", face);
        t.putString("role", role);
        t.putInt("priority", priority);
        t.putString("block", block);
        return t;
    }

    private HSnap() {
    }
}
