package com.kalloer1.p2p.client.screen;

import com.kalloer1.p2p.P2P;
import com.kalloer1.p2p.channel.Channel;
import com.kalloer1.p2p.channel.ChannelType;
import com.kalloer1.p2p.channel.Group;
import com.kalloer1.p2p.channel.Member;
import com.kalloer1.p2p.channel.Network;
import com.kalloer1.p2p.channel.Role;
import com.kalloer1.p2p.client.ClientChannelCache;
import com.kalloer1.p2p.item.ChannelWrench;
import com.kalloer1.p2p.network.BindWrenchC2SPacket;
import com.kalloer1.p2p.network.ChannelActionC2SPacket;
import com.kalloer1.p2p.network.ModMessages;
import com.kalloer1.p2p.network.RequestChannelsC2SPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure-client configuration screen (384×208 panel + 16px top tab = 224 total, within the 232px ceiling).
 * Left: search + group tree + named channel list. Right: selected channel detail with an endpoint table.
 * Endpoints expose a clear "过滤" button (no hidden double-click) and an "⇄" IN/OUT toggle that is sent
 * straight to the server (SET_ROLE). The face/direction of an endpoint is world-bound and labelled as such.
 * The top tab shows the current network and lets the player page between visible networks.
 */
public class ChannelScreen extends Screen {

    private static final int W = 384;
    private static final int H = 208; // panel; +16 top tab = 224 total, within 232 ceiling

    // ---- palette (MC stone) ----
    private static final int STONE = 0xFFC6C6C6, HI = 0xFFFFFFFF, SH = 0xFF555555;
    private static final int LEFT_BG = 0xFFbdbdbd, RIGHT_BG = 0xFFcfcfcf, SEL = 0xFFe8e8e8, GRP_BG = 0xFFa6a6a6, TAB = 0xFF2f2f2f;
    private static final int INK = 0xFF2b2b2b, INK_DIM = 0xFF5a5a5a, TITLE = 0xFFe6e6e6;
    private static final int ITEM_C = 0x8a5a2b, FLUID_C = 0x2f5aa8, ENERGY_C = 0x9c3327;
    private static final int IN_C = 0xFFd93a3a, OUT_C = 0xFF3a7ad9; // INPUT=red, OUTPUT=blue

    private int leftPos, topPos;
    private CompoundTag lastData = null;

    private final List<Channel> channels = new ArrayList<>();
    private final List<Group> groups = new ArrayList<>();
    private final List<Network> nets = new ArrayList<>();
    private int netIndex = 0;
    private int currentNetId = -1;

    private int selectedChannel = -1;
    private int selectedMember = -1;
    private int selectedGroup = -1;      // selectable group header; 删除组 targets it first

    private final Set<Integer> openGroups = new HashSet<>();
    private EditBox searchBox;
    private Button redstoneBtn, distBtn, rateDown, rateUp, speedDown, speedUp, groupBtn, bindBtn, deleteBtn, cleanBtn, createCh, createGrp, deleteGrpBtn, prevNet, nextNet, typeBtn, maxRateBtn;
    private EditBox rateEdit, speedEdit;
    private boolean rateFocusPrev = false, speedFocusPrev = false;

    private final List<Hit> leftRects = new ArrayList<>();
    private final List<MemberBtn> memberBtns = new ArrayList<>();
    private final List<PosHit> posHits = new ArrayList<>();
    private final List<DimHit> dimHits = new ArrayList<>();
    private final List<FaceHit> faceHits = new ArrayList<>();
    private int lastDblIndex = -1;
    private long lastDblTime = 0;
    private int nameHitX, nameHitY, nameHitW, nameHitH;
    private boolean nameHitValid = false;
    private int netNameHitX, netNameHitW;
    private boolean netNameHitValid = false;

    private int leftScroll = 0, leftContentH = 0;
    private int memberScroll = 0, memberContentH = 0;

    public ChannelScreen() { super(Component.literal("P2P 频道管理")); }

