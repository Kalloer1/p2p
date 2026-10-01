package com.kalloer1.p2p.client.screen;

import com.kalloer1.p2p.Lang;
import com.kalloer1.p2p.medium.Medium;
import com.kalloer1.p2p.medium.Mediums;
import com.kalloer1.p2p.network.ChannelActionC2SPacket;
import com.kalloer1.p2p.network.ModMessages;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Compact medium picker. The list is driven by the {@link com.kalloer1.p2p.medium.Mediums}
 * registry, so adding a new medium only requires calling {@code Mediums.register} from the
 * addon's {@code @Mod} constructor. No per-medium artwork is needed: each row is just text + a colored bar.
 *
 * <p>When addon mods register extra mediums the list may exceed the card height, so rows are
 * clipped with {@link GuiGraphics#enableScissor} and the user can scroll with the mouse wheel.
 *
 * <p>1.2.0 skin: masked dialog + rounded white card (spec section 3, last row).
 */
public class ChannelCreateScreen extends Screen {
    private static final int W = 216, H = 184;
    private static final int LIST_TOP = 26;
    private static final int BTN_Y = H - 24;
    private static final int ROW_H = P2PTheme.ROW_H;
    /** Bottom of the scrollable area (pixels from card top). */
    private static final int LIST_BOTTOM = BTN_Y - 8;

    private final Screen parent;
    private int leftPos, topPos;

    private List<Medium> media;
    private int scrollOffset = 0;

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
        Component cancel = Lang.t(Lang.UI_CANCEL);
        int w = this.font.width(cancel) + 12;
        P2PFlatButton cancelBtn = new P2PFlatButton(Button.builder(cancel, b -> minecraft.setScreen(parent))
                .bounds(leftPos + W - P2PTheme.PAD - w, topPos + BTN_Y, w, 14));
        cancelBtn.setStyle(P2PFlatButton.Style.TEXT);
        addRenderableWidget(cancelBtn);
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        drawPanel(gg);
        int cx = leftPos;
        gg.enableScissor(cx + 1, topPos + LIST_TOP, cx + W - 1, topPos + LIST_BOTTOM);
        for (int i = 0; i < media.size(); i++) {
            int cy = topPos + LIST_TOP + i * ROW_H - scrollOffset;
            if (cy + ROW_H < topPos + LIST_TOP || cy > topPos + LIST_BOTTOM) continue;
            drawRow(gg, cx, cy, media.get(i), inside(mx, my, cx + 1, cy, W - 2, ROW_H));
        }
        gg.disableScissor();
        super.render(gg, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics gg) {
        // no-op: drawPanel() already covers the screen
    }

    private void drawPanel(GuiGraphics gg) {
        P2PTheme.dialog(gg, this.width, this.height, leftPos, topPos, W, H, this.font, Lang.t(Lang.UI_CREATE_HEADER));
    }

    private void drawRow(GuiGraphics gg, int x, int y, Medium m, boolean hovered) {
        if (hovered) gg.fill(x + 1, y, x + W - 1, y + ROW_H, P2PTheme.CARD_SEL);
        // 4px colored bar on the left, inset by the 8pt card padding
        P2PTheme.drawRoundRect(gg, x + P2PTheme.PAD, y + 3, 4, ROW_H - 6, 2, m.argbColor);
        gg.drawString(this.font, m.displayName(), x + P2PTheme.PAD + 10, y + 5, P2PTheme.TEXT_PRIMARY, false);
        gg.fill(x + P2PTheme.PAD, y + ROW_H - 1, x + W - P2PTheme.PAD, y + ROW_H, P2PTheme.SEPARATOR);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        // delta > 0 = scroll up (toward row 0), delta < 0 = scroll down
        scrollOffset -= (int) (delta * ROW_H);
        clampScroll();
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int cx = leftPos;
            for (int i = 0; i < media.size(); i++) {
                int cy = topPos + LIST_TOP + i * ROW_H - scrollOffset;
                if (inside(mx, my, cx + 1, cy, W - 2, ROW_H)) {
                    create(media.get(i));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void create(Medium type) {
        ModMessages.sendToServer(new ChannelActionC2SPacket(-1, ChannelActionC2SPacket.Action.CREATE, type.id, -1, null, false, null, ""));
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == InputConstants.KEY_ESCAPE) { minecraft.setScreen(parent); return true; }
        return super.keyPressed(key, scan, mods);
    }

    /** Clamp scrollOffset so the list never scrolls past the first or last row. */
    private void clampScroll() {
        int totalH = media.size() * ROW_H;
        int visibleH = LIST_BOTTOM - LIST_TOP;
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
