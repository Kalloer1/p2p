package com.kalloer1.p2p.client.screen;

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
 *    the slot backgrounds, hover highlight and item tooltips for us (no hand-painted black squares);
 *  - because it is an AbstractContainerScreen, JEI recognises the GUI bounds and shows its item list / search
 *    on both sides, and JEI ghost-dragging into the marker grid works.
 *
 * The menu is client-only (no MenuType, never registered, never synced): every slot is read-only and
 * {@link #slotClicked} is fully overridden, so no container packet is ever sent to the server. The resulting
 * filter is committed with a single SET_FILTER action packet when "完成" is pressed.
 */
public class FilterEditor extends AbstractContainerScreen<FilterEditor.FilterMenu> {

    // vanilla shulker box GUI: 176x166 drawn area inside a 256x256 texture
    private static final ResourceLocation BG = new ResourceLocation("minecraft", "textures/gui/container/shulker_box.png");
    private static final int TEX_W = 256, TEX_H = 256;
    private static final int VANILLA_H = 166;
    /** extra room inserted between the marker grid and the player inventory (mode buttons / tag / regex). */
    private static final int EXTRA = 84;

    static final int SLOT = 18, GRID_COLS = 9, GRID_ROWS = 3;
    static final int GRID_X = 8, GRID_Y = 18;
    /** vanilla player-inventory y (84) and hotbar y (142) shifted down by EXTRA. */
    static final int INV_Y = 84 + EXTRA, HOTBAR_Y = 142 + EXTRA;

    // widget rows inside the inserted blank strip (relative to topPos)
    private static final int ROW_MODE = 78;
    private static final int ROW_TAG = 98;
    private static final int ROW_CHIPS = 116;
    private static final int ROW_REGEX = 132;

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

    private EditBox tagBox, regexBox;
    private final List<TagHit> tagHits = new ArrayList<>();
    private int tagScroll = 0;   // scroll offset for the tag chip row

    public FilterEditor(Screen parent, int channelId, ChannelType type, Member member) {
        super(new FilterMenu(Minecraft.getInstance().player.getInventory(), type),
                Minecraft.getInstance().player.getInventory(),
                Component.literal("过滤编辑器"));
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

        addRenderableWidget(new FlatButton(Button.builder(Component.literal("取消"), b -> onClose())
                .bounds(x + 88, y + 4, 38, 14)));
        addRenderableWidget(new FlatButton(Button.builder(Component.literal("完成").withStyle(ChatFormatting.GREEN), b -> finish())
                .bounds(x + 130, y + 4, 38, 14)));

        addRenderableWidget(new FlatButton(Button.builder(Component.literal("模式: " + modeLabel()), b -> {
            mode = (mode == Filter.Mode.WHITELIST ? Filter.Mode.BLACKLIST : Filter.Mode.WHITELIST);
            b.setMessage(Component.literal("模式: " + modeLabel()));
        }).bounds(x + 8, y + ROW_MODE, 80, 16)));

        if (chType == ChannelType.ITEM) {
            addRenderableWidget(new FlatButton(Button.builder(Component.literal("NBT: " + (matchNBT ? "严格" : "忽略")), b -> {
                matchNBT = !matchNBT;
                b.setMessage(Component.literal("NBT: " + (matchNBT ? "严格" : "忽略")));
            }).bounds(x + 92, y + ROW_MODE, 76, 16)));

            tagBox = new EditBox(this.font, x + 9, y + ROW_TAG, 106, 14, Component.literal("标签"));
            tagBox.setMaxLength(64);
            tagBox.setHint(Component.literal("forge:ingots/iron"));
            addRenderableWidget(tagBox);
            addRenderableWidget(new FlatButton(Button.builder(Component.literal("加标签"), b -> addTagFromBox())
                    .bounds(x + 118, y + ROW_TAG - 1, 50, 16)));

            regexBox = new EditBox(this.font, x + 9, y + ROW_REGEX, 158, 14, Component.literal("NBT 正则"));
            regexBox.setMaxLength(128);
            regexBox.setHint(Component.literal("NBT 正则，如 .*Damage:0.*"));
            regexBox.setValue(nbtRegex);
            addRenderableWidget(regexBox);
        }
    }

    private String modeLabel() { return mode == Filter.Mode.WHITELIST ? "白名单" : "黑名单"; }

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
        if (regexBox != null) nbtRegex = regexBox.getValue().trim();
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
        // top part: title bar + the 3x9 marker rows (vanilla texture y 0..76)
        gg.blit(BG, x, y, 0, 0, imageWidth, 76, TEX_W, TEX_H);
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
        // fluid markers without a bucket item: tint the slot so the entry is still visible
        if (chType == ChannelType.FLUID) {
            for (int i = 0; i < fluidTpl.size() && i < GRID_COLS * GRID_ROWS; i++) {
                if (!FluidUtil.getFilledBucket(fluidTpl.get(i)).isEmpty()) continue;
                int cx = leftPos + GRID_X + (i % GRID_COLS) * SLOT;
                int cy = topPos + GRID_Y + (i / GRID_COLS) * SLOT;
                gg.fill(cx + 1, cy + 1, cx + SLOT - 3, cy + SLOT - 3, fluidTint(fluidTpl.get(i)));
            }
        }
        this.renderTooltip(gg, mx, my);
    }

    @Override
    protected void renderLabels(GuiGraphics gg, int mx, int my) {
        super.renderLabels(gg, mx, my);   // title + "物品栏", vanilla dark-grey ink
        if (chType == ChannelType.ENERGY) {
            gg.fill(GRID_X, GRID_Y, GRID_X + GRID_COLS * SLOT, GRID_Y + GRID_ROWS * SLOT, 0xC0C6C6C6);
            gg.drawString(this.font, Component.literal("能量频道无需过滤（始终通过）"), GRID_X + 4, GRID_Y + 22, 0xFF404040, false);
            return;
        }
        drawTagChips(gg);
    }

    /** tag chips are drawn in renderLabels space (already translated by leftPos/topPos). */
    private void drawTagChips(GuiGraphics gg) {
        tagHits.clear();
        if (chType != ChannelType.ITEM) return;
        int chipH = 12, gap = 2, cx = GRID_X, cy = ROW_CHIPS;
        if (tagScroll > itemTags.size()) tagScroll = itemTags.size();
        int remaining = 0;
        boolean moreUp = tagScroll > 0;
        boolean moreDown = false;
        for (int i = tagScroll; i < itemTags.size(); i++) {
            String s = "#" + itemTags.get(i);
            int cw = this.font.width(s) + 8;
            if (cx + cw > imageWidth - 8) {
                remaining = itemTags.size() - i;
                break;                              // one row only; rest stay folded behind the scroll
            }
            if (i + 1 < itemTags.size()) moreDown = true;
            gg.fill(cx, cy, cx + cw, cy + chipH, 0xFF8b8b8b);
            gg.fill(cx, cy, cx + cw, cy + 1, 0xFFdedede);
            gg.fill(cx, cy, cx + 1, cy + chipH, 0xFFdedede);
            gg.fill(cx, cy + chipH - 1, cx + cw, cy + chipH, 0xFF555555);
            gg.fill(cx + cw - 1, cy, cx + cw, cy + chipH, 0xFF555555);
            gg.drawString(this.font, Component.literal(s), cx + 4, cy + 2, 0xFFFFFFFF, false);
            tagHits.add(new TagHit(i, leftPos + cx, topPos + cy, cw, chipH));
            cx += cw + gap;
        }
        // scroll indicators (fold/collapse affordance)
        if (moreUp) {
            gg.drawString(this.font, Component.literal("↑"), GRID_X, cy + 1, 0xFF404040, false);
            cx += 10;
        }
        if (remaining > 0) {
            String fold = "+" + remaining;
            int cw = this.font.width(fold) + 8;
            if (cx + cw <= imageWidth - 8) {
                gg.fill(cx, cy, cx + cw, cy + chipH, 0xFF6a6a6a);
                gg.fill(cx, cy, cx + cw, cy + 1, 0xFFbdbdbd);
                gg.fill(cx, cy, cx + 1, cy + chipH, 0xFFbdbdbd);
                gg.fill(cx, cy + chipH - 1, cx + cw, cy + chipH, 0xFF4a4a4a);
                gg.fill(cx + cw - 1, cy, cx + cw, cy + chipH, 0xFF4a4a4a);
                gg.drawString(this.font, Component.literal(fold), cx + 4, cy + 2, 0xFFFFFFFF, false);
                tagHits.add(new TagHit(-1, leftPos + cx, topPos + cy, cw, chipH));
                moreDown = false;   // the "+N" chip is the fold target; tapping it opens one page
            }
        }
        if (moreDown) {
            gg.drawString(this.font, Component.literal("▾"), cx + 2, cy + 1, 0xFF404040, false);
        }
        if (itemTags.isEmpty())
            gg.drawString(this.font, Component.literal("（无标签，点击标签可移除）"), GRID_X, ROW_CHIPS + 2, 0xFF6a6a6a, false);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (chType == ChannelType.ITEM && my >= topPos + ROW_CHIPS && my <= topPos + ROW_CHIPS + 12
                && mx >= leftPos + GRID_X && mx <= leftPos + imageWidth - 8) {
            tagScroll = Math.max(0, Math.min(itemTags.size(),
                    tagScroll + (int) Math.signum(delta) * 3));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    /** Vanilla-style grey button (same chrome as Sky Logistics' ConfigPanel buttons). */
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
                gg.fill(x, y, x + w, y + h, 0xFF373737);
                gg.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
                gg.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFFFFFFF);
                gg.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFFFFFFF);
                gg.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF555555);
                gg.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, 0xFF555555);
            } else {
                gg.fill(x, y, x + w, y + h, 0xFF373737);
                gg.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF8b8b8b);
            }
            if (msg != null) {
                Font f = Minecraft.getInstance().font;
                gg.drawString(f, msg, x + (w - f.width(msg)) / 2,
                        y + (h - 8) / 2, active ? 0xFF000000 : 0xFF707070, false);
            }
        }
    }

    // ============================================================ input
    /**
     * Fully overridden: the menu is client-only, so no click may ever turn into a container packet.
     * Marker slots remove an entry, inventory slots add one.
     */
    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType clickType) {
        if (slot == null) return;
        if (slot.container == this.menu.ghost) {
            int idx = slot.getSlotIndex();
            if (chType == ChannelType.ITEM && idx < itemTpl.size()) { itemTpl.remove(idx); syncGhost(); }
            else if (chType == ChannelType.FLUID && idx < fluidTpl.size()) { fluidTpl.remove(idx); syncGhost(); }
            return;
        }
        addMarker(slot.getItem());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int ix = (int) mx, iy = (int) my;
        for (TagHit h : tagHits) {
            if (ix >= h.x && ix <= h.x + h.w && iy >= h.y && iy <= h.y + h.h) {
                if (h.id == -1) tagScroll = Math.min(itemTags.size(), tagScroll + 3);   // fold chip: scroll forward
                else if (h.id < itemTags.size()) itemTags.remove(h.id);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == InputConstants.KEY_ESCAPE) { onClose(); return true; }
        if (tagBox != null && tagBox.isFocused()) {
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) { addTagFromBox(); return true; }
            tagBox.keyPressed(key, scan, mods);
            return true;    // swallow, so the vanilla inventory key ('E') cannot close the screen while typing
        }
        if (regexBox != null && regexBox.isFocused()) {
            regexBox.keyPressed(key, scan, mods);
            return true;
        }
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

    private record TagHit(int id, int x, int y, int w, int h) {}

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
        private final boolean active;

        ReadOnlySlot(net.minecraft.world.Container container, int index, int x, int y, boolean active) {
            super(container, index, x, y);
            this.active = active;
        }

        @Override public boolean mayPickup(Player player) { return false; }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean isActive() { return active; }
    }
}