    // ============================================================ init
    @Override
    protected void init() {
        leftPos = (this.width - W) / 2;
        topPos = (this.height - H) / 2;   // top tab drawn at topPos-16
        // restore the last GUI state across reopen (selected channel/group, expanded groups)
        selectedChannel = ClientChannelCache.INSTANCE.getSelectedChannelId();
        selectedGroup = ClientChannelCache.INSTANCE.getSelectedGroupId();
        openGroups.clear();
        openGroups.addAll(ClientChannelCache.INSTANCE.getOpenGroups());

        int lx = leftPos + 4, lw = 140;
        searchBox = new EditBox(this.font, lx + 4, topPos + 8, lw - 12, 14, Component.literal("搜索"));
        searchBox.setMaxLength(32);
        searchBox.setBordered(true);
        addRenderableWidget(searchBox);

        int rx = leftPos + 148, rw = W - 148 - 4;
        int btnGap = 4;
        int btnW = (rw - 3 * btnGap) / 4;              // row: 红石/分配/分组/清理空气 — four unified buttons
        redstoneBtn = addRenderableWidget(new FlatButton(Button.builder(Component.literal("红石: " + redstoneLabel(Channel.RedstoneMode.ALWAYS)), b -> sendAction(ChannelActionC2SPacket.Action.TOGGLE_REDSTONE, 0)).bounds(rx, topPos + 30, btnW, 18)));
        distBtn = addRenderableWidget(new FlatButton(Button.builder(Component.literal("分配: " + distLabel(Channel.Distribution.ROUND_ROBIN)), b -> sendAction(ChannelActionC2SPacket.Action.CYCLE_DISTRIBUTION, 0)).bounds(rx + btnW + btnGap, topPos + 30, btnW, 18)));
        groupBtn = addRenderableWidget(new FlatButton(Button.builder(Component.literal("分组: 无"), b -> openGroupSelect()).bounds(rx + 2 * (btnW + btnGap), topPos + 30, btnW, 18)));
        cleanBtn = addRenderableWidget(new FlatButton(Button.builder(Component.literal("清理空气"), b -> sendAction(ChannelActionC2SPacket.Action.CLEAN_AIR, 0)).bounds(rx + 3 * (btnW + btnGap), topPos + 30, btnW, 18)));

        // rate: editable value + fine step + unit
        rateEdit = new EditBox(this.font, rx + 24, topPos + 48, 46, 14, Component.literal(""));
        rateEdit.setMaxLength(10);
        rateEdit.setBordered(true);
        rateEdit.setTextColor(0xFFFFFFFF);
        addRenderableWidget(rateEdit);
        rateDown = addRenderableWidget(new FlatButton(Button.builder(Component.literal("-"), b -> stepRate(-16)).bounds(rx + 74, topPos + 48, 18, 16)));
        rateUp = addRenderableWidget(new FlatButton(Button.builder(Component.literal("+"), b -> stepRate(16)).bounds(rx + 92, topPos + 48, 18, 16)));
        maxRateBtn = addRenderableWidget(new FlatButton(Button.builder(Component.literal("MAX"), b -> sendAction(ChannelActionC2SPacket.Action.SET_RATE, ChannelActionC2SPacket.RATE_MAX)).bounds(rx + 112, topPos + 48, 34, 16)));

        // speed (throttle): how many ticks between passes
        speedEdit = new EditBox(this.font, rx + 24, topPos + 66, 46, 14, Component.literal(""));
        speedEdit.setMaxLength(7);
        speedEdit.setBordered(true);
        speedEdit.setTextColor(0xFFFFFFFF);
        addRenderableWidget(speedEdit);
        speedDown = addRenderableWidget(new FlatButton(Button.builder(Component.literal("-"), b -> stepSpeed(-1)).bounds(rx + 74, topPos + 66, 18, 16)));
        speedUp = addRenderableWidget(new FlatButton(Button.builder(Component.literal("+"), b -> stepSpeed(1)).bounds(rx + 92, topPos + 66, 18, 16)));

        bindBtn = addRenderableWidget(new FlatButton(Button.builder(Component.literal("绑定到扳手"), b -> ModMessages.INSTANCE.sendToServer(new BindWrenchC2SPacket(selectedChannel))).bounds(rx, topPos + H - 22, (rw - 8) / 2, 18)));
        deleteBtn = addRenderableWidget(new FlatButton(Button.builder(Component.literal("删除频道").withStyle(ChatFormatting.RED), b -> sendAction(ChannelActionC2SPacket.Action.DELETE, 0)).bounds(rx + (rw - 8) / 2 + 8, topPos + H - 22, (rw - 8) / 2, 18)));

        int btnL = (lw - 2 * btnGap) / 3;              // bottom-left row: 新建频道/新建组/删除组 — unified size
        createCh = addRenderableWidget(new FlatButton(Button.builder(Component.literal("新建频道"), b -> minecraft.setScreen(new ChannelCreateScreen(this))).bounds(lx, topPos + H - 22, btnL, 18)));
        createGrp = addRenderableWidget(new FlatButton(Button.builder(Component.literal("新建组"), b -> minecraft.setScreen(new NameInputScreen(this, "新建组", "", 1, -1, null))).bounds(lx + btnL + btnGap, topPos + H - 22, btnL, 18)));
        deleteGrpBtn = addRenderableWidget(new FlatButton(Button.builder(Component.literal("删除组").withStyle(ChatFormatting.RED), b -> {
            int targetGrp = selectedGroup;
            if (targetGrp < 0) {
                Channel gc = findChannel(selectedChannel);
                if (gc != null && gc.groupId >= 0) targetGrp = gc.groupId;
            }
            if (targetGrp >= 0) {
                sendAction(ChannelActionC2SPacket.Action.DELETE_GROUP, targetGrp);
                selectedGroup = -1;
                saveUiState();
            }
        }).bounds(lx + 2 * (btnL + btnGap), topPos + H - 22, btnL, 18)));

        prevNet = addRenderableWidget(new FlatButton(Button.builder(Component.literal("<"), b -> pageNet(-1)).bounds(leftPos + W - 56, topPos - 16 + 1, 18, 14)));
        nextNet = addRenderableWidget(new FlatButton(Button.builder(Component.literal(">"), b -> pageNet(1)).bounds(leftPos + W - 36, topPos - 16 + 1, 18, 14)));

        typeBtn = addRenderableWidget(new FlatButton(Button.builder(Component.literal("介质"), b -> cycleType()).bounds(rx + rw - 70, topPos + 10, 66, 14)));

        ModMessages.INSTANCE.sendToServer(new RequestChannelsC2SPacket());
    }

    // ============================================================ data
    private Channel findChannel(int id) {
        for (Channel c : channels) if (c.id == id) return c;
        return null;
    }

    private List<Channel> channelsOf(Group g) {
        List<Channel> out = new ArrayList<>();
        for (Channel c : channels) if (c.groupId == g.id) out.add(c);
        return out;
    }

