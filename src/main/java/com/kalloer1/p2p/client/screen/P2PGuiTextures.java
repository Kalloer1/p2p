package com.kalloer1.p2p.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Single-atlas UV registry for P2P GUI elements.
 * Layout (256x256):
 *   panel     u=  2 v=  2  20x20 border=4
 *   field     u= 26 v=  2  20x20 border=4
 *   btn       u= 50 v=  2  20x20 border=4
 *   btn_h     u= 74 v=  2  20x20 border=4
 *   btn_d     u= 98 v=  2  20x20 border=4
 *   tab       u=122 v=  2  20x20 border=4
 *   slot      u=146 v=  2  18x18 fixed
 *   scroll    u=168 v=  2   6x16 fixed
 *   toggle_on u=178 v=  2  44x20 fixed
 *   toggle_off u= 2 v= 26  44x20 fixed
 *   check     u= 50 v=26  14x14 fixed
 *   check_on  u= 68 v=26  14x14 fixed
 */
public final class P2PGuiTextures {

    public static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath("p2p", "textures/gui/p2p.png");
    public static final int ATLAS_W = 256;
    public static final int ATLAS_H = 256;

    public static final class Rgn {
        public final int u, v, w, h, border;
        public Rgn(int u, int v, int w, int h, int border) {
            this.u = u; this.v = v; this.w = w; this.h = h; this.border = border;
        }
        public boolean nineSlice() { return border > 0; }
    }

    public static final Rgn PANEL      = new Rgn(2, 2, 20, 20, 4);
    public static final Rgn FIELD      = new Rgn(26, 2, 20, 20, 4);
    public static final Rgn BTN        = new Rgn(50, 2, 20, 20, 4);
    public static final Rgn BTN_HOVER  = new Rgn(74, 2, 20, 20, 4);
    public static final Rgn BTN_DISABLED = new Rgn(98, 2, 20, 20, 4);
    public static final Rgn TAB        = new Rgn(122, 2, 20, 20, 4);
    public static final Rgn SLOT       = new Rgn(146, 2, 18, 18, 0);
    public static final Rgn SCROLL     = new Rgn(168, 2, 6, 16, 0);
    public static final Rgn TOGGLE_ON  = new Rgn(178, 2, 44, 20, 0);
    public static final Rgn TOGGLE_OFF = new Rgn(2, 26, 44, 20, 0);
    public static final Rgn CHECK      = new Rgn(50, 26, 14, 14, 0);
    public static final Rgn CHECK_ON   = new Rgn(68, 26, 14, 14, 0);

    // ---- shared dark-theme palette (ARGB) ----
    // Single source of truth for every screen's chrome so the whole mod stays consistent.
    public static final int INK       = 0xFFE6E6E6;  // primary text (light)
    public static final int INK_DIM   = 0xFF9AA0A6;  // secondary / dim text
    public static final int TITLE     = 0xFFE8E8E8;  // header / title text
    public static final int TAB_BG    = 0xFF262A30;  // top tab / header strip
    public static final int ROW_BG    = 0xFF2C3138;  // inner list background (left column)
    public static final int ROW_BG2   = 0xFF333941;  // inner panel background (right / detail)
    public static final int SEL       = 0xFF3C4350;  // selected row highlight
    public static final int GRP_BG    = 0xFF363C45;  // group header background
    public static final int SEP       = 0xFF1E2228;  // subtle row separator
    public static final int FAUX_BTN  = 0xFF3A4150;  // faux button fill
    public static final int FAUX_HI   = 0xFF565E6B;  // faux/inset light bevel edge (top/left for raised)
    public static final int FAUX_SH   = 0xFF14171C;  // faux/inset dark bevel edge (bottom/right / recessed top-left)
    public static final int MARK_GOLD = 0xFFD6B24A;  // marked-filter accent
    public static final int DANGER    = 0xFFE06B6B;  // destructive text
    public static final int OK_GREEN  = 0xFF5FC35F;  // positive text

    /** Fixed-size blit. */
    public static void blit(GuiGraphics gg, int x, int y, Rgn r) {
        gg.blit(ATLAS, x, y, r.u, r.v, r.w, r.h, ATLAS_W, ATLAS_H);
    }

    /** Manual 9-slice blit using the region's border. */
    public static void blit9(GuiGraphics gg, int x, int y, int w, int h, Rgn r) {
        if (!r.nineSlice()) {
            blit(gg, x, y, r);
            return;
        }
        int b = r.border;
        int u = r.u, v = r.v, rw = r.w, rh = r.h;
        // corners
        gg.blit(ATLAS, x, y, u, v, b, b, ATLAS_W, ATLAS_H);
        gg.blit(ATLAS, x + w - b, y, u + rw - b, v, b, b, ATLAS_W, ATLAS_H);
        gg.blit(ATLAS, x, y + h - b, u, v + rh - b, b, b, ATLAS_W, ATLAS_H);
        gg.blit(ATLAS, x + w - b, y + h - b, u + rw - b, v + rh - b, b, b, ATLAS_W, ATLAS_H);
        // edges + center
        int cw = w - 2 * b;
        int ch = h - 2 * b;
        if (cw > 0) {
            gg.blit(ATLAS, x + b, y, u + b, v, cw, b, ATLAS_W, ATLAS_H);
            gg.blit(ATLAS, x + b, y + h - b, u + b, v + rh - b, cw, b, ATLAS_W, ATLAS_H);
        }
        if (ch > 0) {
            gg.blit(ATLAS, x, y + b, u, v + b, b, ch, ATLAS_W, ATLAS_H);
            gg.blit(ATLAS, x + w - b, y + b, u + rw - b, v + b, b, ch, ATLAS_W, ATLAS_H);
        }
        if (cw > 0 && ch > 0) {
            gg.blit(ATLAS, x + b, y + b, u + b, v + b, cw, ch, ATLAS_W, ATLAS_H);
        }
    }

    /** Convenience: draw a 9-slice panel. */
    public static void panel(GuiGraphics gg, int x, int y, int w, int h) {
        blit9(gg, x, y, w, h, PANEL);
    }

    /** Convenience: draw a 9-slice button. */
    public static void button(GuiGraphics gg, int x, int y, int w, int h, boolean hovered, boolean active) {
        Rgn r = active ? (hovered ? BTN_HOVER : BTN) : BTN_DISABLED;
        blit9(gg, x, y, w, h, r);
    }

    /** Convenience: draw a 9-slice input field background. */
    public static void field(GuiGraphics gg, int x, int y, int w, int h) {
        blit9(gg, x, y, w, h, FIELD);
    }

    /** Convenience: draw a 9-slice tab. */
    public static void tab(GuiGraphics gg, int x, int y, int w, int h) {
        blit9(gg, x, y, w, h, TAB);
    }

    public static void slot(GuiGraphics gg, int x, int y) { blit(gg, x, y, SLOT); }
    public static void scroll(GuiGraphics gg, int x, int y) { blit(gg, x, y, SCROLL); }
    public static void toggle(GuiGraphics gg, int x, int y, boolean on) { blit(gg, x, y, on ? TOGGLE_ON : TOGGLE_OFF); }
    public static void check(GuiGraphics gg, int x, int y, boolean on) { blit(gg, x, y, on ? CHECK_ON : CHECK); }

    private P2PGuiTextures() {}
}
