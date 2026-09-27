package com.kalloer1.p2p.client.screen;

import com.kalloer1.p2p.Lang;
import com.kalloer1.p2p.medium.Medium;
import com.kalloer1.p2p.medium.Mediums;
import com.kalloer1.p2p.network.ChannelActionC2SPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Compact medium picker. The list is driven by the {@link com.kalloer1.p2p.medium.Mediums}
 * registry, so adding a new medium only requires calling {@code Mediums.register} from the
 * addon's {@code @Mod} constructor. No per-medium artwork is needed: each row is just text + a colored bar.
 *
 * <p>When addon mods register extra mediums the list may exceed the panel height, so rows are
 * clipped with {@link GuiGraphics#enableScissor} and the user can scroll with the mouse wheel.
 */
public class ChannelCreateScreen extends Screen {
    private static final int W = 232, H = 176;
    private static final int TITLE = P2PGuiTextures.TITLE;
    private static final int INK = P2PGuiTextures.INK;
    private static final int INK_DIM = P2PGuiTextures.INK_DIM;

    private final Screen parent;
    private int leftPos, topPos;

    private List<Medium> media;
    private int hovered = -1;
    private int scrollOffset = 0;

    // geometry
    private static final int ROW_W = 216, ROW_H = 19, ROW_X = 8, ROW_Y = 36, ROW_GAP = 2;
    private static final int CANCEL_W = 88, CANCEL_H = 20, CANCEL_X = W - 96, CANCEL_Y = H - 26;
    /** Bottom of the scrollable area (pixels from panel top). */
    private static final int LIST_BOTTOM = CANCEL_Y - 4;
    /** Max visible rows in the list area. */
    private static final int MAX_VISIBLE = (LIST_BOTTOM - ROW_Y) / (ROW_H + ROW_GAP);

    public ChannelCreateScreen(Screen parent) {
        super(Lang.t(Lang.UI_NEW_CHANNEL));
        this.parent = parent;
    }

    @Override
    protected void init() {
        leftPos = (this.width - W) / 2;
        topPos = (this.height - H) / 2;
        media = Mediums.all();
        clampScroll();
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        drawPanel(gg);
        int cx = leftPos + ROW_X;

        // clip rows to the scrollable area inside the panel
        int clipX = leftPos;
        int clipY = topPos + ROW_Y;
        int clipW = W;
        int clipH = LIST_BOTTOM - ROW_Y;
        gg.enableScissor(clipX, clipY, clipX + clipW, clipY + clipH);

        hovered = -1;
        for (int i = 0; i < media.size(); i++) {
            int cy = topPos + ROW_Y + i * (ROW_H + ROW_GAP) - scrollOffset;
            // skip rows fully above or below the visible area (optimisation, scissor clips anyway)
            if (cy + ROW_H < clipY || cy > clipY + clipH) continue;
            boolean over = inside(mx, my, cx, cy, ROW_W, ROW_H);
            if (over) hovered = i;
            drawRow(gg, cx, cy, media.get(i), over);
        }

        gg.disableScissor();

        // cancel button
        int canX = leftPos + CANCEL_X, canY = topPos + CANCEL_Y;
        boolean canOver = inside(mx, my, canX, canY, CANCEL_W, CANCEL_H);
        P2PGuiTextures.button(gg, canX, canY, CANCEL_W, CANCEL_H, canOver, true);
        Component cancel = Lang.t(Lang.UI_CANCEL);
        gg.drawString(this.font, cancel, canX + (CANCEL_W - this.font.width(cancel)) / 2, canY + 6, INK, false);
    }

    @Override
    public void renderBackground(GuiGraphics gg, int mx, int my, float pt) {
        // no-op: drawPanel() already covers the screen
    }

    private void drawPanel(GuiGraphics gg) {
        // full-screen dim
        gg.fill(0, 0, this.width, this.height, 0x66000000);
        // main panel from atlas
        P2PGuiTextures.panel(gg, leftPos, topPos, W, H);
        // header strip
        gg.fill(leftPos, topPos, leftPos + W, topPos + 17, 0xFF2f2f2f);
        gg.drawString(this.font, Lang.t(Lang.UI_CREATE_HEADER), leftPos + 8, topPos + 5, TITLE, false);
    }

    private void drawRow(GuiGraphics gg, int x, int y, Medium m, boolean hovered) {
        P2PGuiTextures.button(gg, x, y, ROW_W, ROW_H, hovered, true);
        // 4px colored bar on the left, inset inside the button bevel
        gg.fill(x + 3, y + 4, x + 7, y + ROW_H - 4, m.argbColor);
        Component name = m.displayName();
        gg.drawString(this.font, name, x + 12, y + 6, INK, false);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        // scrollY > 0 = scroll up (toward row 0), scrollY < 0 = scroll down
        int step = ROW_H + ROW_GAP;
        scrollOffset -= (int) (scrollY * step);
        clampScroll();
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        int cx = leftPos + ROW_X;
        for (int i = 0; i < media.size(); i++) {
            int cy = topPos + ROW_Y + i * (ROW_H + ROW_GAP) - scrollOffset;
            if (inside(mx, my, cx, cy, ROW_W, ROW_H)) {
                create(media.get(i));
                return true;
            }
        }
        if (inside(mx, my, leftPos + CANCEL_X, topPos + CANCEL_Y, CANCEL_W, CANCEL_H)) {
            minecraft.setScreen(parent);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void create(Medium type) {
        PacketDistributor.sendToServer(new ChannelActionC2SPacket(-1, ChannelActionC2SPacket.Action.CREATE, type.id, -1, null, false, null, ""));
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == InputConstants.KEY_ESCAPE) { minecraft.setScreen(parent); return true; }
        return super.keyPressed(key, scan, mods);
    }

    /** Clamp scrollOffset so the list never scrolls past the first or last row. */
    private void clampScroll() {
        int totalH = media.size() * (ROW_H + ROW_GAP);
        int visibleH = LIST_BOTTOM - ROW_Y;
        if (totalH <= visibleH) {
            scrollOffset = 0;
        } else {
            scrollOffset = Math.max(0, Math.min(scrollOffset, totalH - visibleH));
        }
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
