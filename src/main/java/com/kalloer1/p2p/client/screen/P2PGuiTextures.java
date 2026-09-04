package com.kalloer1.p2p.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Runtime-drawn chrome for every P2P GUI.
 *
 * There is no custom texture atlas any more: panels, buttons, fields, tabs, slots, toggles and
 * checkboxes are composed from flat fills plus 1px bevels. That keeps the mod free of GUI image
 * assets and immune to atlas-size / resource-pack mismatches.
 *
 * The {@link Rgn} constants are kept as pure geometry (default widget sizes) so existing call
 * sites keep compiling; their u/v are unused.
 */
public final class P2PGuiTextures {

    /** Widget geometry. {@code border > 0} means the widget stretches to any size. */
    public static final class Rgn {
        public final int u, v, w, h, border;
        public Rgn(int u, int v, int w, int h, int border) {
            this.u = u; this.v = v; this.w = w; this.h = h; this.border = border;
        }
        public boolean nineSlice() { return border > 0; }
    }

    public static final Rgn PANEL        = new Rgn(0, 0, 20, 20, 4);
    public static final Rgn FIELD        = new Rgn(0, 0, 20, 20, 4);
    public static final Rgn BTN          = new Rgn(0, 0, 20, 20, 4);
    public static final Rgn BTN_HOVER    = new Rgn(0, 0, 20, 20, 4);
    public static final Rgn BTN_DISABLED = new Rgn(0, 0, 20, 20, 4);
    public static final Rgn TAB          = new Rgn(0, 0, 20, 20, 4);
    public static final Rgn SLOT         = new Rgn(0, 0, 18, 18, 0);
    public static final Rgn SCROLL       = new Rgn(0, 0, 6, 16, 0);
    public static final Rgn TOGGLE_ON    = new Rgn(0, 0, 44, 20, 0);
    public static final Rgn TOGGLE_OFF   = new Rgn(0, 0, 44, 20, 0);
    public static final Rgn CHECK        = new Rgn(0, 0, 14, 14, 0);
    public static final Rgn CHECK_ON     = new Rgn(0, 0, 14, 14, 0);

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

    // ---- fills that used to come from the atlas ----
    public static final int PANEL_BG  = 0xFF23272E;  // window body
    public static final int FIELD_BG  = 0xFF1A1D22;  // recessed input / slot well
    public static final int BTN_HI    = 0xFF4A5364;  // hovered button fill
    public static final int BTN_OFF   = 0xFF2A2F37;  // disabled button fill
    public static final int BTN_SEL   = 0xFF3D5C8C;  // selected button / active tab fill
    public static final int BTN_SEL_HI= 0xFF4E74AD;  // selected + hovered
    public static final int ACCENT     = 0xFF8FB8F0; // selection accent (tab underline)
    public static final int KNOB      = 0xFF6B7482;  // scrollbar / toggle knob

    /** Raised bevel: light top/left, dark bottom/right, over the given fill. */
    private static void raised(GuiGraphics gg, int x, int y, int w, int h, int fill) {
        if (w <= 0 || h <= 0) return;
        gg.fill(x, y, x + w, y + h, fill);
        gg.fill(x, y, x + w, y + 1, FAUX_HI);
        gg.fill(x, y, x + 1, y + h, FAUX_HI);
        gg.fill(x, y + h - 1, x + w, y + h, FAUX_SH);
        gg.fill(x + w - 1, y, x + w, y + h, FAUX_SH);
    }

    /** Recessed bevel: dark top/left, light bottom/right, over the given fill. */
    private static void inset(GuiGraphics gg, int x, int y, int w, int h, int fill) {
        if (w <= 0 || h <= 0) return;
        gg.fill(x, y, x + w, y + h, fill);
        gg.fill(x, y, x + w, y + 1, FAUX_SH);
        gg.fill(x, y, x + 1, y + h, FAUX_SH);
        gg.fill(x, y + h - 1, x + w, y + h, FAUX_HI);
        gg.fill(x + w - 1, y, x + w, y + h, FAUX_HI);
    }

    /** Draw a widget at its natural size. */
    public static void blit(GuiGraphics gg, int x, int y, Rgn r) {
        blit9(gg, x, y, r.w, r.h, r);
    }

    /** Stretchable widget draw; kept named blit9 so existing call sites are unchanged. */
    public static void blit9(GuiGraphics gg, int x, int y, int w, int h, Rgn r) {
        if (r == FIELD || r == SLOT) inset(gg, x, y, w, h, FIELD_BG);
        else if (r == PANEL) raised(gg, x, y, w, h, PANEL_BG);
        else if (r == TAB) raised(gg, x, y, w, h, TAB_BG);
        else if (r == BTN_HOVER) raised(gg, x, y, w, h, BTN_HI);
        else if (r == BTN_DISABLED) raised(gg, x, y, w, h, BTN_OFF);
        else raised(gg, x, y, w, h, FAUX_BTN);
    }

    /** Convenience: draw the main window panel. */
    public static void panel(GuiGraphics gg, int x, int y, int w, int h) {
        raised(gg, x, y, w, h, PANEL_BG);
    }

    /** Convenience: draw a button in the state given. */
    public static void button(GuiGraphics gg, int x, int y, int w, int h, boolean hovered, boolean active) {
        button(gg, x, y, w, h, hovered, active, false);
    }

    /**
     * Button with an explicit selected state, for tabs and grouped toggles. The three states are kept
     * clearly distinct: unselected = dark fill, hovered = lighter fill + thin accent line,
     * selected = blue fill + a 2px accent underline (so the state is readable without relying on hue).
     */
    public static void button(GuiGraphics gg, int x, int y, int w, int h, boolean hovered, boolean active, boolean selected) {
        if (!active) {
            raised(gg, x, y, w, h, BTN_OFF);
            return;
        }
        if (selected) {
            raised(gg, x, y, w, h, hovered ? BTN_SEL_HI : BTN_SEL);
            if (h > 4) gg.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, ACCENT);
        } else {
            raised(gg, x, y, w, h, hovered ? BTN_HI : FAUX_BTN);
            if (hovered && h > 3) gg.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, ACCENT);
        }
    }

    /** Convenience: draw an input field background. */
    public static void field(GuiGraphics gg, int x, int y, int w, int h) {
        inset(gg, x, y, w, h, FIELD_BG);
    }

    /** Convenience: draw a tab / header strip. */
    public static void tab(GuiGraphics gg, int x, int y, int w, int h) {
        raised(gg, x, y, w, h, TAB_BG);
    }

    public static void slot(GuiGraphics gg, int x, int y) {
        inset(gg, x, y, SLOT.w, SLOT.h, FIELD_BG);
    }

    public static void scroll(GuiGraphics gg, int x, int y) {
        raised(gg, x, y, SCROLL.w, SCROLL.h, KNOB);
    }

    public static void toggle(GuiGraphics gg, int x, int y, boolean on) {
        int w = TOGGLE_ON.w, h = TOGGLE_ON.h;
        inset(gg, x, y, w, h, on ? 0xFF2E4A34 : FIELD_BG);
        raised(gg, on ? x + w - h : x, y, h, h, on ? OK_GREEN : KNOB);
    }

    public static void check(GuiGraphics gg, int x, int y, boolean on) {
        int w = CHECK.w, h = CHECK.h;
        inset(gg, x, y, w, h, FIELD_BG);
        if (on) gg.fill(x + 3, y + 3, x + w - 3, y + h - 3, OK_GREEN);
    }

    private P2PGuiTextures() {}
}