    private void reparse() {
        CompoundTag data = ClientChannelCache.INSTANCE.getData();
        if (data == null || data == lastData) return;
        lastData = data;
        nets.clear();
        for (Tag t : data.getList("networks", Tag.TAG_COMPOUND)) nets.add(Network.deserializeNBT((CompoundTag) t));
        if (nets.isEmpty()) { currentNetId = -1; }
        else {
            int want = ClientChannelCache.INSTANCE.getCurrentNetworkId();
            if (want >= 0) for (int i = 0; i < nets.size(); i++) if (nets.get(i).id == want) { netIndex = i; break; }
            netIndex = Math.max(0, Math.min(netIndex, nets.size() - 1));
            currentNetId = nets.get(netIndex).id;
            ClientChannelCache.INSTANCE.setCurrentNetworkId(currentNetId);
        }
        channels.clear();
        groups.clear();
        for (Tag t : data.getList("groups", Tag.TAG_COMPOUND)) {
            Group g = Group.deserializeNBT((CompoundTag) t);
            if (g.networkId == currentNetId) groups.add(g);
        }
        for (Tag t : data.getList("channels", Tag.TAG_COMPOUND)) {
            Channel c = Channel.deserializeNBT((CompoundTag) t);
            if (c.networkId == currentNetId) channels.add(c);
        }
        if (findChannel(selectedChannel) == null) selectedChannel = channels.isEmpty() ? -1 : channels.get(0).id;
                if (selectedGroup >= 0) {
                    boolean gOk = false;
                    for (Group g : groups) if (g.id == selectedGroup) { gOk = true; break; }
                    if (!gOk) selectedGroup = -1;
                }
                selectedMember = -1;
                saveUiState();
        P2P.LOGGER.info("[p2p] ChannelScreen reparse: nets={} groups={} channels={} currentNet={} selected={}",
                nets.size(), groups.size(), channels.size(), currentNetId, selectedChannel);
    }

