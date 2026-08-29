package com.kalloer1.p2p.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/** A typed point-to-point link. Members are pulled from INPUT faces and pushed to OUTPUT faces. */
public class Channel {
    public final int id;
    public ChannelType type;            // mutable: a channel's medium can be switched at any time from the detail screen
    public String name;                 // user-facing label, editable; defaults to "频道 #id"
    public int groupId;
    public int networkId = 0;          // owning network id (for multi-network filtering)
    public int rate = 512;             // items/tick or mB/t or FE/t (configurable, capped at Integer.MAX_VALUE)
    public int speed = 1;              // execute every N ticks
    // Runtime-only backoff clock (never persisted): lastRunTick drives the per-channel cooldown,
    // backoffTicks holds the adaptive idle penalty. Both reset to 0 on load and are regenerated.
    public long lastRunTick = 0;
    public float backoffTicks = 0f;
    public RedstoneMode redstoneMode = RedstoneMode.ALWAYS;
    public Distribution distribution = Distribution.ROUND_ROBIN;
    public final List<Member> members = new ArrayList<>();

    public Channel(int id, ChannelType type, int groupId) {
        this.id = id;
        this.type = type;
        this.name = "频道 #" + id;
        this.groupId = groupId;
    }

    /**
     * Redstone gating, aligned with the four LogisticsNetworks modes.
     * ALWAYS = ignore redstone (always attempt transfer), HIGH = only when powered,
     * LOW = only when not powered, NEVER = never transfer. This preserves the old
     * boolean semantics: redstoneControl=true became HIGH, false became ALWAYS.
     */
    public enum RedstoneMode { ALWAYS, HIGH, LOW, NEVER }

    /**
     * Output distribution modes, aligned with LogisticsNetworks' four modes:
     * ROUND_ROBIN (轮询), PRIORITY (按端点优先级), NEAREST (就近优先), FARTHEST (最远优先).
     * Legacy values EVEN/FIRST from old saves map to ROUND_ROBIN/PRIORITY.
     */
    public enum Distribution { ROUND_ROBIN, PRIORITY, NEAREST, FARTHEST }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("type", type.name());
        tag.putString("name", name == null ? "" : name);
        tag.putInt("groupId", groupId);
        tag.putInt("networkId", networkId);
        tag.putInt("rate", rate);
        tag.putInt("speed", speed);
        tag.putString("redstoneMode", redstoneMode.name());
        tag.putString("distribution", distribution.name());
        ListTag mem = new ListTag();
        for (Member m : members) mem.add(m.serializeNBT());
        tag.put("members", mem);
        return tag;
    }

    public static Channel deserializeNBT(CompoundTag tag) {
        Channel c = new Channel(tag.getInt("id"), ChannelType.valueOf(tag.getString("type")), tag.getInt("groupId"));
        if (tag.contains("name")) c.name = tag.getString("name");
        c.networkId = tag.getInt("networkId");
        c.rate = tag.getInt("rate");
        c.speed = tag.getInt("speed");
        // old saves carried a boolean "redstone" field; migrate it to the enum
        if (tag.contains("redstoneMode")) {
            try { c.redstoneMode = RedstoneMode.valueOf(tag.getString("redstoneMode")); }
            catch (IllegalArgumentException e) { c.redstoneMode = RedstoneMode.ALWAYS; }
        } else if (tag.contains("redstone")) {
            c.redstoneMode = tag.getBoolean("redstone") ? RedstoneMode.HIGH : RedstoneMode.ALWAYS;
        }
        // old saves had EVEN/FIRST; map them onto the new four-mode set
        if (tag.contains("distribution")) {
            String d = tag.getString("distribution");
            c.distribution = switch (d) {
                case "EVEN" -> Distribution.ROUND_ROBIN;
                case "FIRST" -> Distribution.PRIORITY;
                default -> {
                    try { yield Distribution.valueOf(d); }
                    catch (IllegalArgumentException e) { yield Distribution.ROUND_ROBIN; }
                }
            };
        }
        for (Tag t : tag.getList("members", Tag.TAG_COMPOUND)) c.members.add(Member.deserializeNBT((CompoundTag) t));
        return c;
    }
}