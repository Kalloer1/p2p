package com.kalloer1.p2p.harness;

import java.nio.file.Paths;

public final class FontDebug {
    public static void main(String[] args) throws Exception {
        var assets = Paths.get(System.getProperty("guirender.assets", "build/gui-render/va/assets"));
        HFont f = new HFont(assets);
        f.loadDefaultFont();
        for (String s : new String[]{"P", "2", "A", "!", "频", " ", "0"}) {
            var g = f.glyph(s.codePointAt(0));
            String kind = g.getClass().getSimpleName();
            float adv = g.advance();
            String extra = "";
            if (g instanceof HFont.BitmapGlyph b) {
                extra = " cell=" + b.cellW + "x" + b.cellH + " scale=" + b.scale + " trimW=" + b.trimmedWidth() + " ascent=" + b.ascent;
            } else if (g instanceof HFont.UnihexGlyph u) {
                extra = " w=" + u.width() + " left=" + u.left + " right=" + u.right;
            }
            HLog.info("'" + s + "' -> " + kind + " adv=" + adv + extra);
        }
        HCanvas c = new HCanvas(200, 24);
        for (int y = 0; y < 24 * HCanvas.GS; y++)
            for (int x = 0; x < 200 * HCanvas.GS; x++) c.rect(x, y, x + 1, y + 1, 0xFFF2F2F7);
        f.render(c, "P2P 频道 123 abc", 2, 5, 0xFF1C1C1E, true);
        f.render(c, "P2P 频道管理", 2, 16, 0xFF1C1C1E, true);
        c.savePng(Paths.get(System.getProperty("guirender.out", "build/gui-render")).resolve("fontdebug.png").toFile());
        HLog.info("saved fontdebug.png");
    }
}