    // ============================================================ render
    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        reparse();
        // Draw our own panel directly; do not rely on Screen.renderBackground() dispatch,
        // because some modded environments (Sodium/ModernUI shader/screen mixins) skip or
        // override the standard background path, leaving the panel invisible.
        drawPanel(gg);
        super.render(gg, mx, my, pt);
        // commit rate/speed edits when the box loses focus (clicked elsewhere / switched field)
        if (rateFocusPrev && !rateEdit.isFocused()) commitRate();
        if (speedFocusPrev && !speedEdit.isFocused()) commitSpeed();
        rateFocusPrev = rateEdit.isFocused();
        speedFocusPrev = speedEdit.isFocused();
        // dimension tooltip: the row only has room for a 2-char label, so the full id is shown on hover
        for (DimHit d : dimHits) {
            if (mx >= d.x && mx <= d.x + d.w && my >= d.y && my <= d.y + d.h) {
                gg.renderTooltip(this.font, Component.literal("维度: " + d.dim), mx, my);
                break;
            }
        }
    }

    private void drawPanel(GuiGraphics gg) {
        int x = leftPos, y = topPos;
        gg.fill(0, 0, this.width, this.height, 0x66000000);

        // top tab (drawn above the panel)
        int ty = y - 16;
        gg.fill(x, ty, x + W, ty + 16, TAB);
        gg.fill(x, ty, x + W, ty + 1, HI);
        gg.drawString(this.font, Component.literal("P2P 频道管理"), x + 6, ty + 4, TITLE, false);
        if (!nets.isEmpty()) {
            String nm = trim(nets.get(netIndex).name, 200);
            int nmX = x + W / 2 - this.font.width(nm) / 2;
            gg.drawString(this.font, Component.literal(nm), nmX, ty + 4, 0xFFc9c9c9, false);
            gg.drawString(this.font, Component.literal("▾"), nmX + this.font.width(nm) + 4, ty + 4, 0xFFc9c9c9, false);
            netNameHitX = nmX; netNameHitW = this.font.width(nm) + 12; netNameHitValid = true;
        } else netNameHitValid = false;
        // panel + bevel
        gg.fill(x, y, x + W, y + H, STONE);
        gg.fill(x, y, x + W, y + 1, HI);
        gg.fill(x, y, x + 1, y + H, HI);
        gg.fill(x, y + H - 1, x + W, y + H, SH);
        gg.fill(x + W - 1, y, x + W, y + H, SH);

        drawLeft(gg);
        drawRight(gg);
    }

    // ---------- left: search + group tree + channel list ----------
    private void drawLeft(GuiGraphics gg) {
        int x = leftPos, y = topPos, lx = x + 4, lw = 140;
        int listTop = y + 28, listBottom = y + H - 26, rowH = 16;
        int listH = listBottom - listTop;

        inset(gg, lx + 1, y + 6, lw - 2, 18);            // search frame

        gg.fill(lx, listTop, lx + lw, listBottom, LEFT_BG);
        inset(gg, lx, listTop, lw, listH);

        List<LeftRow> rows = buildLeftRows();
        leftContentH = rows.size() * rowH;
        leftScroll = clamp(leftScroll, 0, Math.max(0, leftContentH - listH));
        leftRects.clear();

        int baseY = listTop - leftScroll;
        for (int i = 0; i < rows.size(); i++) {
            int ry = baseY + i * rowH;
            if (ry + rowH <= listTop) continue;
            if (ry > listBottom) break;
            LeftRow r = rows.get(i);
            if (r.kind == 0) { // group header — selectable tree node
                boolean gSel = r.id == selectedGroup;
                gg.fill(lx + 1, ry, lx + lw - 1, ry + rowH, gSel ? SEL : GRP_BG);
                gg.fill(lx + 2, ry + 2, lx + 6, ry + rowH - 2, r.color == 0 ? 0xFF6b6b6b : r.color);
                String caret = openGroups.contains(r.id) ? "▾" : "▸";
                gg.drawString(this.font, Component.literal(caret + " " + trim(r.label, lw - 32)), lx + 10, ry + 4, gSel ? 0xFFFFFFFF : INK, false);
                String cnt = String.valueOf(groupCount(r.id));
                gg.drawString(this.font, Component.literal(cnt), lx + lw - 8 - this.font.width(cnt), ry + 4, INK_DIM, false);
                leftRects.add(new Hit(lx + 1, ry, lw - 2, rowH, 0, r.id));
                gg.fill(lx + 1, ry + rowH - 1, lx + lw - 1, ry + rowH, 0xFF9a9a9a);   // separator under header
            } else { // channel row — child rows are indented with a left tree guide line
                boolean sel = r.id == selectedChannel;
                if (sel) gg.fill(lx + 1, ry, lx + lw - 1, ry + rowH, SEL);
                if (r.child) gg.fill(lx + 2, ry + 2, lx + 4, ry + rowH - 2, 0xFF9a9a9a);  // tree guide line
                int ind = r.child ? 26 : 8;
                int rowInk = sel ? 0xFFFFFFFF : INK_DIM;
                gg.drawString(this.font, Component.literal("#" + r.id), lx + ind, ry + 4, rowInk, false);
                gg.drawString(this.font, Component.literal(trim(r.label, lw - 56 - (r.child ? 18 : 0))), lx + ind + 26, ry + 4, rowInk, false);
                typeTag(gg, lx + lw - 18, ry + 2, r.type);
                leftRects.add(new Hit(lx + 1, ry, lw - 2, rowH, 1, r.id));
                gg.fill(lx + 1, ry + rowH - 1, lx + lw - 1, ry + rowH, 0xFF9a9a9a);   // row separator
            }
        }
        if (rows.isEmpty())
            gg.drawString(this.font, Component.literal("无匹配频道"), lx + 8, listTop + 10, INK_DIM, false);
    }

    private List<LeftRow> buildLeftRows() {
        List<LeftRow> out = new ArrayList<>();
        String q = searchBox != null ? searchBox.getValue().trim().toLowerCase() : "";
        for (Group g : groups) {
            if (!q.isEmpty() && !matchGroup(g, q)) continue;
            out.add(new LeftRow(0, g.id, g.name, null, g.color, false));
            // a search forces every matching group open; otherwise the group follows its own expanded state
            boolean open = !q.isEmpty() || openGroups.contains(g.id);
            if (open) for (Channel c : channelsOf(g)) {
                if (!q.isEmpty() && !matchCh(c, q)) continue;
                out.add(new LeftRow(1, c.id, c.name == null ? "#" + c.id : c.name, c.type, typeColor(c.type), true));
            }
        }
        for (Channel c : channels) {
            if (c.groupId >= 0) continue;
            if (!q.isEmpty() && !matchCh(c, q)) continue;
            out.add(channelRow(c));
        }
        return out;
    }

    /** Persist selection/expansion into the client cache so reopening the GUI restores it. */
    private void saveUiState() {
        ClientChannelCache.INSTANCE.setSelectedChannelId(selectedChannel);
        ClientChannelCache.INSTANCE.setSelectedGroupId(selectedGroup);
        ClientChannelCache.INSTANCE.setOpenGroups(openGroups);
    }

    private LeftRow channelRow(Channel c) {
        return new LeftRow(1, c.id, c.name == null ? "#" + c.id : c.name, c.type, typeColor(c.type), false);
    }

    private int groupCount(int gid) {
        int n = 0;
        for (Channel c : channels) if (c.groupId == gid) n++;
        return n;
    }

    // ---------- right: selected channel detail ----------
    private void drawRight(GuiGraphics gg) {
        int x = leftPos, y = topPos, rx = x + 148, rw = W - 148 - 4;
        nameHitValid = false;
        Channel c = findChannel(selectedChannel);
        boolean has = c != null;
        redstoneBtn.visible = distBtn.visible = rateDown.visible = rateUp.visible = speedDown.visible = speedUp.visible
                = rateEdit.visible = speedEdit.visible = groupBtn.visible = bindBtn.visible = deleteBtn.visible = cleanBtn.visible
                = maxRateBtn.visible = has;
        typeBtn.visible = has;
        if (deleteGrpBtn != null) deleteGrpBtn.active = has && (selectedGroup >= 0 || (c != null && c.groupId >= 0));
        if (!has) {
            gg.fill(rx, y + 8, rx + rw, y + H - 26, RIGHT_BG);
            inset(gg, rx, y + 8, rw, (y + H - 26) - (y + 8));
            gg.drawString(this.font, Component.literal("暂无频道"), rx + 10, y + 24, INK, false);
            gg.drawString(this.font, Component.literal("点击下方「+ 新建频道」创建一个"), rx + 10, y + 44, INK_DIM, false);
            gg.drawString(this.font, Component.literal("手持扳手右键容器某个面即可把端点加入频道"), rx + 10, y + 62, INK_DIM, false);
            return;
        }

        // header: #id  name  [type tag] — white text on a dark strip to match the button style
        int headerTop = y + 8, headerH = 18;
        gg.fill(rx, headerTop, rx + rw, headerTop + headerH, TAB);
        inset(gg, rx, headerTop, rw, headerH);
        gg.drawString(this.font, Component.literal("#" + c.id), rx + 4, y + 11, TITLE, false);
        String nm = trim(c.name == null ? "#" + c.id : c.name, rw - 108);
        gg.drawString(this.font, Component.literal(nm), rx + 34, y + 10, TITLE, false);
        nameHitX = rx + 34; nameHitY = y + 8; nameHitW = this.font.width(nm) + 4; nameHitH = 15; nameHitValid = true;

        // config reflects state
        redstoneBtn.setMessage(Component.literal("红石: " + redstoneLabel(c.redstoneMode)));
        distBtn.setMessage(Component.literal("分配: " + distLabel(c.distribution)));
        groupBtn.setMessage(Component.literal("分组: " + groupLabel(c.groupId)));
        typeBtn.setMessage(Component.literal("介质: " + typeLabel(c.type)));
        // rate / speed editable values (refresh the box text only when not actively typing)
        if (!rateEdit.isFocused() && !rateEdit.getValue().equals(String.valueOf(c.rate))) rateEdit.setValue(String.valueOf(c.rate));
        if (!speedEdit.isFocused() && !speedEdit.getValue().equals(String.valueOf(c.speed))) speedEdit.setValue(String.valueOf(c.speed));
        String unitShort = c.type == ChannelType.ITEM ? "物品" : c.type == ChannelType.FLUID ? "mB" : "FE";
        gg.drawString(this.font, Component.literal("速率"), rx, y + 50, INK, false);
        gg.drawString(this.font, Component.literal(unitShort + "≈" + formatPerSec(c.rate, c.speed) + "/s"), rx + 150, y + 58, INK_DIM, false);
        gg.drawString(this.font, Component.literal("节流"), rx, y + 68, INK, false);
        gg.drawString(this.font, Component.literal("每 " + c.speed + " tick"), rx + 114, y + 68, INK_DIM, false);

        // endpoint table (fixed-column layout so direction and IN/OUT line up regardless of coordinate width).
        // Group/clean buttons moved up to the 红石/分配 row, so the table starts sooner and shows more coordinates.
        int mTop = y + 88, mBottom = y + H - 26;
        int rowH = 16, listH = mBottom - mTop - headerH;
        gg.fill(rx, mTop, rx + rw, mBottom, RIGHT_BG);
        inset(gg, rx, mTop, rw, mBottom - mTop);
        // header strip
        gg.fill(rx + 1, mTop + 1, rx + rw - 1, mTop + headerH - 1, SEL);
        gg.drawString(this.font, Component.literal("维度"), rx + 4, mTop + 5, 0xFF2b2b2b, false);
        gg.drawString(this.font, Component.literal("坐标"), rx + 29, mTop + 5, 0xFF2b2b2b, false);
        gg.drawString(this.font, Component.literal("面"), rx + 93, mTop + 5, 0xFF2b2b2b, false);
        gg.drawString(this.font, Component.literal("流向"), rx + 108, mTop + 5, 0xFF2b2b2b, false);
        String cnt = c.members.size() + " 个";
        gg.drawString(this.font, Component.literal(cnt), rx + rw - 6 - this.font.width(cnt), mTop + 5, 0xFF2b2b2b, false);

        memberBtns.clear();
        posHits.clear();
        dimHits.clear();
        if (c.members.isEmpty()) {
            gg.drawString(this.font, Component.literal("这个频道还没有端点"), rx + 10, mTop + 26, 0xFF2b2b2b, false);
            gg.drawString(this.font, Component.literal("手持扳手右键容器某个面即可加入"), rx + 10, mTop + 42, 0xFF5a5a5a, false);
            return;
        }
        memberContentH = c.members.size() * rowH;
        memberScroll = clamp(memberScroll, 0, Math.max(0, memberContentH - listH));
        int baseY = mTop + headerH - memberScroll;

        // fixed column positions (relative to rx). Coordinates are LEFT-aligned close to the dimension label so
        // every row starts at the same x; longer coordinates are ellipsised instead of pushing the other columns.
        final int colDimX = 2, colDimW = 4;             // colour pill
        final int colDimTextX = 8;                      // 2-char dimension label (tooltip = full id)
        final int colPosX = 29, colPosW = 60;
        final int colFaceX = 92, colFaceW = 14;         // direction column, centered
        final int colRoleX = 108;                       // IN/OUT square + text
        final int colToggleX = 108, colToggleW = 16;    // IN/OUT swap button
        final int colPriorityX = 125, colPriorityW = 16; // priority value (click to type any number)
        final int colFilterX = 142, colFilterW = 38;
        final int colDelX = 208, colDelW = 20;          // remove endpoint (red ×)

        for (int i = 0; i < c.members.size(); i++) {
            int ry = baseY + i * rowH;
            if (ry + rowH <= mTop + headerH) continue;
            if (ry > mBottom) break;
            Member m = c.members.get(i);
            boolean sel = i == selectedMember;
            if (sel) gg.fill(rx + 1, ry, rx + rw - 1, ry + rowH, SEL);
            // subtle row separator
            gg.fill(rx + 2, ry + rowH - 1, rx + rw - 2, ry + rowH, 0xFF999999);

            // dimension: colour pill + short label (hover shows the full dimension id)
            String dimId = m.dim.toString();
            gg.fill(rx + colDimX, ry + 3, rx + colDimX + colDimW, ry + 12, dimColor(dimId) | 0xFF000000);
            String dimS = dimShort(dimId);
            gg.drawString(this.font, Component.literal(dimS), rx + colDimTextX, ry + 4, 0xFF2b2b2b, false);
            dimHits.add(new DimHit(dimId, rx + colDimX, ry + 2, colDimTextX - colDimX + this.font.width(dimS) + 2, 14));

            // coordinate (left-aligned at a fixed x; ellipsised if a huge coordinate would overflow)
            String pos = trim(m.pos.getX() + "," + m.pos.getY() + "," + m.pos.getZ(), colPosW);
            int posX = rx + colPosX;
            gg.drawString(this.font, Component.literal(pos), posX, ry + 4, 0xFF2b2b2b, false);
            posHits.add(new PosHit(i, posX - 2, ry + 2, this.font.width(pos) + 4, 14));

            // face direction (centered in its column)
            String face = faceShort(m.face);
            int faceW = this.font.width(face);
            gg.drawString(this.font, Component.literal(face), rx + colFaceX + (colFaceW - faceW) / 2, ry + 4, 0xFF2b2b2b, false);
            faceHits.add(new FaceHit(i, rx + colFaceX - 2, ry + 1, colFaceW + 4, 14));

            // action buttons (fixed positions)
            int tbX = rx + colToggleX, pX = rx + colPriorityX, fbX = rx + colFilterX, dbX = rx + colDelX;
            // role-tinted IN/OUT button (click to swap role)
            int roleColor = m.role == Role.INPUT ? IN_C : OUT_C;
            gg.fill(tbX, ry + 1, tbX + colToggleW, ry + 15, roleColor);
            gg.fill(tbX + colToggleW - 1, ry + 1, tbX + colToggleW, ry + 15, 0x44000000);
            String roleS = m.role == Role.INPUT ? "IN" : "OUT";
            gg.drawString(this.font, Component.literal(roleS), tbX + 3, ry + 4, 0xFFFFFFFF, false);
            addInline(gg, pX, ry + 1, colPriorityW, 14, "P" + m.priority, false);
            addInline(gg, fbX, ry + 1, colFilterW, 14, "过滤", hasFilter(m));
            addDanger(gg, dbX, ry + 1, colDelW, 14, "×");
            memberBtns.add(new MemberBtn(i, 0, fbX, ry + 1, colFilterW, 14));
            memberBtns.add(new MemberBtn(i, 1, tbX, ry + 1, colToggleW, 14));
            memberBtns.add(new MemberBtn(i, 2, dbX, ry + 1, colDelW, 14));
            memberBtns.add(new MemberBtn(i, 3, pX, ry + 1, colPriorityW, 14));
        }
    }

    // a faux button drawn with the bevel language (real input handled in mouseClicked)
    private void addInline(GuiGraphics gg, int bx, int by, int bw, int bh, String label, boolean marked) {
        gg.fill(bx, by, bx + bw, by + bh, 0xFFdedede);
        gg.fill(bx, by, bx + bw, by + 1, HI);
        gg.fill(bx, by, bx + 1, by + bh, HI);
        gg.fill(bx, by + bh - 1, bx + bw, by + bh, SH);
        gg.fill(bx + bw - 1, by, bx + bw, by + bh, SH);
        gg.drawString(this.font, Component.literal(label), bx + (bw - this.font.width(label)) / 2, by + 4, marked ? 0xFF9a7c1f : INK, false);
    }

    // ============================================================ input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int ix = (int) mx, iy = (int) my;
        if (netNameHitValid && iy >= topPos - 16 && iy <= topPos - 2 && ix >= netNameHitX && ix <= netNameHitX + netNameHitW) {
            minecraft.setScreen(new NetworkScreen());
            return true;
        }
        if (nameHitValid && ix >= nameHitX && ix <= nameHitX + nameHitW && iy >= nameHitY && iy <= nameHitY + nameHitH) {
            Channel c = findChannel(selectedChannel);
            if (c != null) minecraft.setScreen(new NameInputScreen(this, "重命名频道", c.name == null ? "" : c.name, 0, c.id, null));
            return true;
        }
        // Double-click a member's coordinate text → jump to that block: draw a see-through red frame in-world
        // and close the GUI so the player can look at the highlighted block.
        long now = System.currentTimeMillis();
        for (PosHit ph : posHits) {
            if (ix >= ph.x && ix <= ph.x + ph.w && iy >= ph.y && iy <= ph.y + ph.h) {
                if (ph.index == lastDblIndex && now - lastDblTime < 350) {
                    Channel c = findChannel(selectedChannel);
                    if (c != null && ph.index < c.members.size()) {
                        Member m = c.members.get(ph.index);
                        ClientChannelCache.INSTANCE.addHighlight(m.pos);
                        P2P.LOGGER.info("[p2p] jump-to-block highlight: {}", m.pos);
                        minecraft.setScreen(null);
                        return true;
                    }
                }
                lastDblIndex = ph.index;
                lastDblTime = now;
                return true;
            }
        }
        for (FaceHit fh : faceHits) {
            if (ix >= fh.x && ix <= fh.x + fh.w && iy >= fh.y && iy <= fh.y + fh.h) {
                Channel c = findChannel(selectedChannel);
                if (c != null && fh.index < c.members.size()) cycleFace(c.members.get(fh.index));
                return true;
            }
        }
        for (MemberBtn mb : memberBtns) {
            if (ix >= mb.x && ix <= mb.x + mb.w && iy >= mb.y && iy <= mb.y + mb.h) {
                Channel c = findChannel(selectedChannel);
                if (c == null || mb.index >= c.members.size()) return true;
                Member m = c.members.get(mb.index);
                if (mb.kind == 0) openFilter(m);
                else if (mb.kind == 1) toggleRole(m);
                else if (mb.kind == 2) removeMember(m);
                else cyclePriority(m);
                return true;
            }
        }
        for (Hit h : leftRects) {
            if (ix >= h.x && ix <= h.x + h.w && iy >= h.y && iy <= h.y + h.h) {
                if (h.kind == 0) {
                    if (openGroups.contains(h.id)) openGroups.remove(h.id); else openGroups.add(h.id);
                    selectedGroup = h.id;      // group headers are selectable; 删除组 targets them first
                } else {
                    selectedChannel = h.id;
                    selectedMember = -1;
                    selectedGroup = -1;
                }
                saveUiState();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int lx = leftPos + 4, lw = 140, listBottom = topPos + H - 26;
        int rx = leftPos + 148, rw = W - 148 - 4;
        if (mx >= lx && mx <= lx + lw && my >= topPos + 28 && my <= listBottom)
            leftScroll = clamp(leftScroll - (int) delta * 16, 0, Math.max(0, leftContentH - (listBottom - (topPos + 28))));
        else if (mx >= rx && mx <= rx + rw && my >= topPos + 88 && my <= listBottom)
            memberScroll = clamp(memberScroll - (int) delta * 16, 0, Math.max(0, memberContentH - (listBottom - (topPos + 88 + 18))));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == InputConstants.KEY_ESCAPE) { commitRate(); commitSpeed(); minecraft.setScreen(null); return true; }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            if (rateEdit.isFocused()) { commitRate(); rateEdit.setFocused(false); return true; }
            if (speedEdit.isFocused()) { commitSpeed(); speedEdit.setFocused(false); return true; }
        }
        return super.keyPressed(key, scan, mods);
    }

    // ============================================================ actions
    private int parseBox(EditBox b, int fallback) {
        String s = b.getValue().trim();
        if (s.isEmpty()) return fallback;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return fallback; }
    }

    private void commitRate() {
        Channel c = findChannel(selectedChannel);
        if (c == null) return;
        int v = Math.max(1, Math.min(ChannelActionC2SPacket.RATE_MAX, parseBox(rateEdit, c.rate)));
        if (v != c.rate) sendAction(ChannelActionC2SPacket.Action.SET_RATE, v);
        if (!rateEdit.isFocused() && !rateEdit.getValue().equals(String.valueOf(c.rate))) rateEdit.setValue(String.valueOf(c.rate));
    }

    private void stepRate(int d) {
        Channel c = findChannel(selectedChannel);
        if (c == null) return;
        sendAction(ChannelActionC2SPacket.Action.SET_RATE, Math.max(1, Math.min(ChannelActionC2SPacket.RATE_MAX, parseBox(rateEdit, c.rate) + d)));
    }

    private void commitSpeed() {
        Channel c = findChannel(selectedChannel);
        if (c == null) return;
        int v = Math.max(1, Math.min(ChannelActionC2SPacket.RATE_MAX, parseBox(speedEdit, c.speed)));
        if (v != c.speed) sendAction(ChannelActionC2SPacket.Action.SET_SPEED, v);
        if (!speedEdit.isFocused() && !speedEdit.getValue().equals(String.valueOf(c.speed))) speedEdit.setValue(String.valueOf(c.speed));
    }

    private void stepSpeed(int d) {
        Channel c = findChannel(selectedChannel);
        if (c == null) return;
        sendAction(ChannelActionC2SPacket.Action.SET_SPEED, Math.max(1, Math.min(ChannelActionC2SPacket.RATE_MAX, parseBox(speedEdit, c.speed) + d)));
    }

    private void sendAction(ChannelActionC2SPacket.Action action, int val) {
        if (selectedChannel < 0) return;
        ModMessages.INSTANCE.sendToServer(new ChannelActionC2SPacket(selectedChannel, action, null, val, null, false, null, ""));
    }

    private void openGroupSelect() {
        Channel c = findChannel(selectedChannel);
        if (c == null) return;
        minecraft.setScreen(new GroupSelectScreen(this, selectedChannel, c.groupId, groups));
    }

    private void cycleType() {
        Channel c = findChannel(selectedChannel);
        if (c == null) return;
        ChannelType[] vals = ChannelType.values();
        ChannelType next = vals[(c.type.ordinal() + 1) % vals.length];
        ModMessages.INSTANCE.sendToServer(new ChannelActionC2SPacket(c.id, ChannelActionC2SPacket.Action.SET_TYPE, next.name(), 0, null, false, null, ""));
    }

    private void pageNet(int dir) {
        if (nets.isEmpty()) return;
        netIndex = Math.max(0, Math.min(nets.size() - 1, netIndex + dir));
        selectedChannel = -1;
        selectedMember = -1;
        selectedGroup = -1;
        saveUiState();
    }

    private void openFilter(Member m) {
        Channel c = findChannel(selectedChannel);
        if (c == null) return;
        minecraft.setScreen(new FilterEditor(this, c.id, c.type, m));
    }

    private void toggleRole(Member m) {
        Channel c = findChannel(selectedChannel);
        if (c == null) return;
        int target = (m.role == Role.INPUT) ? Role.OUTPUT.ordinal() : Role.INPUT.ordinal();
        ModMessages.INSTANCE.sendToServer(new ChannelActionC2SPacket(c.id, ChannelActionC2SPacket.Action.SET_ROLE, null, target, m.key(), false, null, ""));
    }

    /** Send REMOVE_MEMBER to the server and drop the row locally for instant feedback. */
    private void removeMember(Member m) {
        Channel c = findChannel(selectedChannel);
        if (c == null) return;
        ModMessages.INSTANCE.sendToServer(new ChannelActionC2SPacket(c.id, ChannelActionC2SPacket.Action.REMOVE_MEMBER, null, 0, m.key(), false, null, ""));
        c.members.remove(m);
        selectedMember = -1;
    }

    /** Faux bevel button with red text, used for destructive row actions. */
    private void addDanger(GuiGraphics gg, int bx, int by, int bw, int bh, String label) {
        gg.fill(bx, by, bx + bw, by + bh, 0xFFdedede);
        gg.fill(bx, by, bx + bw, by + 1, HI);
        gg.fill(bx, by, bx + 1, by + bh, HI);
        gg.fill(bx, by + bh - 1, bx + bw, by + bh, SH);
        gg.fill(bx + bw - 1, by, bx + bw, by + bh, SH);
        gg.drawString(this.font, Component.literal(label), bx + (bw - this.font.width(label)) / 2, by + 4, 0xFFc03030, false);
    }

    /**
     * Vanilla-style grey button (same chrome as Sky Logistics' ConfigPanel buttons):
     * dark outline + #C6C6C6 face + white top/left highlight + dark bottom/right shadow,
     * black text. Hover lightens the face; disabled falls back to a flat dark grey.
     */
    private static class FlatButton extends Button {
        FlatButton(Builder builder) {
            super(builder);
        }

        @Override
        protected void renderWidget(GuiGraphics gg, int mx, int my, float pt) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            Component msg = getMessage();
            if (active) {
                int face = isHoveredOrFocused() ? 0xFFd8d8d8 : 0xFFc6c6c6;
                gg.fill(x, y, x + w, y + h, 0xFF373737);            // dark outline
                gg.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);  // grey face
                gg.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFFFFFFF);   // top highlight
                gg.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFFFFFFF);   // left highlight
                gg.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF555555); // bottom shadow
                gg.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, 0xFF555555); // right shadow
            } else {
                gg.fill(x, y, x + w, y + h, 0xFF373737);
                gg.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF8b8b8b);   // disabled face
            }
            if (msg != null) {
                Font f = Minecraft.getInstance().font;
                gg.drawString(f, msg, x + (w - f.width(msg)) / 2,
                        y + (h - 8) / 2, active ? 0xFF000000 : 0xFF707070, false);
            }
        }
    }

    // ============================================================ helpers
    private String groupLabel(int gid) {
        if (gid < 0) return "无";
        for (Group g : groups) if (g.id == gid) return g.name;
        return "无";
    }

    private boolean hasFilter(Member m) { return (m.extractFilter != null) || (m.insertFilter != null); }

    private boolean matchCh(Channel c, String q) {
        if (q.isEmpty()) return true;
        if (c.name != null && c.name.toLowerCase().contains(q)) return true;
        if (String.valueOf(c.id).contains(q)) return true;
        if (typeLabel(c.type).contains(q) || c.type.name().toLowerCase().contains(q)) return true;
        for (Member m : c.members)
            if (m.dim.toString().toLowerCase().contains(q)
                    || (m.pos.getX() + "," + m.pos.getY() + "," + m.pos.getZ()).contains(q)) return true;
        return false;
    }

    private boolean matchGroup(Group g, String q) {
        if (g.name.toLowerCase().contains(q)) return true;
        for (Channel c : channelsOf(g)) if (matchCh(c, q)) return true;
        return false;
    }

    private static int typeColor(ChannelType t) {
        return t == ChannelType.ITEM ? ITEM_C : t == ChannelType.FLUID ? FLUID_C : ENERGY_C;
    }

    private static String typeLabel(ChannelType t) {
        return t == ChannelType.ITEM ? "物品" : t == ChannelType.FLUID ? "流体" : "能量";
    }

    private static String distLabel(Channel.Distribution d) {
        return switch (d) {
            case PRIORITY -> "优先";
            case NEAREST -> "就近";
            case FARTHEST -> "最远";
            default -> "轮询";
        };
    }

    private static String redstoneLabel(Channel.RedstoneMode m) {
        return switch (m) {
            case HIGH -> "高电平";
            case LOW -> "低电平";
            case NEVER -> "禁用";
            default -> "忽略";
        };
    }

    /** Format a per-second rate for display: 1M / 100k / 2.5G; display-only, never changes the stored value. */
    private static String formatPerSec(int rate, int speed) {
        long perSec = (long) rate * 20 / Math.max(1, speed);
        if (perSec >= 1_000_000_000L) return trimZero(perSec / 1_000_000_000.0) + "G";
        if (perSec >= 1_000_000L) return trimZero(perSec / 1_000_000.0) + "M";
        if (perSec >= 1_000L) return trimZero(perSec / 1_000.0) + "k";
        return String.valueOf(perSec);
    }

    private static String trimZero(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        String s = String.format("%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** Cycle the endpoint's bound face to the next direction (UP/DOWN/NORTH/SOUTH/WEST/EAST). */
    private void cycleFace(Member m) {
        Channel c = findChannel(selectedChannel);
        if (c == null) return;
        Direction[] dirs = Direction.values();
        Direction next = dirs[(m.face.get3DDataValue() + 1) % dirs.length];
        ModMessages.INSTANCE.sendToServer(new ChannelActionC2SPacket(c.id, ChannelActionC2SPacket.Action.SET_FACE,
                null, next.get3DDataValue(), m.key(), false, null, ""));
        int i = c.members.indexOf(m);
        if (i >= 0) c.members.set(i, m.withFace(next));
    }

    /** Open a number prompt so the player can type any routing priority (higher = preferred first). */
    private void cyclePriority(Member m) {
        Channel c = findChannel(selectedChannel);
        if (c == null) return;
        minecraft.setScreen(new NameInputScreen(this, "设置优先级", String.valueOf(m.priority), 2, c.id, m.key()));
    }

    private static int dimColor(String dim) {
        if (dim.contains("nether")) return 0x8a3423;
        if (dim.contains("end")) return 0x5b4a7a;
        if (!dim.startsWith("minecraft:")) return 0x2f6d7a;   // modded dimension
        return 0x3f6d32;
    }

    /** Two-character dimension label that fits the endpoint row; the full id is available as a tooltip. */
    private static String dimShort(String dim) {
        if (dim.equals("minecraft:overworld")) return "主界";
        if (dim.equals("minecraft:the_nether")) return "下界";
        if (dim.equals("minecraft:the_end")) return "末地";
        int colon = dim.indexOf(':');
        String ns = colon > 0 ? dim.substring(0, colon) : dim;
        if (ns.equals("minecraft")) {
            String path = colon > 0 ? dim.substring(colon + 1) : dim;
            ns = path;
        }
        return ns.length() >= 2 ? ns.substring(0, 2).toUpperCase() : ns.toUpperCase();
    }

    private static String faceShort(net.minecraft.core.Direction f) {
        return switch (f) {
            case UP -> "上"; case DOWN -> "下"; case NORTH -> "北"; case SOUTH -> "南"; case EAST -> "东"; case WEST -> "西";
        };
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private String trim(String s, int maxPx) {
        if (s == null) return "";
        if (this.font.width(s) <= maxPx) return s;
        while (this.font.width(s + "…") > maxPx && s.length() > 1) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private void inset(GuiGraphics gg, int x, int y, int w, int h) {
        gg.fill(x, y, x + w, y + 1, SH);
        gg.fill(x, y, x + 1, y + h, SH);
        gg.fill(x, y + h - 1, x + w, y + h, HI);
        gg.fill(x + w - 1, y, x + w, y + h, HI);
    }

    private void typeTag(GuiGraphics gg, int bx, int by, ChannelType t) {
        int col = typeColor(t);
        gg.fill(bx, by, bx + 28, by + 11, col);
        gg.fill(bx, by, bx + 28, by + 1, 0x55ffffff & 0xFFFFFFFF);
        gg.drawString(this.font, Component.literal(typeLabel(t)), bx + 4, by + 2, 0xFFFFFFFF);
    }

    private record LeftRow(int kind, int id, String label, ChannelType type, int color, boolean child) {}
    private record Hit(int x, int y, int w, int h, int kind, int id) {}
    private record MemberBtn(int index, int kind, int x, int y, int w, int h) {}
    private record PosHit(int index, int x, int y, int w, int h) {}
    private record DimHit(String dim, int x, int y, int w, int h) {}
    private record FaceHit(int index, int x, int y, int w, int h) {}
}
