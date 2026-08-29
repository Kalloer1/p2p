package com.kalloer1.p2p.client;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.HashSet;
import java.util.Set;

/** Holds the latest S2C channel snapshot for the client GUI. */
public class ClientChannelCache {
    public static final ClientChannelCache INSTANCE = new ClientChannelCache();

    private CompoundTag data;
    private int currentNetworkId = -1;   // which network the ChannelScreen is currently showing

    // GUI state restored when the ChannelScreen reopens (same-client in-memory, not persisted to disk)
    private int selectedChannelId = -1;
    private int selectedGroupId = -1;
    private final Set<Integer> openGroups = new HashSet<>();

    // GUI "jump to block" highlight: a BlockPos to draw a see-through red frame around, plus its expiry.
    private BlockPos highlightPos;
    private long highlightExpire;

    public void setData(CompoundTag data) { this.data = data; }
    public CompoundTag getData() { return data; }

    public int getCurrentNetworkId() { return currentNetworkId; }
    public void setCurrentNetworkId(int id) { this.currentNetworkId = id; }

    /** Register a block to be highlighted (x-ray red frame) for the next few seconds. */
    public void addHighlight(BlockPos pos) {
        this.highlightPos = pos;
        this.highlightExpire = System.currentTimeMillis() + 10_000L; // 10s window
    }
    public BlockPos getHighlightPos() { return highlightPos; }
    public long getHighlightExpire() { return highlightExpire; }
    public void clearHighlight() { this.highlightPos = null; }

    public int getSelectedChannelId() { return selectedChannelId; }
    public void setSelectedChannelId(int id) { this.selectedChannelId = id; }
    public int getSelectedGroupId() { return selectedGroupId; }
    public void setSelectedGroupId(int id) { this.selectedGroupId = id; }
    public Set<Integer> getOpenGroups() { return openGroups; }
    public void setOpenGroups(Set<Integer> s) { openGroups.clear(); openGroups.addAll(s); }
}
