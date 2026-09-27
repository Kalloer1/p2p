package com.kalloer1.p2p.client.screen;

import com.kalloer1.p2p.Lang;
import com.kalloer1.p2p.P2P;
import com.kalloer1.p2p.api.ChannelScreenExtension;
import com.kalloer1.p2p.api.ChannelScreenExtensions;
import com.kalloer1.p2p.api.DetailContext;
import com.kalloer1.p2p.channel.Channel;
import com.kalloer1.p2p.channel.Filter;
import com.kalloer1.p2p.channel.Group;
import com.kalloer1.p2p.channel.Member;
import com.kalloer1.p2p.channel.Network;
import com.kalloer1.p2p.channel.Role;
import com.kalloer1.p2p.client.ClientChannelCache;
import com.kalloer1.p2p.medium.Medium;
import com.kalloer1.p2p.medium.Mediums;
import com.kalloer1.p2p.network.ChannelActionC2SPacket;
import com.kalloer1.p2p.network.ModMessages;
import com.kalloer1.p2p.network.RequestChannelsC2SPacket;
import com.kalloer1.p2p.network.SetBindChannelC2SPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure-client configuration screen on the 1.2.0 iOS geometry contract: a single 408x240 panel whose first
 * 20px row is the navigation bar (the 16px top tab strip is gone), a 140-wide channel list on the left,
 * a 244-wide detail column on the right and a 20px toolbar at the bottom. All sizes come from
 * .grill-research/ios-ui-spec.md section 4 and are mirrored by {@link DetailContext} for per-medium panels.
 *
 * <p>Endpoints expose a clear "过滤" button (no hidden double-click) and an IN/OUT badge that is sent
 * straight to the server (SET_ROLE). The face/direction of an endpoint is world-bound and labelled as such.
 */
public class ChannelScreen extends Screen {

    private static final int W = 408;
    private static final int H = 240;

    // ---- section geometry (ios-ui-spec.md §4) ----
    private static final int NAV_H = P2PTheme.NAV_H;              // 0..20
    private static final int BAND_TOP = 20, BAND_H = 14;          // search + config pills: 20..34
    private static final int LEFT_X = 8, LEFT_W = 140;
    private static final int RIGHT_X = 156, RIGHT_W = 244;
    private static final int FRAME_TOP = 44, FRAME_BOTTOM = 204;  // left list + right detail + addon frame
    private static final int HEAD_TOP = 44, HEAD_H = 18;          // header card
    private static final int RATE_TOP = 70, RATE_ROW = 18;        // rate + throttle card (70..106)
    private static final int TABLE_TOP = 114, TABLE_HEAD = 16;    // endpoint card (114..204)
    private static final int TOOLBAR_TOP = 212, TOOLBAR_H = P2PTheme.TOOLBAR_H;
    private static final int ROW_H = P2PTheme.ROW_H;

    // ---- endpoint columns, relative to the card's inner left edge ----
    // 1.3.0 重排（真机反馈：旧几何里维度短名 25..43 会压进坐标列起点 40，坐标文本与
    // 「主界」连成一串，表头整体错位）。现列宽重新分配且每列表头对齐本列内容：
    // 色条 19、短名 26..44、坐标 46..96、面 98..112 居中、徽牌 116..138、
    // 优先级 142、过滤 164..204、× 208..222（卡内右缘 228，无溢出）。
    private static final int COL_ICON = 0;
    private static final int COL_DIM = 19, COL_DIM_W = 4;
    private static final int COL_DIM_TEXT = 26;
    private static final int COL_POS = 46, COL_POS_W = 50;
    private static final int COL_FACE = 98, COL_FACE_W = 14;
    private static final int COL_ROLE = 116;
    private static final int COL_PRIORITY = 142, COL_PRIORITY_W = 18;
    private static final int COL_FILTER = 164, COL_FILTER_W = 40;
    private static final int COL_DELETE = 208;

    private int leftPos, topPos;
    private CompoundTag lastData = null;

    private final List<Channel> channels = new ArrayList<>();
    private final List<Group> groups = new ArrayList<>();
    private final List<Network> nets = new ArrayList<>();
    private int netIndex = 0;
    private int currentNetId = -1;

    private int selectedChannel = -1;
    private int selectedGroup = -1;      // selectable group header; 删除组 targets it first

    // ---- 端点选中（1.3.0 新功能，纯 client 端状态） ----
    /** 多选集合，元素是 {@link Member#key()}；行内控件点击不改选区，只有行的空白区参与选中。 */
    private final Set<String> selectedEndpoints = new HashSet<>();
    /** Shift 范围选择的锚点行索引；-1 = 尚未建立锚点。 */
    private int selAnchor = -1;
    /** 最后一次交互选中的端点 key（Ctrl+C 复制它的配置；多选时即“最后选中”）。 */
    private String selLastKey = null;

    /**
     * 端点配置剪贴板。static：复制来源允许跨频道（频道 A 复制 → 频道 B 粘贴），且界面
     * 重开后仍保留；一次 Ctrl+V 的目标永远限定在当前频道内。过滤 tag 是完整序列化结果
     * （含 addon 段），方向语义以粘贴目标为准。
     */
    private static CompoundTag clipFilterTag;
    private static int clipPriority;
    private static boolean clipInput;

    private final Set<Integer> openGroups = new HashSet<>();
    private EditBox searchBox;
    private P2PFlatButton redstoneBtn, distBtn, rateDown, rateUp, speedDown, speedUp, groupBtn, bindBtn, deleteBtn, cleanBtn, createCh, createGrp, deleteGrpBtn, prevNet, nextNet, typeBtn, maxRateBtn, chunkLoadBtn;
    private EditBox rateEdit, speedEdit;
    private boolean rateFocusPrev = false, speedFocusPrev = false;

    private final List<Hit> leftRects = new ArrayList<>();
    private final List<MemberBtn> memberBtns = new ArrayList<>();
    /** 双击定位热区：端点行的物品图标 + 维度色条段（1.3.1 从坐标文本挪过来）。单击=选中，双击=定位。 */
    private final List<JumpHit> jumpHits = new ArrayList<>();
    private final List<BlockHit> blockHits = new ArrayList<>();
    private final List<DimHit> dimHits = new ArrayList<>();
    /** 面列专属热区：点击循环换面（SET_FACE），与 IN/OUT 徽牌同级的行内控件。 */
    private final List<FaceHit> faceHits = new ArrayList<>();
    private int lastDblIndex = -1;
    private long lastDblTime = 0;
    /** 当前频道里"已失效"（维度已加载但方块为空气）端点的实时数量，驱动清理按钮显隐。 */
    private int invalidCount = 0;
    private int nameHitX, nameHitY, nameHitW, nameHitH;
    private boolean nameHitValid = false;
    private int netNameHitX, netNameHitW;
    private boolean netNameHitValid = false;

    private int leftScroll = 0, leftContentH = 0;
    private int memberScroll = 0, memberContentH = 0;

    public ChannelScreen() { super(Lang.t(Lang.UI_TITLE)); }

