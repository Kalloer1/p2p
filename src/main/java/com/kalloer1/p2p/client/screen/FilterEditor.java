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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Filter editor for one endpoint (member) of a channel.
 *
 * This is a REAL vanilla container screen ({@link AbstractContainerScreen}) drawn with the vanilla shulker-box
 * GUI texture, exactly like the AE2 ME Interface config screen:
 *  - the 27 marker slots (9x3) and the player's inventory + hotbar are genuine {@link Slot}s, so vanilla draws
 *    the slot backgrounds, hover highlight and item tooltips for us (no hand-painted black squares) — but ONLY
 *    while the ITEM page is active (JEI ghost-drag target). On the TAG / NBT pages every slot is deactivated and
 *    the grid region is painted over, so the item grid never leaks into those pages.
 *  - because it is an AbstractContainerScreen, JEI recognises the GUI bounds and shows its item list / search
 *    on both sides. JEI ghost-dragging into the marker grid is provided by addons via a local JEI plugin
 *    (p2p itself does not compile against the JEI API).
 *
 * The three pages are genuinely different:
 *  - ITEM: the 9x3 marker grid + whitelist/blacklist + strict-NBT toggle.
 *  - TAG:  a scrollable tag list (many rows) + whitelist/blacklist toggle for tags.
 *  - NBT:  a large multi-line NBT/regex editor.
 *
 * The menu is client-only (no MenuType, never registered, never synced): every slot is read-only and
 * {@link #slotClicked} is fully overridden, so no container packet is ever sent to the server. The resulting
 * filter is committed with a single SET_FILTER action packet when "完成" is pressed.
 */
public class FilterEditor extends AbstractContainerScreen<FilterEditor.FilterMenu> {

    // vanilla shulker box GUI: 176x166 drawn area inside a 256x256 texture
    private static final ResourceLocation BG = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/container/shulker_box.png");
    private static final int TEX_W = 256, TEX_H = 256;
    private static final int VANILLA_H = 166;
    /** extra room inserted between the marker grid and the player inventory (mode buttons / tag / nbt). */
    private static final int EXTRA = 84;

    static final int SLOT = 18, GRID_COLS = 9, GRID_ROWS = 3;
    static final int GRID_X = 8, GRID_Y = 18;
    /** vanilla player-inventory y (84) and hotbar y (142) shifted down by EXTRA. */
    static final int INV_Y = 84 + EXTRA, HOTBAR_Y = 142 + EXTRA;

    private static final int STONE = 0xFF2C3138, HI = P2PGuiTextures.FAUX_HI, SH = P2PGuiTextures.FAUX_SH;

    /**
     * Vertical layout (relative to topPos). The page content — marker grid / tag list / NBT box — lives in
     * CONTENT_Y..CONTENT_BOTTOM; the tab row and the per-page controls sit below it, inside the inserted
     * strip. The rows must never overlap the content region: the tab row used to be drawn *inside* the tag
     * list / NBT editor rectangle, so those two swallowed every click aimed at the 标签 / NBT tabs.
     */
    private static final int CONTENT_Y = 18, CONTENT_BOTTOM = 96;
    private static final int PANEL_BOTTOM = 98;   // dark panel behind the content region on TAG/NBT pages
    private static final int ROW_TAB = 100;       // 标签页按钮行
    private static final int ROW_CTL = 120;       // 各页控件行（NBT 开关 / 标签输入 + 加标签）
    private static final int ROW_MODE = 140;      // 白名单 / 黑名单

    // custom TAG list / NBT box geometry (relative to topPos)
    private static final int TAG_X = 6, TAG_Y = CONTENT_Y, TAG_W = 164, TAG_LIST_H = CONTENT_BOTTOM - CONTENT_Y, TAG_ROW_H = 13;
    private static final int NBT_X = 6, NBT_Y = CONTENT_Y, NBT_W = 164, NBT_H = CONTENT_BOTTOM - CONTENT_Y;

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

    /** 过滤编辑器三个标签页：物品模板 / 标签 / NBT。点击标签按钮切换。 */
    private enum Page { ITEM, TAG, NBT }
    private Page page = Page.ITEM;
    private P2PFlatButton btnItem, btnTag, btnNbt;   // 顶部三个标签页
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
        this.imageWidth = 176;
        this.imageHeight = VANILLA_H + EXTRA;          // 250
        this.inventoryLabelY = this.imageHeight - 94;  // 156 — matches the shifted inventory rows
        this.titleLabelX = 8;
        this.titleLabelY = 6;

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

        addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_CANCEL), b -> onClose())
                .bounds(x + 88, y + 4, 38, 14)));
        addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_DONE).withStyle(ChatFormatting.GREEN), b -> finish())
                .bounds(x + 130, y + 4, 38, 14)));

        // 三个标签页：物品模板 / 标签 / NBT。说明文字全部放进 tooltip，不再常驻占用界面。
        btnItem = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_TAB_ITEM), b -> setPage(Page.ITEM)).bounds(x + 4, y + ROW_TAB, 54, 16)));
        btnTag  = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_TAB_TAG), b -> setPage(Page.TAG)).bounds(x + 60, y + ROW_TAB, 54, 16)));
        btnNbt  = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_TAB_NBT), b -> setPage(Page.NBT)).bounds(x + 116, y + ROW_TAB, 54, 16)));
        btnItem.setTooltip(Tooltip.create(Lang.t(chType == Mediums.FLUID ? Lang.UI_FLUID_HINT : Lang.UI_ITEM_HINT)));
        btnTag.setTooltip(Tooltip.create(Lang.t(Lang.UI_TAG_TIP)));
        btnNbt.setTooltip(Tooltip.create(Lang.t(Lang.UI_NBT_REGEX_HINT)));

        if (chType == Mediums.ITEM) {
            // whitelist/blacklist toggle (governs items AND tags) — shown on ITEM and TAG pages
            modeBtn = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_MODE, modeLabel()), b -> {
                mode = (mode == Filter.Mode.WHITELIST ? Filter.Mode.BLACKLIST : Filter.Mode.WHITELIST);
                b.setMessage(Lang.t(Lang.UI_MODE, modeLabel()));
            }).bounds(x + 8, y + ROW_MODE, 158, 16)));

            // strict-NBT toggle — shown on ITEM and NBT pages
            nbtBtn = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_NBT_SWITCH, nbtLabel()), b -> {
                matchNBT = !matchNBT;
                b.setMessage(Lang.t(Lang.UI_NBT_SWITCH, nbtLabel()));
            }).bounds(x + 92, y + ROW_CTL, 76, 16)));

            // TAG page: a tag text box + add button (only visible on the TAG page)
            tagBox = new EditBox(this.font, x + 9, y + ROW_CTL + 1, 106, 14, Lang.t(Lang.UI_TAG_HINT));
            tagBox.setMaxLength(64);
            tagBox.setHint(Lang.t(Lang.UI_TAG_PLACEHOLDER));
            addRenderableWidget(tagBox);
            addTagBtn = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_ADD_TAG), b -> addTagFromBox())
                    .bounds(x + 118, y + ROW_CTL, 50, 16)));

            // NBT page: a large multi-line editor (only drawn on the NBT page)
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
        // 选中态由按钮自身渲染（蓝底 + 下划线），不再用 "▶ " 前缀
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
    @Override
    protected void renderBg(GuiGraphics gg, float pt, int mx, int my) {
        // Custom screen draws its own background — no vanilla background needed.
        int x = leftPos, y = topPos;
        // where the vanilla "blank band" tiling starts: right below the marker rows (ITEM) or below the dark panel
        int stripTop = (page == Page.ITEM) ? 76 : PANEL_BOTTOM;
        if (page == Page.ITEM) {
            // top part: title bar + the 3x9 marker rows (vanilla texture y 0..76)
            gg.blit(BG, x, y, 0, 0, imageWidth, 76, TEX_W, TEX_H);
        } else {
            // non-ITEM page: paint a plain panel over the whole content region (no baked slot squares)
            gg.fill(x, y, x + imageWidth, y + stripTop, STONE);
            gg.fill(x, y, x + imageWidth, y + 1, HI);
            gg.fill(x, y, x + 1, y + stripTop, HI);
            gg.fill(x, y + stripTop - 1, x + imageWidth, y + stripTop, SH);
            gg.fill(x + imageWidth - 1, y, x + imageWidth, y + stripTop, SH);
        }
        // inserted blank strip: tile the empty band of the vanilla texture (y 73..81)
        int filled = 0, stripH = 76 + EXTRA - stripTop;
        while (filled < stripH) {
            int h = Math.min(8, stripH - filled);
            gg.blit(BG, x, y + stripTop + filled, 0, 73, imageWidth, h, TEX_W, TEX_H);
            filled += h;
        }
        // bottom part: inventory label band + inventory + hotbar + frame (vanilla texture y 76..166)
        gg.blit(BG, x, y + 76 + EXTRA, 0, 76, imageWidth, VANILLA_H - 76, TEX_W, TEX_H);
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        super.render(gg, mx, my, pt);
        if (page != Page.ITEM) {
            // cover the (now inactive) inventory region so no baked slot boxes leak through
            gg.fill(leftPos, topPos + 160, leftPos + imageWidth, topPos + imageHeight, STONE);
            if (page == Page.TAG) drawTagPage(gg);
            else drawNbtPage(gg);
        }
        if (pageExt != null) pageExt.renderOverlay(pageCtx, gg, mx, my);
        // tooltips for the strip widgets are drawn by super.render(); nothing custom to add here
    }

    @Override
    protected void renderLabels(GuiGraphics gg, int mx, int my) {
        // ITEM page keeps the vanilla beige texture (dark ink); TAG/NBT pages are a dark panel (light ink).
        boolean itemPage = (page == Page.ITEM);
        gg.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY,
                itemPage ? 0xFF404040 : P2PGuiTextures.TITLE, false);
        // the player inventory only exists on the ITEM page — do not leave an orphan "物品栏" label behind
        if (itemPage) gg.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFF404040, false);
        if (chType == Mediums.ENERGY && itemPage) {
            gg.fill(GRID_X, GRID_Y, GRID_X + GRID_COLS * SLOT, GRID_Y + GRID_ROWS * SLOT, 0xC0262A30);
            gg.drawString(this.font, Lang.t(Lang.UI_ENERGY_NOTE), GRID_X + 4, GRID_Y + 22, 0xFFC0C6CC, false);
        }
        // no permanent "拖入网格 / 点击格子删除" line any more: it lives in the 物品 tab's tooltip
    }

    /** TAG page: a scrollable vertical tag list (many rows) + the add box lives in the strip. */
    private void drawTagPage(GuiGraphics gg) {
        if (chType != Mediums.ITEM) { drawTypeOnlyNote(gg); return; }
        int x0 = leftPos + TAG_X, y0 = topPos + TAG_Y;
        int listH = TAG_LIST_H;
        int visible = listH / TAG_ROW_H;
        tagHits.clear();
        int maxScroll = Math.max(0, itemTags.size() - visible);
        if (tagScroll > maxScroll) tagScroll = maxScroll;
        if (tagScroll < 0) tagScroll = 0;
        for (int i = tagScroll; i < itemTags.size() && i - tagScroll < visible; i++) {
            int ry = y0 + (i - tagScroll) * TAG_ROW_H;
            String s = "#" + itemTags.get(i);
            gg.fill(x0, ry, x0 + TAG_W, ry + TAG_ROW_H - 1, P2PGuiTextures.ROW_BG2);
            gg.fill(x0, ry, x0 + TAG_W, ry + 1, P2PGuiTextures.FAUX_HI);
            gg.fill(x0, ry, x0 + 1, ry + TAG_ROW_H - 1, P2PGuiTextures.FAUX_HI);
            gg.fill(x0 + TAG_W - 1, ry, x0 + TAG_W, ry + TAG_ROW_H - 1, P2PGuiTextures.FAUX_SH);
            gg.fill(x0 + TAG_W - 13, ry, x0 + TAG_W - 12, ry + TAG_ROW_H - 1, P2PGuiTextures.DANGER); // × column
            gg.drawString(this.font, Component.literal(trim(s, TAG_W - 20)), x0 + 4, ry + 2, 0xFFE0E0E0, false);
            gg.drawString(this.font, Component.literal("×"), x0 + TAG_W - 11, ry + 2, 0xFFFFFFFF, false);
            tagHits.add(new TagHit(i, x0, ry, TAG_W, TAG_ROW_H - 1));
        }
        if (itemTags.isEmpty())
            gg.drawString(this.font, Lang.t(Lang.UI_TAG_EMPTY), x0, y0 + 4, 0xFF6a6a6a, false);
        // simple scrollbar
        if (maxScroll > 0) {
            int barX = x0 + TAG_W + 1;
            int barH = listH;
            gg.fill(barX, y0, barX + 3, y0 + barH, 0xFF5A6068);
            int thumbH = Math.max(8, barH * visible / itemTags.size());
            int thumbY = y0 + (barH - thumbH) * tagScroll / maxScroll;
            gg.fill(barX, thumbY, barX + 3, thumbY + thumbH, 0xFF2A2E34);
        }
    }

    /** NBT page: the large multi-line editor. The strict-NBT toggle lives in the strip. */
    private void drawNbtPage(GuiGraphics gg) {
        if (chType != Mediums.ITEM) { drawTypeOnlyNote(gg); return; }
        if (nbtEdit != null) nbtEdit.render(gg);   // 正则示例见 NBT 页签 tooltip，不再常驻一行提示
    }

    private void drawTypeOnlyNote(GuiGraphics gg) {
        gg.drawString(this.font, Lang.t(Lang.UI_FILTER_TYPE_ONLY), leftPos + 8, topPos + 40, 0xFFC0C6CC, false);
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

    private int fluidTint(FluidStack fs) {
        Fluid fluid = fs.getFluid();
        if (fluid == Fluids.EMPTY) return 0xFF555555;
        return IClientFluidTypeExtensions.of(fluid).getTintColor(fs) | 0xFF000000;
    }

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
            gg.fill(x, y, x + w, y + h, 0xFF0a0a0a);
            gg.fill(x, y, x + w, y + 1, 0xFF555555);
            gg.fill(x, y, x + 1, y + h, 0xFF555555);
            gg.fill(x + w - 1, y, x + w, y + h, 0xFF222222);
            gg.fill(x, y + h - 1, x + w, y + h, 0xFF222222);
            int rowsPerScreen = Math.max(1, h / lineH);
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
            for (int r = scroll; r < visual.size() && r - scroll < rowsPerScreen; r++) {
                int[] seg = visual.get(r);
                int[] m = meta.get(r);
                String txt = lines.get(m[0]).substring(seg[0], seg[1]);
                int ry = y + 2 + (r - scroll) * lineH;
                gg.drawString(font, Component.literal(txt), x + 3, ry, 0xFFe0e0e0, false);
            }
            if (focused && caretRow >= scroll && caretRow < scroll + rowsPerScreen && !meta.isEmpty()) {
                int[] m = meta.get(Math.min(caretRow, meta.size() - 1));
                String pre = lines.get(m[0]).substring(m[1], Math.min(caretCol, m[2]));
                int cx = x + 3 + font.width(pre);
                int ry = y + 2 + (caretRow - scroll) * lineH;
                gg.fill(cx, ry - 1, cx + 1, ry + lineH - 1, 0xFFFFFFFF);
            }
            if (maxScroll > 0) {
                int barX = x + w + 1;
                int thumbH = Math.max(8, h * rowsPerScreen / visual.size());
                int thumbY = y + (h - thumbH) * scroll / maxScroll;
                gg.fill(barX, y, barX + 3, y + h, 0xFF8b8b8b);
                gg.fill(barX, thumbY, barX + 3, thumbY + thumbH, 0xFF5A6068);
            }
        }

        boolean mouseClicked(double mx, double my, int b) {
            if (!isOver(mx, my)) { focused = false; return false; }
            focused = true;
            int rowsPerScreen = h / lineH;
            int r = (int) ((my - y - 2) / lineH) + scroll;
            int maxScroll = Math.max(0, totalVisual() - rowsPerScreen);
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
     * Client-side-only menu: gives the screen genuine vanilla slots (so vanilla draws slot backgrounds,
     * highlights and tooltips, and JEI sees a container GUI) while forbidding every real interaction.
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
                    addSlot(new ReadOnlySlot(inv, 9 + row * 9 + col, GRID_X + col * SLOT, INV_Y + row * SLOT, true));
            for (int col = 0; col < 9; col++)
                addSlot(new ReadOnlySlot(inv, col, GRID_X + col * SLOT, HOTBAR_Y, true));
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
