package com.kalloer1.p2p.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;

/**
 * Shared flat-style button that renders from the P2P GUI atlas. All configuration screens should use
 * this instead of vanilla Button or per-screen FlatButton copies, so the mod's chrome stays consistent.
 */
public class P2PFlatButton extends Button {

    /** true = this button is the active choice of a group (e.g. the current tab). Off by default. */
    private boolean selected;

    public P2PFlatButton(Builder builder) {
        super(builder);
    }

    /** Mark this button as the active choice of its group; returns {@code this} for chaining. */
    public P2PFlatButton setSelected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public boolean isSelected() { return selected; }

    @Override
    protected void renderWidget(GuiGraphics gg, int mx, int my, float pt) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hovered = isHoveredOrFocused();
        P2PGuiTextures.button(gg, x, y, w, h, hovered, active, selected);
        if (getMessage() != null) {
            Font f = Minecraft.getInstance().font;
            int ink = !active ? 0xFF8A8F96 : (selected || hovered ? 0xFFFFFFFF : 0xFFD2D7DD);
            gg.drawString(f, getMessage(), x + (w - f.width(getMessage())) / 2,
                    y + (h - 8) / 2, ink, false);
        }
    }
}
