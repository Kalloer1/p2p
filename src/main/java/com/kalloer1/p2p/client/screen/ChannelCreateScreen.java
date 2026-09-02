package com.kalloer1.p2p.client.screen;

import com.kalloer1.p2p.Lang;
import com.kalloer1.p2p.channel.ChannelType;
import com.kalloer1.p2p.network.ChannelActionC2SPacket;
import com.kalloer1.p2p.network.ModMessages;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Compact medium picker. The list is driven by {@link MediumRegistry}, so adding a new
 * medium only requires registering it there (and implementing its transfer backend).
 * No per-medium artwork is needed: each row is just text + a colored bar.
 */
public class ChannelCreateScreen extends Screen {
    private static final int W = 232, H = 176;
    private static final int TITLE = P2PGuiTextures.TITLE;
    private static final int INK = P2PGuiTextures.INK;
    private static final int INK_DIM = P2PGuiTextures.INK_DIM;

    private final Screen parent;
    private int leftPos, topPos;

    private List<MediumRegistry.Medium> media;
    private int hovered = -1;

    // geometry
    private static final int ROW_W = 216, ROW_H = 19, ROW_X = 8, ROW_Y = 36, ROW_GAP = 2;
    private static final int CANCEL_W = 88, CANCEL_H = 20, CANCEL_X = W - 96, CANCEL_Y = H - 26;

    public ChannelCreateScreen(Screen parent) {
        super(Lang.t(Lang.UI_NEW_CHANNEL));
        this.parent = parent;
    }

    @Override
    protected void init() {
        leftPos = (this.width - W) / 2;
        topPos = (this.height - H) / 2;
        media = MediumRegistry.all();
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        drawPanel(gg);
        int cx = leftPos + ROW_X;
        for (int i = 0; i < media.size(); i++) {
            int cy = topPos + ROW_Y + i * (ROW_H + ROW_GAP);
            boolean over = inside(mx, my, cx, cy, ROW_W, ROW_H);
            if (over) hovered = i;
            else if (hovered == i) hovered = -1;
            drawRow(gg, cx, cy, media.get(i), over);
        }
        // cancel button
        int canX = leftPos + CANCEL_X, canY = topPos + CANCEL_Y;
        boolean canOver = inside(mx, my, canX, canY, CANCEL_W, CANCEL_H);
        P2PGuiTextures.button(gg, canX, canY, CANCEL_W, CANCEL_H, canOver, true);
        Component cancel = Lang.t(Lang.UI_CANCEL);
        gg.drawString(this.font, cancel, canX + (CANCEL_W - this.font.width(cancel)) / 2, canY + 6, INK, false);
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

    private void drawRow(GuiGraphics gg, int x, int y, MediumRegistry.Medium m, boolean hovered) {
        P2PGuiTextures.button(gg, x, y, ROW_W, ROW_H, hovered, true);
        // 4px colored bar on the left, inset inside the button bevel
        gg.fill(x + 3, y + 4, x + 7, y + ROW_H - 4, m.argbColor());
        Component name = Lang.t(m.langKey());
        gg.drawString(this.font, name, x + 12, y + 6, INK, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        int cx = leftPos + ROW_X;
        for (int i = 0; i < media.size(); i++) {
            int cy = topPos + ROW_Y + i * (ROW_H + ROW_GAP);
            if (inside(mx, my, cx, cy, ROW_W, ROW_H)) {
                create(media.get(i).type());
                return true;
            }
        }
        if (inside(mx, my, leftPos + CANCEL_X, topPos + CANCEL_Y, CANCEL_W, CANCEL_H)) {
            minecraft.setScreen(parent);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void create(ChannelType type) {
        ModMessages.INSTANCE.sendToServer(new ChannelActionC2SPacket(-1, ChannelActionC2SPacket.Action.CREATE, type.name(), -1, null, false, null, ""));
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == InputConstants.KEY_ESCAPE) { minecraft.setScreen(parent); return true; }
        return super.keyPressed(key, scan, mods);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