    // ============================================================ init
    @Override
    protected void init() {
        leftPos = (this.width - W) / 2;
        topPos = (this.height - H) / 2;
        // restore the last GUI state across reopen (selected channel/group, expanded groups)
        selectedChannel = ClientChannelCache.INSTANCE.getSelectedChannelId();
        selectedGroup = ClientChannelCache.INSTANCE.getSelectedGroupId();
        openGroups.clear();
        openGroups.addAll(ClientChannelCache.INSTANCE.getOpenGroups());

        int lx = leftPos + LEFT_X;
        searchBox = new EditBox(this.font, lx + 16, topPos + BAND_TOP + 1, LEFT_W - 24, 12, Lang.t(Lang.UI_SEARCH));
        searchBox.setMaxLength(32);
        searchBox.setBordered(false);
        searchBox.setTextColor(P2PTheme.TEXT_PRIMARY);
        searchBox.setHint(Lang.t(Lang.UI_SEARCH));
        addRenderableWidget(searchBox);

        int rx = leftPos + RIGHT_X;
        int bandY = topPos + BAND_TOP;
        int btnGap = 4;
        int btnW = (RIGHT_W - 5 * btnGap) / 6;              // row: 介质 + 红石/分配/分组/清理空气/区块加载
        // spec v1.1：配置 pill 只显短词标签，当前值放 tooltip（drawRight 里逐帧刷新），非默认态 TINT 提亮
        redstoneBtn = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_PILL_REDSTONE), b -> sendAction(ChannelActionC2SPacket.Action.TOGGLE_REDSTONE, 0)).bounds(rx, bandY, btnW, BAND_H)));
        distBtn = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_PILL_DIST), b -> sendAction(ChannelActionC2SPacket.Action.CYCLE_DISTRIBUTION, 0)).bounds(rx + btnW + btnGap, bandY, btnW, BAND_H)));
        groupBtn = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_PILL_GROUP), b -> openGroupSelect()).bounds(rx + 2 * (btnW + btnGap), bandY, btnW, BAND_H)));
        cleanBtn = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_PILL_CLEAN), b -> sendAction(ChannelActionC2SPacket.Action.CLEAN_AIR, 0)).bounds(rx + 3 * (btnW + btnGap), bandY, btnW, BAND_H)));
        chunkLoadBtn = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_PILL_CHUNKLOAD), b -> sendAction(ChannelActionC2SPacket.Action.TOGGLE_CHUNKLOAD, 0)).bounds(rx + 4 * (btnW + btnGap), bandY, btnW, BAND_H)));
        chunkLoadBtn.setStyle(P2PFlatButton.Style.TOGGLE);
        // 介质钮留在配置行而不是详情卡里：附属面板接管 44..204 全帧时它仍必须可点。
        typeBtn = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_MEDIUM), b -> cycleType()).bounds(rx + RIGHT_W - 56, bandY, 56, BAND_H)));
        typeBtn.setStyle(P2PFlatButton.Style.CHIP);

        // rate: editable value + fine step + unit
        rateEdit = new EditBox(this.font, rxF() + 48, topPos + RATE_TOP + 3, 30, 12, Component.literal(""));
        rateEdit.setMaxLength(10);
        rateEdit.setBordered(false);
        rateEdit.setTextColor(P2PTheme.TEXT_PRIMARY);
        addRenderableWidget(rateEdit);
        rateDown = stepper("-", b -> stepRate(-16), rxF() + 30, topPos + RATE_TOP + 2);
        rateUp = stepper("+", b -> stepRate(16), rxF() + 82, topPos + RATE_TOP + 2);
        maxRateBtn = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_RATE_MAX), b -> sendAction(ChannelActionC2SPacket.Action.SET_RATE, ChannelActionC2SPacket.RATE_MAX)).bounds(rxF() + 100, topPos + RATE_TOP + 2, 28, 14)));

        // speed (throttle): how many ticks between passes
        speedEdit = new EditBox(this.font, rxF() + 48, topPos + RATE_TOP + RATE_ROW + 3, 30, 12, Component.literal(""));
        speedEdit.setMaxLength(7);
        speedEdit.setBordered(false);
        speedEdit.setTextColor(P2PTheme.TEXT_PRIMARY);
        addRenderableWidget(speedEdit);
        speedDown = stepper("-", b -> stepSpeed(-1), rxF() + 30, topPos + RATE_TOP + RATE_ROW + 2);
        speedUp = stepper("+", b -> stepSpeed(1), rxF() + 82, topPos + RATE_TOP + RATE_ROW + 2);

        int toolbarY = topPos + TOOLBAR_TOP + (TOOLBAR_H - BAND_H) / 2;
        bindBtn = addRenderableWidget(textButton(Lang.t(Lang.UI_BIND_CHANNEL), false,
                b -> { if (selectedChannel >= 0) ModMessages.sendToServer(new SetBindChannelC2SPacket(selectedChannel)); },
                leftPos + W / 2 - 30, toolbarY));
        deleteBtn = addRenderableWidget(textButton(Lang.t(Lang.UI_DELETE_CHANNEL), true,
                b -> sendAction(ChannelActionC2SPacket.Action.DELETE, 0), leftPos + RIGHT_X + RIGHT_W - 66, toolbarY));
        deleteGrpBtn = addRenderableWidget(textButton(Lang.t(Lang.UI_DELETE_GROUP), true, b -> {
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
        }, leftPos + RIGHT_X + RIGHT_W - 66 - 54, toolbarY));

        createCh = addRenderableWidget(textButton(Lang.t(Lang.UI_NEW_CHANNEL), false,
                b -> minecraft.setScreen(new ChannelCreateScreen(this)), lx + 6, toolbarY));
        createGrp = addRenderableWidget(textButton(Lang.t(Lang.UI_NEW_GROUP), false,
                b -> minecraft.setScreen(new NameInputScreen(this, Lang.t(Lang.UI_NEW_GROUP), "", 1, -1, null)), lx + 6, toolbarY));
        layoutToolbar();

        prevNet = addRenderableWidget(new P2PFlatButton(Button.builder(Component.literal("<"), b -> pageNet(-1)).bounds(leftPos + W - 8 - 2 * 16, topPos + (NAV_H - 14) / 2, 14, 14)));
        nextNet = addRenderableWidget(new P2PFlatButton(Button.builder(Component.literal(">"), b -> pageNet(1)).bounds(leftPos + W - 8 - 16, topPos + (NAV_H - 14) / 2, 14, 14)));
        prevNet.setStyle(P2PFlatButton.Style.CIRCLE);
        nextNet.setStyle(P2PFlatButton.Style.CIRCLE);

        ModMessages.sendToServer(new RequestChannelsC2SPacket());
    }

    /** Right column inner content x (8px card padding). */
    private int rxF() { return leftPos + RIGHT_X + P2PTheme.PAD; }

    private P2PFlatButton stepper(String glyph, Button.OnPress action, int x, int y) {
        P2PFlatButton b = new P2PFlatButton(Button.builder(Component.literal(glyph), action).bounds(x, y, 14, 14));
        b.setStyle(P2PFlatButton.Style.STEPPER);
        return addRenderableWidget(b);
    }

    private P2PFlatButton textButton(Component label, boolean danger, Button.OnPress action, int x, int y) {
        P2PFlatButton b = new P2PFlatButton(Button.builder(label, action).bounds(x, y, this.font.width(label) + 12, BAND_H));
        b.setStyle(P2PFlatButton.Style.TEXT);
        b.setDanger(danger);
        return b;
    }

    /**
     * Toolbar text buttons are laid out right where they belong: creation on the left, binding centred,
     * destructive actions right-aligned. Recomputed once because all five labels are static.
     */
    private void layoutToolbar() {
        int y = topPos + TOOLBAR_TOP + (TOOLBAR_H - BAND_H) / 2;
        int gap = 6;
        int x = leftPos + LEFT_X + 6;
        for (P2PFlatButton b : new P2PFlatButton[]{createCh, createGrp}) {
            int w = this.font.width(b.getMessage()) + 12;
            b.setX(x); b.setY(y); b.setWidth(w);
            x += w + gap;
        }
        int right = leftPos + RIGHT_X + RIGHT_W - 6;
        for (P2PFlatButton b : new P2PFlatButton[]{deleteBtn, deleteGrpBtn}) {
            int w = this.font.width(b.getMessage()) + 12;
            right -= w;
            b.setX(right); b.setY(y); b.setWidth(w);
            right -= gap;
        }
        int bw = this.font.width(bindBtn.getMessage()) + 12;
        bindBtn.setX(leftPos + (W - bw) / 2);
        bindBtn.setY(y);
        bindBtn.setWidth(bw);
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
            Channel c = Channel.deserializeNBT((CompoundTag) t, this.minecraft.level.registryAccess());
            if (c.networkId == currentNetId) channels.add(c);
        }
        if (findChannel(selectedChannel) == null) selectedChannel = channels.isEmpty() ? -1 : channels.get(0).id;
        if (selectedGroup >= 0) {
            boolean gOk = false;
            for (Group g : groups) if (g.id == selectedGroup) { gOk = true; break; }
            if (!gOk) selectedGroup = -1;
        }
        // 快照刷新后端点集可能已变（键含坐标/面），旧选区整体失效
        clearEndpointSelection();
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
        // (1.3.1：附上端点实时状态——已失效 / 未加载未知)
        for (DimHit d : dimHits) {
            if (mx >= d.x && mx <= d.x + d.w && my >= d.y && my <= d.y + d.h) {
                Component tip;
                if (d.state == EndpointState.INVALID)
                    tip = Lang.t(Lang.UI_ENDPOINT_INVALID_TIP).append(" · ").append(Lang.t(Lang.HUD_DIMENSION, d.dim));
                else if (d.state == EndpointState.UNKNOWN)
                    tip = Lang.t(Lang.HUD_DIMENSION, d.dim).append(" · ").append(Lang.t(Lang.UI_ENDPOINT_UNLOADED));
                else
                    tip = Lang.t(Lang.HUD_DIMENSION, d.dim);
                gg.renderTooltip(this.font, tip, mx, my);
                break;
            }
        }
        // block icon tooltip: shows the block name at this endpoint's position.
        // 名称取自 ItemStack 显示名（JEI 同款）而不是 Block.getName()——后者的翻译键不含参数，
        // 精妙存储这类「%s箱子」模板名会渲染成字面的 "%s钻石箱子"。
        for (BlockHit b : blockHits) {
            if (mx >= b.x && mx <= b.x + b.w && my >= b.y && my <= b.y + b.h) {
                gg.renderTooltip(this.font, Component.empty()
                        .append(Lang.t(Lang.HUD_BLOCK))
                        .append(b.icon.getHoverName()), mx, my);
                break;
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics gg, int mx, int my, float pt) {
        // no-op: drawPanel() already covers the screen with our own dim + panel
    }

    private void drawPanel(GuiGraphics gg) {
        gg.fill(0, 0, this.width, this.height, P2PTheme.BG_OVERLAY);
        P2PTheme.drawRoundRect(gg, leftPos, topPos, W, H, P2PTheme.R_DIALOG, P2PTheme.BG_GROUP);
        drawNav(gg);

        drawLeft(gg);
        drawRight(gg);
        fitConfigRow();
        // 面板脚注：端点多选 + 复制/粘贴的常驻提示（工具条已满，放面板最底行）
        P2PTheme.drawCentered(gg, this.font, Lang.t(Lang.UI_ENDPOINT_HINT),
                leftPos, topPos + H - 9, W, P2PTheme.TEXT_TERT);
    }

    /** Navigation row: network pill on the left, centred bold title, paging circles on the right. */
    private void drawNav(GuiGraphics gg) {
        int y = topPos + (NAV_H - BAND_H) / 2;
        netNameHitValid = false;
        if (!nets.isEmpty()) {
            String nm = trim(nets.get(netIndex).name, 96);
            int w = this.font.width(nm) + 24;
            P2PTheme.pillBackground(gg, leftPos + LEFT_X, y, w, BAND_H, false, true);
            gg.drawString(this.font, Component.literal(nm), leftPos + LEFT_X + 7, y + 3, P2PTheme.TEXT_PRIMARY, false);
            gg.drawString(this.font, Component.literal("v"), leftPos + LEFT_X + w - 13, y + 3, P2PTheme.TEXT_SECOND, false);
            netNameHitX = leftPos + LEFT_X; netNameHitW = w; netNameHitValid = true;
        }
        P2PTheme.drawCenteredBold(gg, this.font, Lang.t(Lang.UI_TITLE), leftPos, topPos + 6, W, P2PTheme.TEXT_PRIMARY);
    }

    /**
     * Re-flows the 红石/分配/分组/清理空气/区块加载 row to its messages' real widths; the fixed
     * per-button width makes longer (e.g. Chinese) labels spill past the panel edge. Runs after
     * drawRight refreshed the messages and before renderWidgets draws the buttons. The medium chip
     * keeps its own width at the right end of the band.
     */
    private void fitConfigRow() {
        if (redstoneBtn == null || !redstoneBtn.visible) return;
        // 不可见按钮（如按需出现的"清理失效端点"）不参与排版，避免留出空档
        List<P2PFlatButton> rowList = new ArrayList<>();
        for (P2PFlatButton b : new P2PFlatButton[]{redstoneBtn, distBtn, groupBtn, cleanBtn, chunkLoadBtn})
            if (b.visible) rowList.add(b);
        if (rowList.isEmpty()) return;
        P2PFlatButton[] row = rowList.toArray(new P2PFlatButton[0]);
        int rx = leftPos + RIGHT_X;
        int chipW = typeBtn != null && typeBtn.visible ? typeBtn.getWidth() + 4 : 0;
        int gap = 4;
        int padding = 8;
        int[] textWidths = new int[row.length];
        int textTotal = 0;
        for (int i = 0; i < row.length; i++) {
            textWidths[i] = this.font.width(row[i].getMessage());
            textTotal += textWidths[i];
        }
        int budget = RIGHT_W - chipW - gap * (row.length - 1) - row.length * padding;
        float scale = textTotal <= budget ? 1f : (float) budget / textTotal;
        int x = rx;
        for (int i = 0; i < row.length; i++) {
            int text = Math.max(12, Math.round(textWidths[i] * scale));
            if (text < textWidths[i]) row[i].setMessage(Component.literal(trim(row[i].getMessage().getString(), text)));
            row[i].setX(x);
            row[i].setWidth(text + padding + row[i].extraWidth());
            x += row[i].getWidth() + gap;
        }
    }

    // ---------- left: search + group tree + channel list ----------
    private void drawLeft(GuiGraphics gg) {
        int lx = leftPos + LEFT_X;
        int listTop = topPos + FRAME_TOP, listBottom = topPos + FRAME_BOTTOM;
        int listH = listBottom - listTop;

        P2PTheme.searchField(gg, lx, topPos + BAND_TOP, LEFT_W, BAND_H);

        P2PTheme.card(gg, lx, listTop, LEFT_W, listH);

        List<LeftRow> rows = buildLeftRows();
        leftContentH = rows.size() * ROW_H;
        leftScroll = clamp(leftScroll, 0, Math.max(0, leftContentH - listH));
        leftRects.clear();

        gg.enableScissor(lx + 1, listTop + 1, lx + LEFT_W - 1, listBottom);
        int baseY = listTop - leftScroll;
        for (int i = 0; i < rows.size(); i++) {
            int ry = baseY + i * ROW_H;
            if (ry + ROW_H <= listTop) continue;
            if (ry > listBottom) break;
            LeftRow r = rows.get(i);
            if (r.kind == 0) { // group header — selectable tree node
                boolean gSel = r.id == selectedGroup;
                if (gSel) P2PTheme.drawRoundRect(gg, lx + 1, ry, LEFT_W - 2, ROW_H, P2PTheme.R_CARD, P2PTheme.CARD_SEL);
                P2PTheme.drawRoundRect(gg, lx + P2PTheme.PAD, ry + 3, 4, ROW_H - 6, 2, r.color == 0 ? P2PTheme.TEXT_TERT : r.color);
                String caret = openGroups.contains(r.id) ? "v" : ">";
                gg.drawString(this.font, Component.literal(caret + " " + trim(r.label, LEFT_W - 46)),
                        lx + P2PTheme.PAD + 10, ry + 5, P2PTheme.TEXT_PRIMARY, false);
                String cnt = String.valueOf(groupCount(r.id));
                gg.drawString(this.font, Component.literal(cnt), lx + LEFT_W - P2PTheme.PAD - this.font.width(cnt),
                        ry + 5, P2PTheme.TEXT_SECOND, false);
                leftRects.add(new Hit(lx + 1, ry, LEFT_W - 2, ROW_H, 0, r.id));
                gg.fill(lx + P2PTheme.PAD, ry + ROW_H - 1, lx + LEFT_W - P2PTheme.PAD, ry + ROW_H, P2PTheme.SEPARATOR);
            } else { // channel row — child rows are indented with a left tree guide line
                boolean sel = r.id == selectedChannel;
                if (sel) P2PTheme.drawRoundRect(gg, lx + 1, ry, LEFT_W - 2, ROW_H, P2PTheme.R_CARD, P2PTheme.CARD_SEL);
                if (r.child) gg.fill(lx + P2PTheme.PAD + 1, ry + 3, lx + P2PTheme.PAD + 3, ry + ROW_H - 3, P2PTheme.SEPARATOR);
                int ind = r.child ? 24 : P2PTheme.PAD;
                int rowInk = sel ? P2PTheme.TEXT_PRIMARY : P2PTheme.TEXT_SECOND;
                // show the channel name only (no "#id" prefix); trim so the type tag fits inside the card
                int nameMax = LEFT_W - 40 - (r.child ? 16 : 0);
                gg.drawString(this.font, Component.literal(trim(r.label, nameMax)), lx + ind, ry + 5, rowInk, false);
                typeTag(gg, lx + LEFT_W - 8 - 56, ry + 4, r.type);
                leftRects.add(new Hit(lx + 1, ry, LEFT_W - 2, ROW_H, 1, r.id));
                gg.fill(lx + P2PTheme.PAD, ry + ROW_H - 1, lx + LEFT_W - P2PTheme.PAD, ry + ROW_H, P2PTheme.SEPARATOR);
            }
        }
        if (rows.isEmpty()) {
            gg.disableScissor();
            gg.drawString(this.font, Lang.t(Lang.UI_NO_MATCH), lx + P2PTheme.PAD, listTop + 10, P2PTheme.TEXT_SECOND, false);
        } else {
            gg.disableScissor();
        }
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
        int rx = leftPos + RIGHT_X;
        int cx = rxF();
        nameHitValid = false;
        Channel c = findChannel(selectedChannel);
        boolean has = c != null;
        redstoneBtn.visible = distBtn.visible = rateDown.visible = rateUp.visible = speedDown.visible = speedUp.visible
                = rateEdit.visible = speedEdit.visible = groupBtn.visible = bindBtn.visible = deleteBtn.visible
                = maxRateBtn.visible = has;
        // 清理失效端点按钮：仅当前频道存在失效端点时出现（行扫描后再定，见下方 row loop）
        cleanBtn.visible = false;
        typeBtn.visible = has;
        if (deleteGrpBtn != null) deleteGrpBtn.active = has && (selectedGroup >= 0 || (c != null && c.groupId >= 0));
        if (!has) {
            P2PTheme.card(gg, rx, topPos + FRAME_TOP, RIGHT_W, FRAME_BOTTOM - FRAME_TOP);
            gg.drawString(this.font, Lang.t(Lang.UI_NO_CHANNEL), cx, topPos + FRAME_TOP + 24, P2PTheme.TEXT_PRIMARY, false);
            gg.drawString(this.font, Lang.t(Lang.UI_NO_CHANNEL_HINT1), cx, topPos + FRAME_TOP + 44, P2PTheme.TEXT_SECOND, false);
            gg.drawString(this.font, Lang.t(Lang.UI_NO_CHANNEL_HINT2), cx, topPos + FRAME_TOP + 62, P2PTheme.TEXT_SECOND, false);
            return;
        }

        // config reflects state — pill 只显短标签（spec v1.1），当前值进 tooltip，非默认态 TINT 提亮
        redstoneBtn.setMessage(Lang.t(Lang.UI_PILL_REDSTONE));
        redstoneBtn.setTooltip(Tooltip.create(Lang.t(Lang.UI_REDSTONE, redstoneLabel(c.redstoneMode))));
        redstoneBtn.setHighlighted(c.redstoneMode != Channel.RedstoneMode.ALWAYS);
        distBtn.setMessage(Lang.t(Lang.UI_PILL_DIST));
        distBtn.setTooltip(Tooltip.create(Lang.t(Lang.UI_DISTRIBUTION, distLabel(c.distribution))));
        distBtn.setHighlighted(c.distribution != Channel.Distribution.ROUND_ROBIN);
        groupBtn.setMessage(Lang.t(Lang.UI_PILL_GROUP));
        groupBtn.setTooltip(Tooltip.create(Lang.t(Lang.UI_GROUP, groupLabel(c.groupId))));
        groupBtn.setHighlighted(c.groupId >= 0);
        // cleanBtn 是动作钮没有状态，高亮恒为 false；tooltip 说明"清理失效端点"的范围
        cleanBtn.setMessage(Lang.t(Lang.UI_PILL_CLEAN));
        cleanBtn.setTooltip(Tooltip.create(Lang.t(Lang.UI_CLEAN_INVALID)));
        cleanBtn.setHighlighted(false);
        typeBtn.setMessage(typeLabel(c.type));
        typeBtn.setChipColor(typeColor(c.type));
        typeBtn.setWidth(Math.min(this.font.width(typeLabel(c.type)) + 12, 56));
        typeBtn.setX(rx + RIGHT_W - typeBtn.getWidth());
        // chunk loading is a network-level switch shown next to the channel-level buttons
        Network net = null;
        for (Network nn : nets) if (nn.id == c.networkId) { net = nn; break; }
        boolean netLoad = net != null && net.chunkLoad;
        chunkLoadBtn.setMessage(Lang.t(Lang.UI_PILL_CHUNKLOAD));
        chunkLoadBtn.setTooltip(Tooltip.create(Lang.t(netLoad ? Lang.UI_CHUNKLOAD_ON : Lang.UI_CHUNKLOAD_OFF)));
        chunkLoadBtn.setSelected(netLoad);
        chunkLoadBtn.setHighlighted(netLoad);
        // rate / speed editable values (refresh the box text only when not actively typing)
        if (!rateEdit.isFocused() && !rateEdit.getValue().equals(String.valueOf(c.rate))) rateEdit.setValue(String.valueOf(c.rate));
        if (!speedEdit.isFocused() && !speedEdit.getValue().equals(String.valueOf(c.speed))) speedEdit.setValue(String.valueOf(c.speed));

        // per-medium extension: a medium may take over the whole right-hand detail panel
        // (hides the generic rate/throttle controls and the header card, skips the default endpoint table)
        ChannelScreenExtension ext = extensionFor(c);
        if (ext != null && ext.replacesDefaultPanel(c)) {
            rateDown.visible = rateUp.visible = speedDown.visible = speedUp.visible = maxRateBtn.visible = false;
            rateEdit.visible = speedEdit.visible = false;
            memberBtns.clear(); jumpHits.clear(); dimHits.clear(); blockHits.clear(); faceHits.clear();
            ext.drawDetails(detailContext(), gg, c);
            return;
        }

        drawHeaderCard(gg, rx, cx, c);
        drawRateCard(gg, cx, c);

        // endpoint table (fixed-column layout so direction and IN/OUT line up regardless of coordinate width)
        // 表头与列内容一一对齐：维度=色条+短名的块头，坐标=坐标文本起点，面/流向在本列居中
        int tableTop = topPos + TABLE_TOP;
        int rowsTop = tableTop + TABLE_HEAD;
        int tableBottom = topPos + FRAME_BOTTOM;
        P2PTheme.card(gg, rx, tableTop, RIGHT_W, tableBottom - tableTop);
        gg.drawString(this.font, Lang.t(Lang.UI_DIM), cx + COL_DIM, tableTop + 5, P2PTheme.TEXT_SECOND, false);
        gg.drawString(this.font, Lang.t(Lang.UI_POS), cx + COL_POS, tableTop + 5, P2PTheme.TEXT_SECOND, false);
        Component faceHead = Lang.t(Lang.UI_FACE);
        P2PTheme.drawCentered(gg, this.font, faceHead, cx + COL_FACE, tableTop + 5, COL_FACE_W, P2PTheme.TEXT_SECOND);
        Component flowHead = Lang.t(Lang.UI_FLOW);
        P2PTheme.drawCentered(gg, this.font, flowHead, cx + COL_ROLE, tableTop + 5, P2PTheme.BADGE_W, P2PTheme.TEXT_SECOND);
        Component cnt = Lang.t(Lang.UI_MEMBER_COUNT, c.members.size());
        gg.drawString(this.font, cnt, rx + RIGHT_W - P2PTheme.PAD - this.font.width(cnt), tableTop + 5, P2PTheme.TEXT_SECOND, false);
        gg.fill(rx + P2PTheme.PAD, rowsTop, rx + RIGHT_W - P2PTheme.PAD, rowsTop + 1, P2PTheme.SEPARATOR);

        memberBtns.clear();
        jumpHits.clear();
        dimHits.clear();
        blockHits.clear();
        faceHits.clear();
        if (c.members.isEmpty()) {
            gg.drawString(this.font, Lang.t(Lang.UI_NO_ENDPOINT), cx, rowsTop + 12, P2PTheme.TEXT_PRIMARY, false);
            gg.drawString(this.font, Lang.t(Lang.UI_NO_CHANNEL_HINT2), cx, rowsTop + 28, P2PTheme.TEXT_SECOND, false);
            if (ext != null) ext.drawDetails(detailContext(), gg, c);
            return;
        }
        // 失效端点实时统计（同维度已加载但方块为空气）；驱动"清理失效端点"按钮显隐
        invalidCount = 0;
        for (Member m : c.members) if (endpointState(m) == EndpointState.INVALID) invalidCount++;
        cleanBtn.visible = invalidCount > 0;
        int viewport = tableBottom - rowsTop;
        memberContentH = c.members.size() * ROW_H;
        memberScroll = clamp(memberScroll, 0, Math.max(0, memberContentH - viewport));
        int baseY = rowsTop - memberScroll;

        gg.enableScissor(rx + 1, rowsTop + 1, rx + RIGHT_W - 1, tableBottom);
        for (int i = 0; i < c.members.size(); i++) {
            int ry = baseY + i * ROW_H;
            if (ry + ROW_H <= rowsTop) continue;
            if (ry > tableBottom) break;
            drawMemberRow(gg, c.members.get(i), i, ry, cx);
        }
        gg.disableScissor();
        // overlay details (default panel still visible) — non-replacing extensions draw here
        if (ext != null) ext.drawDetails(detailContext(), gg, c);
    }

    /** Header card: {@code #id} in secondary ink, then the (click-to-rename) channel title. */
    private void drawHeaderCard(GuiGraphics gg, int rx, int cx, Channel c) {
        P2PTheme.card(gg, rx, topPos + HEAD_TOP, RIGHT_W, HEAD_H);
        String id = "#" + c.id;
        gg.drawString(this.font, Component.literal(id), cx, topPos + HEAD_TOP + 5, P2PTheme.TEXT_SECOND, false);
        int nameX = cx + this.font.width(id) + 6;
        String nm = trim(c.name == null ? "#" + c.id : c.name, rx + RIGHT_W - P2PTheme.PAD - nameX);
        P2PTheme.drawStringBold(gg, this.font, Component.literal(nm), nameX, topPos + HEAD_TOP + 5, P2PTheme.TEXT_PRIMARY);
        nameHitX = nameX; nameHitY = topPos + HEAD_TOP; nameHitW = this.font.width(nm) + 4; nameHitH = HEAD_H; nameHitValid = true;
    }

    /** Rate + throttle card: two 18px rows, each label / stepper / derived footnote. */
    private void drawRateCard(GuiGraphics gg, int cx, Channel c) {
        P2PTheme.card(gg, leftPos + RIGHT_X, topPos + RATE_TOP, RIGHT_W, RATE_ROW * 2);
        gg.fill(leftPos + RIGHT_X + P2PTheme.PAD, topPos + RATE_TOP + RATE_ROW,
                leftPos + RIGHT_X + RIGHT_W - P2PTheme.PAD, topPos + RATE_TOP + RATE_ROW + 1, P2PTheme.SEPARATOR);

        P2PTheme.stepperField(gg, cx + 46, topPos + RATE_TOP + 2, 34, 14);
        gg.drawString(this.font, Lang.t(Lang.UI_RATE), cx, topPos + RATE_TOP + 5, P2PTheme.TEXT_SECOND, false);
        // 单位与介质名分开画：p2p.unit.<介质id> 没有配键时留空，长介质名因此不再被截断。
        int footX = cx + 132;
        String unit = Lang.unitOf(c.type.id);
        if (!unit.isEmpty()) {
            gg.drawString(this.font, Component.literal(unit), footX, topPos + RATE_TOP + 5, P2PTheme.TEXT_SECOND, false);
            footX += this.font.width(unit) + 4;
        }
        gg.drawString(this.font, Component.literal("~" + formatPerSec(c.rate, c.speed) + "/s"),
                footX, topPos + RATE_TOP + 5, P2PTheme.TEXT_TERT, false);

        P2PTheme.stepperField(gg, cx + 46, topPos + RATE_TOP + RATE_ROW + 2, 34, 14);
        gg.drawString(this.font, Lang.t(Lang.UI_THROTTLE), cx, topPos + RATE_TOP + RATE_ROW + 5, P2PTheme.TEXT_SECOND, false);
        gg.drawString(this.font, Lang.t(Lang.UI_EVERY_TICKS, c.speed), cx + 100,
                topPos + RATE_TOP + RATE_ROW + 5, P2PTheme.TEXT_TERT, false);
    }

    /** 端点行的实时状态（1.3.1，面板直接反映世界现状，不做破坏自动移除）。 */
    private enum EndpointState {
        /** 维度已加载且方块仍在：正常图标。 */
        VALID,
        /** 维度已加载但坐标处是空气：方块已被破坏 → 空槽井 + 整行红字"已失效"。 */
        INVALID,
        /** 跨维度 / 区块未加载：状态未知 → blockId 兜底图标 + "未加载"级提示，不标红。 */
        UNKNOWN
    }

    /**
     * 实时解析端点现状：只在玩家当前维度且区块已加载时才能判定（空气=失效、有方块=有效）；
     * 其余情况（跨维度、本维度区块未加载）一律 UNKNOWN，绝不把"查不到"误判成"已失效"。
     */
    private EndpointState endpointState(Member m) {
        if (this.minecraft.level == null || !this.minecraft.level.dimension().location().equals(m.dim))
            return EndpointState.UNKNOWN;
        if (!this.minecraft.level.hasChunkAt(m.pos)) return EndpointState.UNKNOWN;
        return this.minecraft.level.getBlockState(m.pos).isAir() ? EndpointState.INVALID : EndpointState.VALID;
    }

    private void drawMemberRow(GuiGraphics gg, Member m, int index, int ry, int cx) {
        // 选中判定按 memberKey（多选集合），行内控件的点击语义不受选中影响
        boolean sel = selectedEndpoints.contains(m.key());
        if (sel) P2PTheme.drawRoundRect(gg, cx - P2PTheme.PAD + 1, ry, RIGHT_W - 2, ROW_H, P2PTheme.R_CARD, P2PTheme.CARD_SEL);
        gg.fill(cx, ry + ROW_H - 1, cx + RIGHT_W - P2PTheme.PAD * 2, ry + ROW_H, P2PTheme.SEPARATOR);

        EndpointState st = endpointState(m);
        boolean invalid = st == EndpointState.INVALID;

        // 方块图标三态：有效=实时取（pick-stack + 方块实体 NBT 还原模组变体）；
        // 未知（跨维度/区块未加载）=绑定时记录的 blockId 兜底显示最后已知图标；
        // 已失效=不画旧图标，画空槽井（绝不回退到 blockId 显示已破坏方块的"幽灵图标"）。
        ItemStack icon = endpointIcon(m);
        if (!icon.isEmpty()) {
            gg.renderItem(icon, cx + COL_ICON, ry + 1);
            blockHits.add(new BlockHit(index, cx + COL_ICON, ry, 16, ROW_H, icon));
        } else {
            P2PTheme.slotWell(gg, cx + COL_ICON, ry + 1);
        }

        // 双击定位热区 = 物品图标 + 维度色条那一段（单击=选中，双击=跳转定位，见 mouseClicked）
        jumpHits.add(new JumpHit(index, cx + COL_ICON, ry, COL_DIM_TEXT - COL_ICON, ROW_H));

        // dimension: colour pill + short label (hover shows the full dimension id + 状态提示)
        String dimId = m.dim.toString();
        P2PTheme.dimPill(gg, cx + COL_DIM, ry + 3, ROW_H - 6,
                invalid ? P2PTheme.DANGER : st == EndpointState.UNKNOWN ? P2PTheme.TEXT_TERT : dimColor(dimId));
        Component dimLabel = invalid ? Lang.t(Lang.UI_ENDPOINT_INVALID) : Component.literal(dimShort(dimId));
        gg.drawString(this.font, dimLabel, cx + COL_DIM_TEXT, ry + 5,
                invalid ? P2PTheme.DANGER : st == EndpointState.UNKNOWN ? P2PTheme.TEXT_TERT : P2PTheme.TEXT_PRIMARY, false);
        dimHits.add(new DimHit(dimId, st, cx + COL_DIM, ry + 2,
                COL_DIM_TEXT - COL_DIM + this.font.width(dimLabel) + 2, ROW_H - 4));

        // coordinate (left-aligned at a fixed x; ellipsised if a huge coordinate would overflow)
        String pos = trim(m.pos.getX() + "," + m.pos.getY() + "," + m.pos.getZ(), COL_POS_W);
        int posX = cx + COL_POS;
        gg.drawString(this.font, Component.literal(pos), posX, ry + 5,
                invalid ? P2PTheme.DANGER : P2PTheme.TEXT_PRIMARY, false);

        // face direction: 专属控件热区（点击循环换面 SET_FACE），做成小 pill 以示可点；
        // 失效行不画 pill（换面已无意义），只保留红字展示。
        Component face = faceShort(m.face);
        int faceW = this.font.width(face);
        if (!invalid) {
            P2PTheme.drawPill(gg, cx + COL_FACE - 2, ry + 3, COL_FACE_W + 4, 12, P2PTheme.FIELD_BG);
            gg.drawString(this.font, face, cx + COL_FACE + (COL_FACE_W - faceW) / 2, ry + 5, P2PTheme.TEXT_PRIMARY, false);
        } else {
            gg.drawString(this.font, face, cx + COL_FACE + (COL_FACE_W - faceW) / 2, ry + 5, P2PTheme.DANGER, false);
        }
        // 失效行不注册换面热区（与"不画 pill"一致）：点击落入整行选中，不再发 SET_FACE。
        if (!invalid) faceHits.add(new FaceHit(index, cx + COL_FACE - 2, ry + 3, COL_FACE_W + 4, 12));

        // IN/OUT badge (click to swap role), priority, filter, circular delete
        int bx = cx + COL_ROLE, pX = cx + COL_PRIORITY, fbX = cx + COL_FILTER, dbX = cx + COL_DELETE;
        P2PTheme.roleBadge(gg, bx, ry + 3, m.role == Role.INPUT);
        P2PTheme.drawCentered(gg, this.font, Component.literal(m.role == Role.INPUT ? "IN" : "OUT"),
                bx, ry + 5, P2PTheme.BADGE_W, 0xFFFFFFFF);
        addFaux(gg, pX, ry + 3, COL_PRIORITY_W, P2PTheme.FIELD_BG, "P" + m.priority, P2PTheme.TEXT_PRIMARY);
        addFaux(gg, fbX, ry + 3, COL_FILTER_W, hasFilter(m) ? P2PTheme.CARD_SEL : P2PTheme.FIELD_BG,
                Lang.t(Lang.UI_FILTER).getString(), hasFilter(m) ? P2PTheme.TINT : P2PTheme.TEXT_SECOND);
        P2PTheme.deleteCross(gg, dbX, ry + 2, false);
        memberBtns.add(new MemberBtn(index, 0, fbX, ry + 3, COL_FILTER_W, 12));
        memberBtns.add(new MemberBtn(index, 1, bx, ry + 3, P2PTheme.BADGE_W, 12));
        memberBtns.add(new MemberBtn(index, 2, dbX, ry + 2, P2PTheme.CIRCLE, P2PTheme.CIRCLE));
        memberBtns.add(new MemberBtn(index, 3, pX, ry + 3, COL_PRIORITY_W, 12));
    }

    /**
     * 按端点状态解析图标：VALID=实时取（同维度，pick-stack + BE NBT）；INVALID=空（绝不回退到
     * blockId 显示已破坏方块的旧图标）；UNKNOWN（跨维度/区块未加载）=blockId 兜底显示最后已知图标。
     */
    private ItemStack endpointIcon(Member m) {
        EndpointState st = endpointState(m);
        if (st == EndpointState.INVALID) return ItemStack.EMPTY;
        if (st == EndpointState.VALID) {
            BlockState stt = this.minecraft.level.getBlockState(m.pos);
            Block blk = stt.getBlock();
            if (blk != Blocks.AIR && blk.asItem() != Items.AIR) {
                ItemStack pick = blk.getCloneItemStack(stt,
                        new BlockHitResult(Vec3.atCenterOf(m.pos), m.face, m.pos, false),
                        this.minecraft.level, m.pos, this.minecraft.player);
                if (pick.isEmpty()) pick = new ItemStack(blk.asItem());
                // 把方块实体的 NBT 挂到 pick-stack 上（vanilla BlockItem 同款 "BlockEntityTag"），
                // 模组物品（精妙存储等）读它还原木材/等级变体，图标与名称才会和世界里的实际方块一致。
                BlockEntity be = this.minecraft.level.getBlockEntity(m.pos);
                if (be != null) {
                    CompoundTag beTag = be.saveWithFullMetadata(this.minecraft.level.registryAccess());
                    beTag.remove("id");
                    BlockItem.setBlockEntityData(pick, be.getType(), beTag);
                }
                return pick;
            }
            return ItemStack.EMPTY;
        }
        // UNKNOWN：BuiltInRegistries.BLOCK 是 DefaultedRegistry：查不到返回 AIR 而不是 null
        if (m.blockId != null) {
            Block blk = BuiltInRegistries.BLOCK.get(m.blockId);
            if (blk != Blocks.AIR && blk.asItem() != Items.AIR) return new ItemStack(blk.asItem());
        }
        return ItemStack.EMPTY;
    }

    // ============================================================ input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        ChannelScreenExtension ext = extensionFor(findChannel(selectedChannel));
        if (ext != null && ext.mouseClicked(detailContext(), mx, my, button)) return true;
        if (super.mouseClicked(mx, my, button)) return true;
        int ix = (int) mx, iy = (int) my;
        if (netNameHitValid && iy >= topPos && iy <= topPos + NAV_H && ix >= netNameHitX && ix <= netNameHitX + netNameHitW) {
            minecraft.setScreen(new NetworkScreen());
            return true;
        }
        if (nameHitValid && ix >= nameHitX && ix <= nameHitX + nameHitW && iy >= nameHitY && iy <= nameHitY + nameHitH) {
            Channel c = findChannel(selectedChannel);
            if (c != null) minecraft.setScreen(new NameInputScreen(this, Lang.t(Lang.UI_RENAME), c.name == null ? "" : c.name, 0, c.id, null));
            return true;
        }
        // 双击端点行的物品图标 / 维度色条区 → 跳转到世界方块：画透视红框并关 GUI。
        // 单击该区不再被消费（落到下方整行选中逻辑）：单击=选中、双击=定位，两者共存。
        long now = System.currentTimeMillis();
        for (JumpHit jh : jumpHits) {
            if (ix >= jh.x && ix <= jh.x + jh.w && iy >= jh.y && iy <= jh.y + jh.h) {
                if (jh.index == lastDblIndex && now - lastDblTime < 350) {
                    Channel c = findChannel(selectedChannel);
                    if (c != null && jh.index < c.members.size()) {
                        Member m = c.members.get(jh.index);
                        ClientChannelCache.INSTANCE.addHighlight(m.pos);
                        P2P.LOGGER.info("[p2p] jump-to-block highlight: {}", m.pos);
                        minecraft.setScreen(null);
                        return true;
                    }
                }
                lastDblIndex = jh.index;
                lastDblTime = now;
                break; // 单击：继续走整行选中
            }
        }
        // 面列 = 专属控件：点击循环换面（SET_FACE），命中即消费，不落入整行选中
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
        // 端点行整行（含图标、维度色条、坐标、列间空隙）= 选中语义（1.3.1：真机反馈"有时点不中"，
        // 命中区从列间空隙扩到整行）；表格内最后一行之下 = 清空。
        // 行内专属控件（面列换面、IN/OUT 徽牌、优先级、过滤、×）在上面各自的命中循环里已被消费，
        // 双击定位区单击也会落到这里（单击=选中、双击=定位），选中不会劫持控件点击。
        // 附属面板接管右栏时不画端点表，这里也必须让位（否则会在看不见的行上“盲选”）。
        Channel selCh = findChannel(selectedChannel);
        ChannelScreenExtension selExt = selCh == null ? null : extensionFor(selCh);
        if (selCh != null && (selExt == null || !selExt.replacesDefaultPanel(selCh))) {
            int tRowsTop = topPos + TABLE_TOP + TABLE_HEAD;
            int trx = leftPos + RIGHT_X;
            if (ix >= trx && ix <= trx + RIGHT_W && iy >= tRowsTop && iy <= topPos + FRAME_BOTTOM) {
                int idx = (iy - tRowsTop + memberScroll) / ROW_H;
                if (idx >= 0 && idx < selCh.members.size()) selectEndpoint(selCh, idx);
                else clearEndpointSelection();
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
                    selectedGroup = -1;
                    clearEndpointSelection();  // 换频道后旧频道的选区整体失效
                }
                saveUiState();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        ChannelScreenExtension ext = extensionFor(findChannel(selectedChannel));
        if (ext != null && ext.mouseScrolled(detailContext(), mx, my, scrollX, scrollY)) return true;
        int lx = leftPos + LEFT_X, listTop = topPos + FRAME_TOP, listBottom = topPos + FRAME_BOTTOM;
        int rx = leftPos + RIGHT_X;
        int rowsTop = topPos + TABLE_TOP + TABLE_HEAD;
        if (mx >= lx && mx <= lx + LEFT_W && my >= listTop && my <= listBottom)
            leftScroll = clamp(leftScroll - (int) scrollY * ROW_H, 0, Math.max(0, leftContentH - (listBottom - listTop)));
        else if (mx >= rx && mx <= rx + RIGHT_W && my >= rowsTop && my <= listBottom)
            memberScroll = clamp(memberScroll - (int) scrollY * ROW_H, 0, Math.max(0, memberContentH - (listBottom - rowsTop)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == InputConstants.KEY_ESCAPE) {
            // Esc 先清空端点选区（spec：Esc=清空），没有选区才关闭界面
            if (!selectedEndpoints.isEmpty()) { clearEndpointSelection(); return true; }
            commitRate(); commitSpeed(); minecraft.setScreen(null); return true;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            if (rateEdit.isFocused()) { commitRate(); rateEdit.setFocused(false); return true; }
            if (speedEdit.isFocused()) { commitSpeed(); speedEdit.setFocused(false); return true; }
        }
        // 端点多选快捷键：输入框聚焦时交给输入框（Ctrl+A 全选文本等语义优先）
        boolean typing = (searchBox != null && searchBox.isFocused())
                || rateEdit.isFocused() || speedEdit.isFocused();
        if (!typing && Screen.hasControlDown()) {
            if (key == InputConstants.KEY_C) { copyEndpointConfig(); return true; }
            if (key == InputConstants.KEY_V) { pasteEndpointConfig(); return true; }
            if (key == InputConstants.KEY_A) { selectAllEndpoints(); return true; }
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
        ModMessages.sendToServer(new ChannelActionC2SPacket(selectedChannel, action, null, val, null, false, null, ""));
    }

    private void openGroupSelect() {
        Channel c = findChannel(selectedChannel);
        if (c == null) return;
        minecraft.setScreen(new GroupSelectScreen(this, selectedChannel, c.groupId, groups));
    }

    private void cycleType() {
        Channel c = findChannel(selectedChannel);
        if (c == null) return;
        List<Medium> vals = Mediums.all();
        int idx = vals.indexOf(c.type);
        Medium next = vals.get(Math.floorMod(idx + 1, vals.size()));
        ModMessages.sendToServer(new ChannelActionC2SPacket(c.id, ChannelActionC2SPacket.Action.SET_TYPE, next.id, 0, null, false, null, ""));
    }

    private void pageNet(int dir) {
        if (nets.isEmpty()) return;
        netIndex = Math.max(0, Math.min(nets.size() - 1, netIndex + dir));
        selectedChannel = -1;
        selectedGroup = -1;
        clearEndpointSelection();
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
        ModMessages.sendToServer(new ChannelActionC2SPacket(c.id, ChannelActionC2SPacket.Action.SET_ROLE, null, target, m.key(), false, null, ""));
    }

    /** Send REMOVE_MEMBER to the server and drop the row locally for instant feedback. */
    private void removeMember(Member m) {
        Channel c = findChannel(selectedChannel);
        if (c == null) return;
        ModMessages.sendToServer(new ChannelActionC2SPacket(c.id, ChannelActionC2SPacket.Action.REMOVE_MEMBER, null, 0, m.key(), false, null, ""));
        c.members.remove(m);
        selectedEndpoints.remove(m.key());
        if (m.key().equals(selLastKey)) selLastKey = null;
    }

    // ============================================================ endpoint selection + copy/paste
    private void clearEndpointSelection() {
        selectedEndpoints.clear();
        selAnchor = -1;
        selLastKey = null;
    }

    /**
     * 单击行空白区：普通单击=单选替换；Ctrl+单击=增减多选；Shift+单击=从锚点行到当前行的
     * 范围选中（配合 Ctrl 则追加范围）。行内控件的点击不会进入这里（已被控件命中消费）。
     */
    private void selectEndpoint(Channel c, int idx) {
        String key = c.members.get(idx).key();
        boolean ctrl = Screen.hasControlDown();
        boolean shift = Screen.hasShiftDown();
        if (shift && selAnchor >= 0 && selAnchor < c.members.size()) {
            if (!ctrl) selectedEndpoints.clear();
            for (int i = Math.min(selAnchor, idx); i <= Math.max(selAnchor, idx); i++)
                selectedEndpoints.add(c.members.get(i).key());
        } else if (ctrl) {
            if (!selectedEndpoints.remove(key)) selectedEndpoints.add(key);
        } else {
            selectedEndpoints.clear();
            selectedEndpoints.add(key);
        }
        selAnchor = idx;
        selLastKey = key;
    }

    /** Ctrl+A：全选当前频道的所有端点。 */
    private void selectAllEndpoints() {
        Channel c = findChannel(selectedChannel);
        if (c == null || c.members.isEmpty()) return;
        for (Member m : c.members) selectedEndpoints.add(m.key());
        selAnchor = -1;
        if (selLastKey == null) selLastKey = c.members.get(0).key();
    }

    /**
     * Ctrl+C：复制“最后选中”（多选时即最后一次点选；否则唯一选中）端点的配置 =
     * 完整过滤 NBT（含 addon 段，原样序列化）+ 优先级 + 流向。剪贴板是 static 的，
     * 允许在频道 A 复制、打开频道 B 再粘贴。
     */
    private void copyEndpointConfig() {
        Channel c = findChannel(selectedChannel);
        if (c == null || selectedEndpoints.isEmpty()) return;
        Member src = null;
        if (selLastKey != null)
            for (Member m : c.members) if (m.key().equals(selLastKey)) { src = m; break; }
        if (src == null)
            for (Member m : c.members) if (selectedEndpoints.contains(m.key())) { src = m; break; }
        if (src == null) { clearEndpointSelection(); return; }
        Filter f = src.role == Role.INPUT ? src.extractFilter : src.insertFilter;
        clipFilterTag = f == null ? null : f.serializeNBT(this.minecraft.level.registryAccess());
        clipPriority = src.priority;
        clipInput = src.role == Role.INPUT;
        clipValid = true;
    }

    /** 剪贴板是否有内容可粘贴。 */
    private static boolean clipValid = false;

    /**
     * Ctrl+V：把剪贴板配置应用到当前频道的所有选中端点。逐端点复用既有
     * {@link ChannelActionC2SPacket}：SET_ROLE → SET_FILTER → SET_PRIORITY。
     * 流向先落位、过滤器随后按「粘贴之后的目标方向」（extract 侧以目标为准）放进对应一侧，
     * 这样来源 IN / 目标 OUT 的跨方向粘贴，过滤器最终仍在目标实际使用的那一侧生效。
     * 目标全部来自当前频道成员，天然满足「一次粘贴只作用于同一频道」。
     */
    private void pasteEndpointConfig() {
        Channel c = findChannel(selectedChannel);
        if (c == null || selectedEndpoints.isEmpty() || !clipValid) return;
        int roleOrd = clipInput ? Role.INPUT.ordinal() : Role.OUTPUT.ordinal();
        for (Member m : c.members) {
            if (!selectedEndpoints.contains(m.key())) continue;
            ModMessages.sendToServer(new ChannelActionC2SPacket(c.id,
                    ChannelActionC2SPacket.Action.SET_ROLE, null, roleOrd, m.key(), false, null, ""));
            // 无过滤器的来源也要发（服务端把 null tag 解释为清空），
            // 否则来源没配过滤时“粘贴配置”会静默丢掉“清除目标旧过滤”这半边语义。
            ModMessages.sendToServer(new ChannelActionC2SPacket(c.id,
                    ChannelActionC2SPacket.Action.SET_FILTER, null, 0, m.key(), clipInput,
                    clipFilterTag == null ? null : clipFilterTag.copy(), ""));
            ModMessages.sendToServer(new ChannelActionC2SPacket(c.id,
                    ChannelActionC2SPacket.Action.SET_PRIORITY, null, clipPriority, m.key(), false, null, ""));
        }
    }

    // ============================================================ helpers
    /** The per-medium detail-panel extension registered for this channel (null = vanilla layout). */
    @Nullable
    private static ChannelScreenExtension extensionFor(@Nullable Channel c) {
        if (c == null || c.type == null) return null;
        return ChannelScreenExtensions.get(c.type.id);
    }

    /** Fresh facade over the current panel geometry — rebuilt per callback so window resizes are honoured. */
    private DetailContext detailContext() {
        return new DetailContext(leftPos, topPos, W, H, leftPos + RIGHT_X, RIGHT_W,
                this.font, this.minecraft, pkt -> ModMessages.sendToServer(pkt));
    }

    /** In-row faux control (priority / filter): rounded FIELD plate + centred label, hit tested in mouseClicked. */
    private void addFaux(GuiGraphics gg, int x, int y, int w, int bg, String label, int ink) {
        P2PTheme.drawPill(gg, x, y, w, 12, bg);
        P2PTheme.drawCentered(gg, this.font, Component.literal(label), x, y + 2, w, ink);
    }

    private static Component distLabel(Channel.Distribution d) {
        return switch (d) {
            case PRIORITY -> Lang.t(Lang.UI_DIST_PRIORITY);
            case NEAREST -> Lang.t(Lang.UI_DIST_NEAREST);
            case FARTHEST -> Lang.t(Lang.UI_DIST_FARTHEST);
            case SPLIT_EVENLY -> Lang.t(Lang.UI_DIST_SPLIT);
            default -> Lang.t(Lang.UI_DIST_ROUND);
        };
    }

    private static Component redstoneLabel(Channel.RedstoneMode m) {
        return switch (m) {
            case HIGH -> Lang.t(Lang.UI_RED_HIGH);
            case LOW -> Lang.t(Lang.UI_RED_LOW);
            case NEVER -> Lang.t(Lang.UI_RED_NEVER);
            default -> Lang.t(Lang.UI_RED_IGNORE);
        };
    }

    private Component groupLabel(int gid) {
        if (gid < 0) return Lang.t(Lang.UI_GROUP_NONE);
        for (Group g : groups) if (g.id == gid) return Component.literal(g.name);
        return Lang.t(Lang.UI_GROUP_NONE);
    }

    private boolean hasFilter(Member m) { return (m.extractFilter != null) || (m.insertFilter != null); }

    private boolean matchCh(Channel c, String q) {
        if (q.isEmpty()) return true;
        if (c.name != null && c.name.toLowerCase().contains(q)) return true;
        if (String.valueOf(c.id).contains(q)) return true;
        if (typeLabel(c.type).getString().contains(q) || c.type.id.toLowerCase().contains(q)) return true;
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

    private static int typeColor(Medium t) {
        return t.argbColor;
    }

    private static Component typeLabel(Medium t) {
        return t.displayName();
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
        ModMessages.sendToServer(new ChannelActionC2SPacket(c.id, ChannelActionC2SPacket.Action.SET_FACE,
                null, next.get3DDataValue(), m.key(), false, null, ""));
        int i = c.members.indexOf(m);
        if (i >= 0) c.members.set(i, m.withFace(next));
    }

    /** Open a number prompt so the player can type any routing priority (higher = preferred first). */
    private void cyclePriority(Member m) {
        Channel c = findChannel(selectedChannel);
        if (c == null) return;
        minecraft.setScreen(new NameInputScreen(this, Lang.t(Lang.UI_SET_PRIORITY), String.valueOf(m.priority), 2, c.id, m.key()));
    }

    private static int dimColor(String dim) {
        return P2PTheme.dimColor(dim);
    }

    /** Two-character dimension label that fits the endpoint row; the full id is available as a tooltip. */
    private static String dimShort(String dim) {
        if (dim.equals("minecraft:overworld")) return Lang.t(Lang.UI_DIM_OVERWORLD).getString();
        if (dim.equals("minecraft:the_nether")) return Lang.t(Lang.UI_DIM_NETHER).getString();
        if (dim.equals("minecraft:the_end")) return Lang.t(Lang.UI_DIM_END).getString();
        int colon = dim.indexOf(':');
        String ns = colon > 0 ? dim.substring(0, colon) : dim;
        if (ns.equals("minecraft")) {
            String path = colon > 0 ? dim.substring(colon + 1) : dim;
            ns = path;
        }
        return ns.length() >= 2 ? ns.substring(0, 2).toUpperCase() : ns.toUpperCase();
    }

    private static Component faceShort(net.minecraft.core.Direction f) {
        return switch (f) {
            case UP -> Lang.t(Lang.UI_FACE_UP); case DOWN -> Lang.t(Lang.UI_FACE_DOWN); case NORTH -> Lang.t(Lang.UI_FACE_NORTH); case SOUTH -> Lang.t(Lang.UI_FACE_SOUTH); case EAST -> Lang.t(Lang.UI_FACE_EAST); case WEST -> Lang.t(Lang.UI_FACE_WEST);
        };
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private String trim(String s, int maxPx) {
        if (s == null) return "";
        if (this.font.width(s) <= maxPx) return s;
        while (this.font.width(s + "…") > maxPx && s.length() > 1) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /** Medium tag: right-aligned chip that shrinks to the label width (capped), long names ellipsise. */
    private void typeTag(GuiGraphics gg, int bx, int by, @Nullable Medium t) {
        if (t == null) return;
        Component label = typeLabel(t);
        int textWidth = this.font.width(label);
        int tagWidth = Math.min(textWidth + 8, 56);
        int x = bx + 56 - tagWidth;
        P2PTheme.chip(gg, x, by, tagWidth, 11, typeColor(t));
        Component shown = textWidth + 8 > tagWidth ? Component.literal(trim(label.getString(), tagWidth - 8)) : label;
        gg.drawString(this.font, shown, x + (tagWidth - this.font.width(shown)) / 2, by + 2, 0xFFFFFFFF, false);
    }

    private record LeftRow(int kind, int id, String label, Medium type, int color, boolean child) {}
    private record Hit(int x, int y, int w, int h, int kind, int id) {}
    private record MemberBtn(int index, int kind, int x, int y, int w, int h) {}
    private record JumpHit(int index, int x, int y, int w, int h) {}
    private record DimHit(String dim, EndpointState state, int x, int y, int w, int h) {}
    private record FaceHit(int index, int x, int y, int w, int h) {}
    private record BlockHit(int index, int x, int y, int w, int h, ItemStack icon) {}
}
