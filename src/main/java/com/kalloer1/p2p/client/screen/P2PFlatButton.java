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

    public P2PFlatButton(Builder builder) {
        super(builder);
    }

    @Override
    protected void renderWidget(GuiGraphics gg, int mx, int my, float pt) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        P2PGuiTextures.button(gg, x, y, w, h, isHoveredOrFocused(), active);
        if (getMessage() != null) {
            Font f = Minecraft.getInstance().font;
            gg.drawString(f, getMessage(), x + (w - f.width(getMessage())) / 2,
                    y + (h - 8) / 2, active ? 0xFFF0F0F0 : 0xFF8A8F96, false);
        }
    }
}
