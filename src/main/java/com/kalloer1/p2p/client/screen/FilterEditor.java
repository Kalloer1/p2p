package com.kalloer1.p2p.client.screen;

import com.kalloer1.p2p.Lang;
import com.kalloer1.p2p.api.FilterPageContext;
import com.kalloer1.p2p.api.FilterPageExtension;
import com.kalloer1.p2p.api.FilterPageExtensions;
import com.kalloer1.p2p.medium.Medium;
import com.kalloer1.p2p.medium.Mediums;
import com.kalloer1.p2p.channel.Filter;
import com.kalloer1.p2p.channel.Member;
import com.kalloer1.p2p.channel.Role;
import com.kalloer1.p2p.network.ChannelActionC2SPacket;
import com.kalloer1.p2p.network.ModMessages;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Filter editor for one endpoint (member) of a channel.
 *
 * 1.2.0 换皮不换骨：潜影盒纹理与 bevel 全部退役，改为 iOS 遮罩 + 圆角 6 白卡（规格 §4）。
 * 结构不变：这仍是一个真正的 {@link AbstractContainerScreen}，
 *  - 27 个标记槽（9x3）与玩家背包/快捷栏都是真正的 {@link Slot}，所以原版负责物品绘制、
 *    悬停高亮与物品 tooltip；只有 ITEM 页激活，TAG / NBT 页所有槽位置灰。
 *  - 因为它是 AbstractContainerScreen，JEI 认得 GUI 边界并在两侧显示物品列表；拖入标记网格由
 *    本地 JEI 插件（附属）提供，p2p 本体不编译依赖 JEI API。
 *  - 菜单是纯客户端的（无 MenuType、从不注册、从不同步），每个槽只读且 {@link #slotClicked}
 *    被完全覆写，因此永远不会产生容器包；过滤器只在按「完成」时以一条 SET_FILTER 提交。
 *
 * 三个页面各自成立：ITEM = 9x3 标记网格 + 白/黑名单 + 严格 NBT 开关；TAG = 可滚动标签列表；
 * NBT = 多行 NBT/正则编辑器。
 */
public class FilterEditor extends AbstractContainerScreen<FilterEditor.FilterMenu> {

    /** 面板尺寸（规格 §4：184x264，原 176x250）。 */
    static final int SLOT = 18, GRID_COLS = 9, GRID_ROWS = 3;

    // 内容卡（segmented / 内容底板 / 模式行）统一内边距 8，宽 168
    private static final int CARD_X = 8, CARD_W = 168;
    // 导航行 y=8..28：取消 · 居中标题 · 完成
    private static final int NAV_Y = 8, NAV_H = 20;
    // segmented 页签 y=32..50，三段等宽
    private static final int SEG_Y = 32, SEG_H = 18, SEG_CELL = CARD_W / 3;
    // 内容底板 y=54..128（标记网格 / 标签列表 / NBT 编辑器共用）
    private static final int CONTENT_Y = 54, CONTENT_BOTTOM = 128;
    // 控件行 y=132（NBT 开关 / 标签输入 + 加标签），模式行 y=150（白名单 / 黑名单）
    private static final int ROW_CTL = 132, ROW_MODE = 150, CTL_H = 14;
    private static final int INV_LABEL_Y = 166;

    /** 标记网格起点（规格 §4：9x3、槽距 18、起点 x=11），在底板内垂直居中。 */
    static final int GRID_X = 11, GRID_Y = 65;
    /** 玩家背包沿用原版布局，只把槽位井换成主题画法。 */
    static final int INV_X = 12, INV_Y = 178, HOTBAR_Y = 236;

    // 标签列表 / NBT 编辑器几何（相对 topPos）
    private static final int TAG_X = 11, TAG_Y = CONTENT_Y + 1, TAG_W = 158,
            TAG_LIST_H = CONTENT_BOTTOM - TAG_Y, TAG_ROW_H = P2PTheme.ROW_H;
    private static final int NBT_X = 11, NBT_Y = CONTENT_Y + 1, NBT_W = 158, NBT_H = CONTENT_BOTTOM - NBT_Y;

    private final Screen parent;
    private final int channelId;
    private final Medium chType;
    private final Member member;
    private final boolean extract;

    private Filter.Mode mode = Filter.Mode.WHITELIST;
    private boolean matchNBT = false;
    private final List<ItemStack> itemTpl = new ArrayList<>();
    private final List<FluidStack> fluidTpl = new ArrayList<>();
    private final List<ResourceLocation> itemTags = new ArrayList<>();
    private String nbtRegex = "";

    /** Per-medium filter page hooked in via {@link FilterPageExtensions} (null when none registered). */
    @Nullable
    private final FilterPageExtension pageExt;
    private final FilterPageContext pageCtx;

    private EditBox tagBox;
    private MultilineEdit nbtEdit;
    private final List<TagHit> tagHits = new ArrayList<>();
    private int tagScroll = 0;   // scroll offset for the tag list (vertical)

    /** 过滤编辑器三个标签页：物品模板 / 标签 / NBT。点击 segmented 段切换。 */
    private enum Page { ITEM, TAG, NBT }
    private Page page = Page.ITEM;
    private P2PFlatButton btnItem, btnTag, btnNbt;   // segmented 三段
    private P2PFlatButton modeBtn, nbtBtn, addTagBtn; // 各页控件（按页显隐）

    public FilterEditor(Screen parent, int channelId, Medium type, Member member) {
        super(new FilterMenu(Minecraft.getInstance().player.getInventory(), type),
                Minecraft.getInstance().player.getInventory(),
                Lang.t(Lang.UI_FILTER_TITLE));
        this.parent = parent;
        this.channelId = channelId;
        this.chType = type;
        this.member = member;
        this.extract = member.role == Role.INPUT;
        this.imageWidth = 184;
        this.imageHeight = 264;
        this.inventoryLabelY = INV_LABEL_Y;
        this.titleLabelX = 8;
        this.titleLabelY = NAV_Y + (NAV_H - 8) / 2;

        Filter existing = extract ? member.extractFilter : member.insertFilter;
        if (existing != null) {
            this.mode = existing.getMode();
            this.matchNBT = existing.isMatchNBT();
            for (ItemStack s : existing.getItemTemplates()) itemTpl.add(s.copy());
            for (FluidStack f : existing.getFluidTemplates()) fluidTpl.add(f.copy());
            itemTags.addAll(existing.getItemTags());
            this.nbtRegex = existing.getNbtRegex();
        }
        this.pageExt = FilterPageExtensions.get(type == null ? null : type.id);
        this.pageCtx = new FilterPageContext(this, channelId, member, extract);
        if (pageExt != null) pageExt.onOpen(pageCtx, existing);
    }

    public Medium getType() { return chType; }

    /** Register a widget on this screen (used by {@link com.kalloer1.p2p.api.FilterPageExtension}). */
    public <T extends GuiEventListener & Renderable & NarratableEntry> T addScreenWidget(T widget) {
        return addRenderableWidget(widget);
    }

    /** The 27-slot ghost container rendered in the marker grid (used by filter page extensions). */
    public SimpleContainer ghostContainer() { return menu.ghost; }

    /** Screen-space rectangle of the marker grid — JEI uses it as the ghost-drop target. */
    public Rect2i getMarkerGridArea() {
        return new Rect2i(leftPos + GRID_X, topPos + GRID_Y, GRID_COLS * SLOT, GRID_ROWS * SLOT);
    }

    /** 网格槽距（18）。槽位井为 16x16 且起点即槽位原点，附属色块按此对齐。 */
    public int getMarkerPitch() { return SLOT; }

    /**
     * 「模式」行（白名单/黑名单）的屏幕矩形。附属过滤页的模式钮与本体同一行、同一画法，
     * 因此取本体常量而不是自己写死偏移。
     */
    public Rect2i getModeRowArea() {
        return new Rect2i(leftPos + CARD_X, topPos + ROW_MODE, CARD_W, CTL_H);
    }

    /** JEI ghost-drag may only drop an item onto the grid while the ITEM page is active. */
    public boolean isItemPage() { return page == Page.ITEM; }

    /** Called by the JEI ghost handler and by clicking an inventory slot. */
    public void addMarker(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        if (chType == Mediums.ITEM) {
            if (itemTpl.size() >= GRID_COLS * GRID_ROWS) return;
            for (ItemStack s : itemTpl) if (ItemStack.isSameItem(s, stack)) return; // no duplicates
            ItemStack copy = stack.copy();
            copy.setCount(1);
            itemTpl.add(copy);
            syncGhost();
        } else if (chType == Mediums.FLUID) {
            // 流体频道里丢进来的物品按容器处理：能倒出流体就当作流体模板
            addFluidMarker(FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY));
        }
    }

    /**
     * 直接加一条流体模板。JEI 的流体条目交过来的是 FluidStack 本身（不是装满的桶），
     * 所以流体拖拽必须走这里，不能再绕 addMarker(ItemStack)。
     */
    public void addFluidMarker(FluidStack fs) {
        if (fs == null || fs.isEmpty()) return;
        if (fluidTpl.size() >= GRID_COLS * GRID_ROWS) return;
        for (FluidStack f : fluidTpl) if (f.getFluid() == fs.getFluid()) return;   // no duplicates
        fluidTpl.add(new FluidStack(fs.getFluid(), 1000));
        syncGhost();
    }

    /** Mirror the template lists into the ghost container so vanilla renders them in the marker slots. */
    public void syncGhost() {
        for (int i = 0; i < GRID_COLS * GRID_ROWS; i++) {
            ItemStack show = ItemStack.EMPTY;
            if (chType == Mediums.ITEM && i < itemTpl.size()) {
                show = itemTpl.get(i);
            } else if (chType == Mediums.FLUID && i < fluidTpl.size()) {
                ItemStack bucket = FluidUtil.getFilledBucket(fluidTpl.get(i));
                if (!bucket.isEmpty()) show = bucket;
            }
            this.menu.ghost.setItem(i, show);
        }
    }

    @Override
    protected void init() {
        super.init();   // computes leftPos / topPos
        int x = leftPos, y = topPos;
        syncGhost();

        // 导航行：取消（左）· 完成（右），均为无边框文本钮（规格 §3）
        String cancelLabel = Lang.t(Lang.UI_CANCEL).getString();
        String doneLabel = Lang.t(Lang.UI_DONE).getString();
        int bw = Math.max(this.font.width(cancelLabel), this.font.width(doneLabel)) + 8;
        addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_CANCEL), b -> onClose())
                .bounds(x + 4, y + NAV_Y + (NAV_H - CTL_H) / 2, bw, CTL_H)).setStyle(P2PFlatButton.Style.TEXT));
        addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_DONE), b -> finish())
                .bounds(x + imageWidth - 4 - bw, y + NAV_Y + (NAV_H - CTL_H) / 2, bw, CTL_H))
                .setStyle(P2PFlatButton.Style.TEXT));

        // segmented 页签：物品 / 标签 / NBT。说明文字全部放进 tooltip，不常驻占用界面。
        btnItem = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_TAB_ITEM), b -> setPage(Page.ITEM))
                .bounds(x + CARD_X, y + SEG_Y, SEG_CELL, SEG_H)).setStyle(P2PFlatButton.Style.SEGMENT));
        btnTag  = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_TAB_TAG), b -> setPage(Page.TAG))
                .bounds(x + CARD_X + SEG_CELL, y + SEG_Y, SEG_CELL, SEG_H)).setStyle(P2PFlatButton.Style.SEGMENT));
        btnNbt  = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_TAB_NBT), b -> setPage(Page.NBT))
                .bounds(x + CARD_X + SEG_CELL * 2, y + SEG_Y, SEG_CELL, SEG_H)).setStyle(P2PFlatButton.Style.SEGMENT));
        btnItem.setTooltip(Tooltip.create(Lang.t(chType == Mediums.FLUID ? Lang.UI_FLUID_HINT : Lang.UI_ITEM_HINT)));
        btnTag.setTooltip(Tooltip.create(Lang.t(Lang.UI_TAG_TIP)));
        btnNbt.setTooltip(Tooltip.create(Lang.t(Lang.UI_NBT_REGEX_HINT)));

        if (chType == Mediums.ITEM) {
            int ctlY = y + ROW_CTL;
            // 白/黑名单（同时作用于物品与标签）— ITEM / TAG 页显示
            modeBtn = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_MODE, modeLabel()), b -> {
                mode = (mode == Filter.Mode.WHITELIST ? Filter.Mode.BLACKLIST : Filter.Mode.WHITELIST);
                b.setMessage(Lang.t(Lang.UI_MODE, modeLabel()));
            }).bounds(x + CARD_X, y + ROW_MODE, CARD_W, CTL_H)));

            // 严格 NBT 开关 — ITEM / NBT 页显示，右对齐（规格 §3：iOS toggle）
            nbtBtn = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_NBT_SWITCH, nbtLabel()), b -> {
                matchNBT = !matchNBT;
                b.setMessage(Lang.t(Lang.UI_NBT_SWITCH, nbtLabel()));
                ((P2PFlatButton) b).setSelected(matchNBT);
            }).bounds(x + CARD_X + CARD_W - 108, ctlY, 108, CTL_H)).setStyle(P2PFlatButton.Style.TOGGLE));
            nbtBtn.setSelected(matchNBT);

            // TAG 页：标签输入框 + 加标签钮（只在 TAG 页显示）
            tagBox = new EditBox(this.font, x + CARD_X + 4, ctlY + 1, 100, 12, Lang.t(Lang.UI_TAG_HINT));
            tagBox.setBordered(false);
            tagBox.setTextColor(P2PTheme.TEXT_PRIMARY);
            tagBox.setMaxLength(64);
            tagBox.setHint(Lang.t(Lang.UI_TAG_PLACEHOLDER));
            addRenderableWidget(tagBox);
            addTagBtn = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_ADD_TAG), b -> addTagFromBox())
                    .bounds(x + CARD_X + 108, ctlY, 60, CTL_H)));

            // NBT 页：多行编辑器（只在 NBT 页绘制）
            nbtEdit = new MultilineEdit(this.font, x + NBT_X, y + NBT_Y, NBT_W, NBT_H);
            nbtEdit.setValue(nbtRegex);
        } else {
            modeBtn = null; nbtBtn = null; tagBox = null; addTagBtn = null; nbtEdit = null;
        }
        applyPage();
        if (pageExt != null) pageExt.onInit(pageCtx);   // resize-safe: re-registers extension widgets
    }

    /** 切换到指定标签页，并刷新各控件的可见性。 */
    private void setPage(Page p) {
        this.page = p;
        applyPage();
    }

    private void applyPage() {
        boolean itemPage = (page == Page.ITEM);
        if (modeBtn != null) modeBtn.visible = (page == Page.ITEM || page == Page.TAG);
        if (nbtBtn != null) nbtBtn.visible = (page == Page.ITEM || page == Page.NBT);
        if (tagBox != null) {
            tagBox.visible = (page == Page.TAG);
            if (!tagBox.visible) tagBox.setFocused(false);   // otherwise keyPressed keeps swallowing keys off the page
        }
        if (addTagBtn != null) addTagBtn.visible = (page == Page.TAG);
        if (nbtEdit != null && page != Page.NBT) nbtEdit.focused = false;
        if (page != Page.TAG) tagHits.clear();               // stale rows must not eat clicks on other pages
        // deactivate every slot off the ITEM page so the grid/inventory never renders or captures clicks
        setSlotsActive(itemPage && chType != Mediums.ENERGY, itemPage);
        // 选中态由 segmented 自己画（白底 + TINT 字），不再用 "▶ " 前缀
        btnItem.setSelected(page == Page.ITEM);
        btnTag.setSelected(page == Page.TAG);
        btnNbt.setSelected(page == Page.NBT);
    }

    private void setSlotsActive(boolean markerActive, boolean invActive) {
        for (Slot s : menu.slots) ((ReadOnlySlot) s).setActive(s.container == menu.ghost ? markerActive : invActive);
    }

    private Component modeLabel() { return mode == Filter.Mode.WHITELIST ? Lang.t(Lang.UI_MODE_WHITELIST) : Lang.t(Lang.UI_MODE_BLACKLIST); }
    private Component nbtLabel() { return matchNBT ? Lang.t(Lang.UI_NBT_STRICT) : Lang.t(Lang.UI_NBT_IGNORE); }

    private void addTagFromBox() {
        String v = tagBox.getValue().trim();
        if (v.startsWith("#")) v = v.substring(1);
        if (v.isEmpty()) return;
        ResourceLocation id = ResourceLocation.tryParse(v);
        if (id != null && !itemTags.contains(id)) {
            itemTags.add(id);
            tagBox.setValue("");
        }
    }

    private void finish() {
        if (pageExt != null && pageExt.finish(pageCtx)) return;   // extension committed and closed itself
        if (nbtEdit != null) nbtRegex = nbtEdit.getValue().trim();
        Filter f;
        if (chType == Mediums.ENERGY) f = Filter.energyPass();
        else if (chType == Mediums.ITEM) f = Filter.itemFilter(mode, matchNBT, itemTpl, itemTags, nbtRegex);
        else f = Filter.fluidFilter(mode, fluidTpl);
        CompoundTag tag = f.serializeNBT(minecraft.level.registryAccess());
        ModMessages.sendToServer(new ChannelActionC2SPacket(channelId,
                ChannelActionC2SPacket.Action.SET_FILTER, null, 0, member.key(), extract, tag, ""));
        minecraft.setScreen(parent);
    }

    // ============================================================ rendering
    /** 遮罩 + 白卡：不要原版的模糊/渐变背景。 */
    @Override
    public void renderBackground(GuiGraphics gg, int mx, int my, float pt) {
        gg.fill(0, 0, this.width, this.height, P2PTheme.BG_OVERLAY);
        this.renderBg(gg, pt, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics gg, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        P2PTheme.drawRoundRect(gg, x, y, imageWidth, imageHeight, P2PTheme.R_DIALOG, P2PTheme.CARD);
        // segmented 轨道（页签底）在 init 的按钮之下，先画轨道再画内容底板
        P2PTheme.segmentedTrack(gg, x + CARD_X, y + SEG_Y, CARD_W, SEG_H);
        P2PTheme.plate(gg, x + CARD_X, y + CONTENT_Y, CARD_W, CONTENT_BOTTOM - CONTENT_Y);
        if (page == Page.TAG && tagBox != null && tagBox.visible)
            P2PTheme.field(gg, x + CARD_X, y + ROW_CTL, 108, CTL_H);
        // 槽位井：只给激活中的槽画（TAG/NBT 页所有槽置灰，因此不会泄漏到别的页）
        for (Slot s : menu.slots) if (s.isActive()) P2PTheme.slotWell(gg, x + s.x, y + s.y);
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        super.render(gg, mx, my, pt);
        if (page == Page.TAG) drawTagPage(gg);
        else if (page == Page.NBT) drawNbtPage(gg);
        if (pageExt != null) pageExt.renderOverlay(pageCtx, gg, mx, my);
        // 控件 tooltip 由 super.render 负责，这里没有额外浮层
    }

    @Override
    protected void renderLabels(GuiGraphics gg, int mx, int my) {
        // 坐标已平移 leftPos/topPos，这里全部用面板内坐标
        P2PTheme.drawStringBold(gg, this.font, this.title, (imageWidth - this.font.width(this.title)) / 2,
                NAV_Y + (NAV_H - 8) / 2, P2PTheme.TEXT_PRIMARY);
        // 玩家背包只在 ITEM 页存在 — 别留下孤立的「物品栏」标题
        if (page == Page.ITEM && chType != Mediums.ENERGY)
            gg.drawString(this.font, this.playerInventoryTitle, CARD_X, INV_LABEL_Y, P2PTheme.TEXT_SECOND, false);
        if (chType == Mediums.ENERGY && page == Page.ITEM)
            P2PTheme.drawCentered(gg, this.font, Lang.t(Lang.UI_ENERGY_NOTE), CARD_X,
                    CONTENT_Y + (CONTENT_BOTTOM - CONTENT_Y - 8) / 2, CARD_W, P2PTheme.TEXT_SECOND);
        // 「拖入网格 / 点击格子删除」不再常驻：它在 物品 页签的 tooltip 里
    }

    /** TAG page: a scrollable vertical tag list (many rows) + the add box lives in the control row. */
    private void drawTagPage(GuiGraphics gg) {
        if (chType != Mediums.ITEM) { drawTypeOnlyNote(gg); return; }
        int x0 = leftPos + TAG_X, y0 = topPos + TAG_Y;
        int visible = TAG_LIST_H / TAG_ROW_H;
        tagHits.clear();
        int maxScroll = Math.max(0, itemTags.size() - visible);
        if (tagScroll > maxScroll) tagScroll = maxScroll;
        if (tagScroll < 0) tagScroll = 0;
        for (int i = tagScroll; i < itemTags.size() && i - tagScroll < visible; i++) {
            int ry = y0 + (i - tagScroll) * TAG_ROW_H;
            String s = "#" + itemTags.get(i);
            P2PTheme.drawRoundRect(gg, x0, ry, TAG_W, TAG_ROW_H - 1, P2PTheme.R_SLOT, P2PTheme.CARD);
            P2PTheme.deleteCross(gg, x0 + TAG_W - P2PTheme.CIRCLE - 1, ry + 1, false);
            gg.drawString(this.font, Component.literal(trim(s, TAG_W - P2PTheme.CIRCLE - 8)),
                    x0 + 4, ry + 5, P2PTheme.TEXT_PRIMARY, false);
            tagHits.add(new TagHit(i, x0, ry, TAG_W, TAG_ROW_H - 1));
        }
        if (itemTags.isEmpty())
            P2PTheme.drawCentered(gg, this.font, Lang.t(Lang.UI_TAG_EMPTY), x0, y0 + TAG_LIST_H / 2 - 4, TAG_W, P2PTheme.TEXT_TERT);
        if (maxScroll > 0) drawScrollbar(gg, x0 + TAG_W + 1, y0, TAG_LIST_H, visible, itemTags.size(), tagScroll, maxScroll);
    }

    /** NBT page: the large multi-line editor. The strict-NBT toggle lives in the control row. */
    private void drawNbtPage(GuiGraphics gg) {
        if (chType != Mediums.ITEM) { drawTypeOnlyNote(gg); return; }
        if (nbtEdit != null) nbtEdit.render(gg);   // 正则示例见 NBT 页签 tooltip，不再常驻一行提示
    }

    private void drawTypeOnlyNote(GuiGraphics gg) {
        P2PTheme.drawCentered(gg, this.font, Lang.t(Lang.UI_FILTER_TYPE_ONLY), leftPos + CARD_X,
                topPos + CONTENT_Y + (CONTENT_BOTTOM - CONTENT_Y - 8) / 2, CARD_W, P2PTheme.TEXT_SECOND);
    }

    /** SEPARATOR 轨道 + TEXT_TERT 滑块，3px 宽。 */
    private static void drawScrollbar(GuiGraphics gg, int x, int y, int h, int visible, int total, int scroll, int maxScroll) {
        gg.fill(x, y, x + 3, y + h, P2PTheme.SEPARATOR);
        int thumbH = Math.max(8, h * visible / total);
        int thumbY = y + (h - thumbH) * scroll / maxScroll;
        P2PTheme.drawRoundRect(gg, x, thumbY, 3, thumbH, 1, P2PTheme.TEXT_TERT);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (chType == Mediums.ITEM && page == Page.TAG
                && mx >= leftPos && mx <= leftPos + imageWidth && my >= topPos + TAG_Y && my <= topPos + TAG_Y + TAG_LIST_H) {
            int visible = TAG_LIST_H / TAG_ROW_H;
            int maxScroll = Math.max(0, itemTags.size() - visible);
            tagScroll = Math.max(0, Math.min(maxScroll, tagScroll - (int) Math.signum(scrollY)));
            return true;
        }
        if (chType == Mediums.ITEM && page == Page.NBT && nbtEdit != null && nbtEdit.isOver(mx, my)) {
            nbtEdit.scrollBy(-(int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int ix = (int) mx, iy = (int) my;
        // Both of these are page-local hit tests. Without the page guard they cover the tab row as well,
        // which is exactly why the 标签 / NBT buttons never fired: the (invisible) NBT editor and the stale
        // tag rows returned true first and the click never reached the buttons.
        if (page == Page.NBT && nbtEdit != null && nbtEdit.mouseClicked(mx, my, button)) return true;
        if (page == Page.TAG) {
            for (TagHit h : tagHits) {
                if (ix >= h.x && ix <= h.x + h.w && iy >= h.y && iy <= h.y + h.h) {
                    if (h.id >= 0 && h.id < itemTags.size()) itemTags.remove(h.id);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean charTyped(char c, int k) {
        if (page == Page.NBT && nbtEdit != null && nbtEdit.focused) return nbtEdit.charTyped(c, k);
        return super.charTyped(c, k);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == InputConstants.KEY_ESCAPE) { onClose(); return true; }
        if (page == Page.TAG && tagBox != null && tagBox.isFocused()) {
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) { addTagFromBox(); return true; }
            tagBox.keyPressed(key, scan, mods);
            return true;    // swallow, so the vanilla inventory key ('E') cannot close the screen while typing
        }
        if (page == Page.NBT && nbtEdit != null && nbtEdit.focused) { nbtEdit.keyPressed(key, scan, mods); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);   // never call player.closeContainer(): this menu was never opened server-side
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private String trim(String s, int maxPx) {
        if (s == null) return "";
        if (this.font.width(s) <= maxPx) return s;
        while (this.font.width(s + "…") > maxPx && s.length() > 1) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private record TagHit(int id, int x, int y, int w, int h) {}

    // ============================================================ input (marker grid)
    /**
     * Fully overridden: the menu is client-only, so no click may ever turn into a container packet.
     * Marker slots remove an entry, inventory slots add one (only while the ITEM page is active).
     */
    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType clickType) {
        if (slot == null) return;
        if (pageExt != null && pageExt.slotClicked(pageCtx, slot, slotId, mouseButton, clickType)) return;
        if (page != Page.ITEM) return;   // off the ITEM page the grid is inert
        if (slot.container == this.menu.ghost) {
            int idx = slot.getSlotIndex();
            if (chType == Mediums.ITEM && idx < itemTpl.size()) { itemTpl.remove(idx); syncGhost(); }
            else if (chType == Mediums.FLUID && idx < fluidTpl.size()) { fluidTpl.remove(idx); syncGhost(); }
            return;
        }
        addMarker(slot.getItem());
    }

    /**
     * Lightweight multi-line text editor: soft-wraps long lines, scrolls vertically, supports newline,
     * backspace/delete, arrows, Home/End. Used for the NBT/regex filter on the NBT page.
     */
    private static class MultilineEdit {
        final Font font;
        final int x, y, w, h;
        final List<String> lines = new ArrayList<>();
        int caretLine = 0, caretCol = 0;
        int scroll = 0;
        boolean focused = false;
        final int lineH = 12;

        MultilineEdit(Font f, int x, int y, int w, int h) {
            this.font = f; this.x = x; this.y = y; this.w = w; this.h = h;
        }

        void setValue(String s) {
            lines.clear();
            if (s == null || s.isEmpty()) { lines.add(""); return; }
            for (String l : s.split("\n", -1)) lines.add(l);
            if (lines.isEmpty()) lines.add("");
        }

        String getValue() { return String.join("\n", lines); }

        private List<int[]> wrapLine(String s) {
            List<int[]> segs = new ArrayList<>();
            if (s.isEmpty()) { segs.add(new int[]{0, 0}); return segs; }
            int start = 0;
            while (start < s.length()) {
                int end = start, curW = 0;
                while (end < s.length() && curW + font.width(s.substring(end, end + 1)) <= w - 6) {
                    curW += font.width(s.substring(end, end + 1));
                    end++;
                }
                if (end == start) end = start + 1;
                segs.add(new int[]{start, end});
                start = end;
            }
            return segs;
        }

        void render(GuiGraphics gg) {
            P2PTheme.drawRoundRect(gg, x, y, w, h, P2PTheme.R_SLOT, P2PTheme.FIELD_BG);
            int rowsPerScreen = Math.max(1, (h - 4) / lineH);
            List<int[]> visual = new ArrayList<>();
            List<int[]> meta = new ArrayList<>();   // {lineIndex, segStart, segEnd}
            for (int li = 0; li < lines.size(); li++) {
                for (int[] seg : wrapLine(lines.get(li))) { visual.add(seg); meta.add(new int[]{li, seg[0], seg[1]}); }
            }
            int maxScroll = Math.max(0, visual.size() - rowsPerScreen);
            // the caret's visual row: the box is short, so it has to follow the caret instead of hiding the typing
            int caretRow = -1, vc = 0;
            for (int li = 0; li < lines.size(); li++) {
                List<int[]> segs = wrapLine(lines.get(li));
                if (li == caretLine) {
                    for (int i = 0; i < segs.size(); i++)
                        if (caretCol <= segs.get(i)[1] || i == segs.size() - 1) { caretRow = vc + i; break; }
                    break;
                }
                vc += segs.size();
            }
            if (focused && caretRow >= 0) {
                if (caretRow < scroll) scroll = caretRow;
                else if (caretRow >= scroll + rowsPerScreen) scroll = caretRow - rowsPerScreen + 1;
            }
            scroll = Math.max(0, Math.min(maxScroll, scroll));
            gg.enableScissor(x, y, x + w, y + h);
            for (int r = scroll; r < visual.size() && r - scroll < rowsPerScreen; r++) {
                int[] seg = visual.get(r);
                int[] m = meta.get(r);
                String txt = lines.get(m[0]).substring(seg[0], seg[1]);
                int ry = y + 2 + (r - scroll) * lineH;
                gg.drawString(font, Component.literal(txt), x + 3, ry, P2PTheme.TEXT_PRIMARY, false);
            }
            if (focused && caretRow >= scroll && caretRow < scroll + rowsPerScreen && !meta.isEmpty()) {
                int[] m = meta.get(Math.min(caretRow, meta.size() - 1));
                String pre = lines.get(m[0]).substring(m[1], Math.min(caretCol, m[2]));
                int cx = x + 3 + font.width(pre);
                int ry = y + 2 + (caretRow - scroll) * lineH;
                gg.fill(cx, ry - 1, cx + 1, ry + lineH - 1, P2PTheme.TEXT_PRIMARY);
            }
            gg.disableScissor();
            if (maxScroll > 0)
                drawScrollbar(gg, x + w + 1, y, h, rowsPerScreen, visual.size(), scroll, maxScroll);
        }

        boolean mouseClicked(double mx, double my, int b) {
            if (!isOver(mx, my)) { focused = false; return false; }
            focused = true;
            int rowsPerScreen = Math.max(1, (h - 4) / lineH);
            int r = (int) ((my - y - 2) / lineH) + scroll;
            if (r < 0 || r >= totalVisual()) { caretLine = Math.max(0, lines.size() - 1); caretCol = lines.get(caretLine).length(); return true; }
            // map visual row r -> logical line + column
            int acc = 0, li = 0;
            for (; li < lines.size(); li++) {
                int n = wrapLine(lines.get(li)).size();
                if (acc + n > r) break;
                acc += n;
            }
            if (li >= lines.size()) li = lines.size() - 1;
            List<int[]> segs = wrapLine(lines.get(li));
            int local = r - acc;
            if (local >= segs.size()) local = segs.size() - 1;
            int[] seg = segs.get(local);
            int col = seg[0];
            int runW = 0;
            while (col < seg[1] && runW + font.width(lines.get(li).substring(col, col + 1)) <= (mx - x - 3)) {
                runW += font.width(lines.get(li).substring(col, col + 1));
                col++;
            }
            caretLine = li; caretCol = col;
            return true;
        }

        private int totalVisual() {
            int n = 0;
            for (String l : lines) n += wrapLine(l).size();
            return n;
        }

        boolean isOver(double mx, double my) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }

        /** Mouse wheel: {@code render()} clamps the value against the real content height. */
        void scrollBy(int rows) { scroll = Math.max(0, scroll + rows); }

        boolean charTyped(char c, int k) {
            if (!focused) return false;
            if (c < 32) return false;   // control chars (incl. newline) are handled via keyPressed
            String line = lines.get(caretLine);
            lines.set(caretLine, line.substring(0, caretCol) + c + line.substring(caretCol));
            caretCol++;
            return true;
        }

        boolean keyPressed(int key, int scan, int mods) {
            if (!focused) return false;
            switch (key) {
                case InputConstants.KEY_BACKSPACE -> {
                    if (caretCol > 0) {
                        String line = lines.get(caretLine);
                        lines.set(caretLine, line.substring(0, caretCol - 1) + line.substring(caretCol));
                        caretCol--;
                    } else if (caretLine > 0) {
                        String prev = lines.get(caretLine - 1);
                        caretCol = prev.length();
                        lines.set(caretLine - 1, prev + lines.get(caretLine));
                        lines.remove(caretLine);
                        caretLine--;
                    }
                }
                case InputConstants.KEY_DELETE -> {
                    String line = lines.get(caretLine);
                    if (caretCol < line.length()) lines.set(caretLine, line.substring(0, caretCol) + line.substring(caretCol + 1));
                    else if (caretLine < lines.size() - 1) {
                        lines.set(caretLine, line + lines.get(caretLine + 1));
                        lines.remove(caretLine + 1);
                    }
                }
                case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                    String line = lines.get(caretLine);
                    String rest = line.substring(caretCol);
                    lines.set(caretLine, line.substring(0, caretCol));
                    lines.add(caretLine + 1, rest);
                    caretLine++; caretCol = 0;
                }
                case InputConstants.KEY_LEFT -> {
                    if (caretCol > 0) caretCol--;
                    else if (caretLine > 0) { caretLine--; caretCol = lines.get(caretLine).length(); }
                }
                case InputConstants.KEY_RIGHT -> {
                    if (caretCol < lines.get(caretLine).length()) caretCol++;
                    else if (caretLine < lines.size() - 1) { caretLine++; caretCol = 0; }
                }
                case InputConstants.KEY_UP -> { if (caretLine > 0) { caretLine--; caretCol = Math.min(caretCol, lines.get(caretLine).length()); } }
                case InputConstants.KEY_DOWN -> { if (caretLine < lines.size() - 1) { caretLine++; caretCol = Math.min(caretCol, lines.get(caretLine).length()); } }
                case InputConstants.KEY_HOME -> caretCol = 0;
                case InputConstants.KEY_END -> caretCol = lines.get(caretLine).length();
                default -> { return false; }
            }
            return true;
        }
    }

    // ============================================================ client-only menu
    /**
     * Client-side-only menu: gives the screen genuine vanilla slots (so vanilla draws slot contents,
     * highlights and tooltips, and JEI sees a container GUI) while forbidding every real interaction.
     * Slot 坐标语义未变；换肤只影响 {@link #renderBg} 画的槽位井。
     */
    public static class FilterMenu extends AbstractContainerMenu {
        public final SimpleContainer ghost = new SimpleContainer(GRID_COLS * GRID_ROWS);

        public FilterMenu(Inventory inv, Medium type) {
            super(null, 0);   // no MenuType: never registered, never synchronised
            for (int i = 0; i < GRID_COLS * GRID_ROWS; i++) {
                int col = i % GRID_COLS, row = i / GRID_COLS;
                addSlot(new ReadOnlySlot(ghost, i, GRID_X + col * SLOT, GRID_Y + row * SLOT,
                        type != Mediums.ENERGY));
            }
            for (int row = 0; row < 3; row++)
                for (int col = 0; col < 9; col++)
                    addSlot(new ReadOnlySlot(inv, 9 + row * 9 + col, INV_X + col * SLOT, INV_Y + row * SLOT, true));
            for (int col = 0; col < 9; col++)
                addSlot(new ReadOnlySlot(inv, col, INV_X + col * SLOT, HOTBAR_Y, true));
        }

        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clicked(int slotId, int button, ClickType type, Player player) { /* client-only */ }
        @Override public boolean canDragTo(Slot slot) { return false; }
    }

    private static class ReadOnlySlot extends Slot {
        private boolean active;

        ReadOnlySlot(net.minecraft.world.Container container, int index, int x, int y, boolean active) {
            super(container, index, x, y);
            this.active = active;
        }

        void setActive(boolean a) { this.active = a; }
        @Override public boolean mayPickup(Player player) { return false; }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean isActive() { return active; }
    }
}
