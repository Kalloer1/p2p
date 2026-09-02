package com.kalloer1.p2p.client.screen;

import com.kalloer1.p2p.Lang;
import com.kalloer1.p2p.channel.ChannelType;
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
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

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
 *    on both sides, and JEI ghost-dragging into the marker grid works (gated to the ITEM page by P2PJeiPlugin).
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
    private static final ResourceLocation BG = new ResourceLocation("minecraft:textures/gui/container/shulker_box.png");
    private static final int TEX_W = 256, TEX_H = 256;
    private static final int VANILLA_H = 166;
    /** extra room inserted between the marker grid and the player inventory (mode buttons / tag / nbt). */
    private static final int EXTRA = 84;

    static final int SLOT = 18, GRID_COLS = 9, GRID_ROWS = 3;
    static final int GRID_X = 8, GRID_Y = 18;
    /** vanilla player-inventory y (84) and hotbar y (142) shifted down by EXTRA. */
    static final int INV_Y = 84 + EXTRA, HOTBAR_Y = 142 + EXTRA;

    private static final int STONE = 0xFF2C3138, HI = P2PGuiTextures.FAUX_HI, SH = P2PGuiTextures.FAUX_SH;

    // widget rows inside the inserted blank strip (relative to topPos)
    private static final int ROW_TAB = 76;        // 标签页按钮行
    private static final int ROW_CHIPS = 116;     // ITEM 页提示行

    // custom TAG list / NBT box geometry (relative to topPos)
    private static final int TAG_X = 6, TAG_Y = 18, TAG_W = 164, TAG_LIST_H = 100, TAG_ROW_H = 13;
    private static final int NBT_X = 6, NBT_Y = 18, NBT_W = 164, NBT_H = 98;

    private final Screen parent;
    private final int channelId;
    private final ChannelType chType;
    private final Member member;
    private final boolean extract;

    private Filter.Mode mode = Filter.Mode.WHITELIST;
    private boolean matchNBT = false;
    private final List<ItemStack> itemTpl = new ArrayList<>();
    private final List<FluidStack> fluidTpl = new ArrayList<>();
    private final List<ResourceLocation> itemTags = new ArrayList<>();
    private String nbtRegex = "";

    private EditBox tagBox;
    private MultilineEdit nbtEdit;
    private final List<TagHit> tagHits = new ArrayList<>();
    private int tagScroll = 0;   // scroll offset for the tag list (vertical)

    /** 过滤编辑器三个标签页：物品模板 / 标签 / NBT。点击标签按钮切换。 */
    private enum Page { ITEM, TAG, NBT }
    private Page page = Page.ITEM;
    private P2PFlatButton btnItem, btnTag, btnNbt;   // 顶部三个标签页
    private P2PFlatButton modeBtn, nbtBtn, addTagBtn; // 各页控件（按页显隐）

    public FilterEditor(Screen parent, int channelId, ChannelType type, Member member) {
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
    }

    public ChannelType getType() { return chType; }

    /** Screen-space rectangle of the marker grid — JEI uses it as the ghost-drop target. */
    public Rect2i getMarkerGridArea() {
        return new Rect2i(leftPos + GRID_X, topPos + GRID_Y, GRID_COLS * SLOT, GRID_ROWS * SLOT);
    }

    /** JEI ghost-drag may only drop an item onto the grid while the ITEM page is active. */
    public boolean isItemPage() { return page == Page.ITEM; }

    /** Called by the JEI ghost handler and by clicking an inventory slot. */
    public void addMarker(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        if (chType == ChannelType.ITEM) {
            if (itemTpl.size() >= GRID_COLS * GRID_ROWS) return;
            for (ItemStack s : itemTpl) if (ItemStack.isSameItem(s, stack)) return; // no duplicates
            ItemStack copy = stack.copy();
            copy.setCount(1);
            itemTpl.add(copy);
            syncGhost();
        } else if (chType == ChannelType.FLUID) {
            FluidStack fs = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
            if (fs.isEmpty()) return;
            if (fluidTpl.size() >= GRID_COLS * GRID_ROWS) return;
            for (FluidStack f : fluidTpl) if (f.getFluid() == fs.getFluid()) return;
            fluidTpl.add(new FluidStack(fs.getFluid(), 1000));
            syncGhost();
        }
    }

    /** Mirror the template lists into the ghost container so vanilla renders them in the marker slots. */
    private void syncGhost() {
        for (int i = 0; i < GRID_COLS * GRID_ROWS; i++) {
            ItemStack show = ItemStack.EMPTY;
            if (chType == ChannelType.ITEM && i < itemTpl.size()) {
                show = itemTpl.get(i);
            } else if (chType == ChannelType.FLUID && i < fluidTpl.size()) {
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

        // 三个标签页：物品模板 / 标签 / NBT
        btnItem = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_TAB_ITEM), b -> setPage(Page.ITEM)).bounds(x + 4, y + ROW_TAB, 54, 16)));
        btnTag  = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_TAB_TAG), b -> setPage(Page.TAG)).bounds(x + 60, y + ROW_TAB, 54, 16)));
        btnNbt  = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_TAB_NBT), b -> setPage(Page.NBT)).bounds(x + 116, y + ROW_TAB, 54, 16)));

        if (chType == ChannelType.ITEM) {
            // whitelist/blacklist toggle (governs items AND tags) — shown on ITEM and TAG pages
            modeBtn = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_MODE, modeLabel()), b -> {
                mode = (mode == Filter.Mode.WHITELIST ? Filter.Mode.BLACKLIST : Filter.Mode.WHITELIST);
                b.setMessage(Lang.t(Lang.UI_MODE, modeLabel()));
            }).bounds(x + 8, y + 140, 158, 16)));

            // strict-NBT toggle — shown on ITEM and NBT pages
            nbtBtn = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_NBT_SWITCH, nbtLabel()), b -> {
                matchNBT = !matchNBT;
                b.setMessage(Lang.t(Lang.UI_NBT_SWITCH, nbtLabel()));
            }).bounds(x + 92, y + 124, 76, 16)));

            // TAG page: a tag text box + add button (only visible on the TAG page)
            tagBox = new EditBox(this.font, x + 9, y + 124, 106, 14, Lang.t(Lang.UI_TAG_HINT));
            tagBox.setMaxLength(64);
            tagBox.setHint(Lang.t(Lang.UI_TAG_PLACEHOLDER));
            addRenderableWidget(tagBox);
            addTagBtn = addRenderableWidget(new P2PFlatButton(Button.builder(Lang.t(Lang.UI_ADD_TAG), b -> addTagFromBox())
                    .bounds(x + 118, y + 123, 50, 16)));

            // NBT page: a large multi-line editor (only drawn on the NBT page)
            nbtEdit = new MultilineEdit(this.font, x + NBT_X, y + NBT_Y, NBT_W, NBT_H);
            nbtEdit.setValue(nbtRegex);
        } else {
            modeBtn = null; nbtBtn = null; tagBox = null; addTagBtn = null; nbtEdit = null;
        }
        applyPage();
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
        if (tagBox != null) tagBox.visible = (page == Page.TAG);
        if (addTagBtn != null) addTagBtn.visible = (page == Page.TAG);
        // deactivate every slot off the ITEM page so the grid/inventory never renders or captures clicks
        setSlotsActive(itemPage, itemPage);
        btnItem.setMessage(Component.literal(page == Page.ITEM ? "▶ " : "").append(Lang.t(Lang.UI_TAB_ITEM)));
        btnTag.setMessage(Component.literal(page == Page.TAG ? "▶ " : "").append(Lang.t(Lang.UI_TAB_TAG)));
        btnNbt.setMessage(Component.literal(page == Page.NBT ? "▶ " : "").append(Lang.t(Lang.UI_TAB_NBT)));
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
        if (nbtEdit != null) nbtRegex = nbtEdit.getValue().trim();
        Filter f;
        if (chType == ChannelType.ENERGY) f = Filter.energyPass();
        else if (chType == ChannelType.ITEM) f = Filter.itemFilter(mode, matchNBT, itemTpl, itemTags, nbtRegex);
        else f = Filter.fluidFilter(mode, fluidTpl);
        CompoundTag tag = f.serializeNBT();
        ModMessages.INSTANCE.sendToServer(new ChannelActionC2SPacket(channelId,
                ChannelActionC2SPacket.Action.SET_FILTER, null, 0, member.key(), extract, tag, ""));
        minecraft.setScreen(parent);
    }

    // ============================================================ rendering
    @Override
    protected void renderBg(GuiGraphics gg, float pt, int mx, int my) {
        // 1.20.1: Screen.render() does NOT dim the background for us — do it here, where it is guaranteed to run.
        this.renderBackground(gg);
        int x = leftPos, y = topPos;
        if (page == Page.ITEM) {
            // top part: title bar + the 3x9 marker rows (vanilla texture y 0..76)
            gg.blit(BG, x, y, 0, 0, imageWidth, 76, TEX_W, TEX_H);
        } else {
            // non-ITEM page: paint a plain panel over the grid region (no baked slot squares)
            gg.fill(x, y, x + imageWidth, y + 76, STONE);
            gg.fill(x, y, x + imageWidth, y + 1, HI);
            gg.fill(x, y, x + 1, y + 76, HI);
            gg.fill(x, y + 75, x + imageWidth, y + 76, SH);
            gg.fill(x + imageWidth - 1, y, x + imageWidth, y + 76, SH);
        }
        // inserted blank strip: tile the empty band of the vanilla texture (y 73..81)
        int filled = 0;
        while (filled < EXTRA) {
            int h = Math.min(8, EXTRA - filled);
            gg.blit(BG, x, y + 76 + filled, 0, 73, imageWidth, h, TEX_W, TEX_H);
            filled += h;
        }
        // bottom part: inventory label band + inventory + hotbar + frame (vanilla texture y 76..166)
        gg.blit(BG, x, y + 76 + EXTRA, 0, 76, imageWidth, VANILLA_H - 76, TEX_W, TEX_H);
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        super.render(gg, mx, my, pt);
        if (page != Page.ITEM) {
            // cover the (now inactive) marker grid region and the inventory region so no slot boxes leak through
            gg.fill(leftPos, topPos, leftPos + imageWidth, topPos + 76, STONE);
            gg.fill(leftPos, topPos + 160, leftPos + imageWidth, topPos + imageHeight, STONE);
            // redraw the title (it sits in the top band we just covered)
            gg.drawString(this.font, this.title, leftPos + titleLabelX, topPos + titleLabelY, 0xFFC0C6CC, false);
            if (page == Page.TAG) drawTagPage(gg);
            else drawNbtPage(gg);
        }
        // tooltips for the strip widgets are drawn by super.render(); nothing custom to add here
    }

    @Override
    protected void renderLabels(GuiGraphics gg, int mx, int my) {
        super.renderLabels(gg, mx, my);   // title + "物品栏", vanilla dark-grey ink
        if (chType == ChannelType.ENERGY) {
            gg.fill(GRID_X, GRID_Y, GRID_X + GRID_COLS * SLOT, GRID_Y + GRID_ROWS * SLOT, 0xC0262A30);
            gg.drawString(this.font, Lang.t(Lang.UI_ENERGY_NOTE), GRID_X + 4, GRID_Y + 22, 0xFFC0C6CC, false);
            return;
        }
        // ITEM page hint (the grid is the main content; tag/nbt pages draw their own content in render())
        if (page == Page.ITEM) {
            Component hint = chType == ChannelType.ITEM ? Lang.t(Lang.UI_ITEM_HINT) : Lang.t(Lang.UI_FLUID_HINT);
            gg.drawString(this.font, hint, GRID_X, ROW_CHIPS + 2, 0xFF9AA0A6, false);
        }
    }

    /** TAG page: a scrollable vertical tag list (many rows) + the add box lives in the strip. */
    private void drawTagPage(GuiGraphics gg) {
        if (chType != ChannelType.ITEM) { drawTypeOnlyNote(gg); return; }
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
        if (chType != ChannelType.ITEM) { drawTypeOnlyNote(gg); return; }
        if (nbtEdit != null) nbtEdit.render(gg);
        gg.drawString(this.font, Lang.t(Lang.UI_NBT_REGEX_HINT), leftPos + NBT_X, topPos + NBT_Y + NBT_H + 4, 0xFF9AA0A6, false);
    }

    private void drawTypeOnlyNote(GuiGraphics gg) {
        gg.drawString(this.font, Lang.t(Lang.UI_FILTER_TYPE_ONLY), leftPos + 8, topPos + 40, 0xFFC0C6CC, false);
        gg.drawString(this.font, Lang.t(Lang.UI_FILTER_TYPE_ONLY2), leftPos + 8, topPos + 56, 0xFF9AA0A6, false);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (page == Page.TAG && chType == ChannelType.ITEM
                && mx >= leftPos && mx <= leftPos + imageWidth && my >= topPos + TAG_Y && my <= topPos + TAG_Y + TAG_LIST_H) {
            int visible = TAG_LIST_H / TAG_ROW_H;
            int maxScroll = Math.max(0, itemTags.size() - visible);
            tagScroll = Math.max(0, Math.min(maxScroll, tagScroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int ix = (int) mx, iy = (int) my;
        if (nbtEdit != null && nbtEdit.mouseClicked(mx, my, button)) return true;
        for (TagHit h : tagHits) {
            if (ix >= h.x && ix <= h.x + h.w && iy >= h.y && iy <= h.y + h.h) {
                if (h.id >= 0 && h.id < itemTags.size()) itemTags.remove(h.id);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean charTyped(char c, int k) {
        if (nbtEdit != null && nbtEdit.focused) return nbtEdit.charTyped(c, k);
        return super.charTyped(c, k);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == InputConstants.KEY_ESCAPE) { onClose(); return true; }
        if (tagBox != null && tagBox.isFocused()) {
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) { addTagFromBox(); return true; }
            tagBox.keyPressed(key, scan, mods);
            return true;    // swallow, so the vanilla inventory key ('E') cannot close the screen while typing
        }
        if (nbtEdit != null && nbtEdit.focused) { nbtEdit.keyPressed(key, scan, mods); return true; }
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
        if (page != Page.ITEM) return;   // off the ITEM page the grid is inert
        if (slot.container == this.menu.ghost) {
            int idx = slot.getSlotIndex();
            if (chType == ChannelType.ITEM && idx < itemTpl.size()) { itemTpl.remove(idx); syncGhost(); }
            else if (chType == ChannelType.FLUID && idx < fluidTpl.size()) { fluidTpl.remove(idx); syncGhost(); }
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
            int rowsPerScreen = h / lineH;
            List<int[]> visual = new ArrayList<>();
            List<int[]> meta = new ArrayList<>();   // {lineIndex, segStart, segEnd}
            for (int li = 0; li < lines.size(); li++) {
                for (int[] seg : wrapLine(lines.get(li))) { visual.add(seg); meta.add(new int[]{li, seg[0], seg[1]}); }
            }
            int maxScroll = Math.max(0, visual.size() - rowsPerScreen);
            if (scroll > maxScroll) scroll = maxScroll;
            if (scroll < 0) scroll = 0;
            for (int r = scroll; r < visual.size() && r - scroll < rowsPerScreen; r++) {
                int[] seg = visual.get(r);
                int[] m = meta.get(r);
                String txt = lines.get(m[0]).substring(seg[0], seg[1]);
                int ry = y + 2 + (r - scroll) * lineH;
                gg.drawString(font, Component.literal(txt), x + 3, ry, 0xFFe0e0e0, false);
            }
            if (focused) {
                int vc = 0;
                for (int li = 0; li < caretLine; li++) vc += wrapLine(lines.get(li)).size();
                List<int[]> segs = wrapLine(lines.get(caretLine));
                int rr = -1;
                for (int i = 0; i < segs.size(); i++) {
                    int[] seg = segs.get(i);
                    if (caretCol <= seg[1] || i == segs.size() - 1) { rr = vc + i; break; }
                }
                if (rr >= 0 && rr - scroll >= 0 && rr - scroll < rowsPerScreen) {
                    int[] seg = segs.get(Math.min(rr - vc, segs.size() - 1));
                    String pre = lines.get(caretLine).substring(seg[0], Math.min(caretCol, seg[1]));
                    int cx = x + 3 + font.width(pre);
                    int ry = y + 2 + (rr - scroll) * lineH;
                    gg.fill(cx, ry - 1, cx + 1, ry + lineH - 1, 0xFFFFFFFF);
                }
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
            if (mx < x || mx > x + w || my < y || my > y + h) { focused = false; return false; }
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

        public FilterMenu(Inventory inv, ChannelType type) {
            super(null, 0);   // no MenuType: never registered, never synchronised
            for (int i = 0; i < GRID_COLS * GRID_ROWS; i++) {
                int col = i % GRID_COLS, row = i / GRID_COLS;
                addSlot(new ReadOnlySlot(ghost, i, GRID_X + col * SLOT, GRID_Y + row * SLOT,
                        type != ChannelType.ENERGY));
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
