package com.kalloer1.p2p.harness;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 离线字体引擎：加载 vanilla 字体 provider 链（bitmap / space / unihex），按原版规则计算
 * advance 与裁剪列，把字形位图栅格化到 {@link HCanvas}。
 *
 * <p>advance/几何公式逐一复刻自反编译源码：
 * <ul>
 *   <li>BitmapProvider：advance = round(0.5 + trimmedWidth*scale) + 1，oversample = 1/scale，
 *       baked 顶 = 7 - ascent（SheetGlyphInfo.getTop() = 7 - bearingTop）；</li>
 *   <li>UnihexProvider：宽 = right-left+1（32 位行数据的前导零/尾零列），advance = width/2 + 1，
 *       shadowOffset = 0.5，oversample = 2（字形 16px 源渲染为 8px 字面）；</li>
 *   <li>space provider：仅 advance。</li>
 * </ul>
 */
public final class HFont {

    public abstract static class Glyph {
        /** 字面步进（font 像素）。 */
        public abstract float advance();

        /** 阴影偏移（font 像素）：bitmap 1.0，unihex 0.5。 */
        public float shadowOffset() {
            return 1.0f;
        }

        /** 把字形画到画布。px,py 为字形盒左上角（真实像素），cellPx 为每个源像素的真实边长。 */
        abstract void blit(HCanvas canvas, float px, float py, float cellPx, int argb);
    }

    static final class BitmapGlyph extends Glyph {
        final BufferedImage page;
        final int offX, offY, cellW, cellH;
        final float scale;
        final int ascent;
        final float advance;

        BitmapGlyph(BufferedImage page, int offX, int offY, int cellW, int cellH, float scale, int ascent) {
            this.page = page;
            this.offX = offX;
            this.offY = offY;
            this.cellW = cellW;
            this.cellH = cellH;
            this.scale = scale;
            this.ascent = ascent;
            this.advance = (int) (0.5f + trimmedWidth() * scale) + 1; // vanilla 截断语义
        }

        int trimmedWidth() {
            for (int i = cellW - 1; i >= 0; i--) {
                for (int k = 0; k < cellH; k++) {
                    if (((page.getRGB(offX + i, offY + k) >>> 24) & 0xFF) != 0) return i + 1;
                }
            }
            return 0;
        }

        @Override
        public float advance() {
            return advance;
        }

        @Override
        void blit(HCanvas canvas, float px, float py, float cellPx, int argb) {
            float y0 = py + (7 - ascent) * cellPx;
            for (int j = 0; j < cellH; j++) {
                for (int i = 0; i < cellW; i++) {
                    // vanilla getLuminanceOrAlpha：字体页用 alpha 通道（白色 + alpha 的强度贴图）
                    int lum = (page.getRGB(offX + i, offY + j) >>> 24) & 0xFF;
                    if (lum == 0) continue;
                    int rx = Math.round(px + i * cellPx);
                    int ry = Math.round(y0 + j * cellPx);
                    int size = Math.max(1, Math.round(cellPx));
                    canvas.blendTinted(rx, ry, size, size, argb, lum);
                }
            }
        }
    }

    static final class UnihexGlyph extends Glyph {
        final int[] lines;
        final int left, right;

        UnihexGlyph(int[] lines, int left, int right) {
            this.lines = lines;
            this.left = left;
            this.right = right;
        }

        public int width() {
            return right - left + 1;
        }

        @Override
        public float advance() {
            return width() / 2.0f + 1;
        }

        @Override
        public float shadowOffset() {
            return 0.5f;
        }

        @Override
        void blit(HCanvas canvas, float px, float py, float cellPx, int argb) {
            for (int r = 0; r < 16; r++) {
                int line = lines[r];
                for (int c = 0; c < width(); c++) {
                    int bit = (line >> (31 - left - c)) & 1;
                    if (bit != 0) {
                        canvas.blendTinted(Math.round(px + c * cellPx), Math.round(py + r * cellPx),
                                Math.max(1, Math.round(cellPx)), Math.max(1, Math.round(cellPx)), argb, 255);
                    }
                }
            }
        }
    }

    static final class SpaceGlyph extends Glyph {
        final float advance;

        SpaceGlyph(float advance) {
            this.advance = advance;
        }

        @Override
        public float advance() {
            return advance;
        }

        @Override
        void blit(HCanvas canvas, float px, float py, float cellPx, int argb) {
        }
    }

    static final class BlankGlyph extends Glyph {
        static final BlankGlyph INSTANCE = new BlankGlyph();

        @Override
        public float advance() {
            return 6;
        }

        @Override
        void blit(HCanvas canvas, float px, float py, float cellPx, int argb) {
        }
    }

    /** 按声明顺序排列的 provider 字形表；先声明者优先。 */
    private final List<Map<Integer, Glyph>> providers = new ArrayList<>();
    private final Path assetsDir;

    public HFont(Path assetsDir) {
        this.assetsDir = assetsDir;
    }

    public void loadDefaultFont() throws IOException {
        loadFontJson(assetsDir.resolve("minecraft/font/default.json"));
    }

    private void loadFontJson(Path json) throws IOException {
        JsonObject root;
        try (Reader r = Files.newBufferedReader(json, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(r).getAsJsonObject();
        }
        loadProviders(root.getAsJsonArray("providers"));
    }

    private void loadProviders(JsonArray providers) throws IOException {
        for (JsonElement el : providers) {
            JsonObject p = el.getAsJsonObject();
            String type = p.get("type").getAsString();
            switch (type) {
                case "reference" -> {
                    String id = p.get("id").getAsString();
                    // vanilla FontManager 语义：reference id 解析到 assets/<ns>/font/<path>.json
                    String ns = id.substring(0, id.indexOf(':'));
                    String path = id.substring(id.indexOf(':') + 1);
                    loadFontJson(assetsDir.resolve(ns).resolve("font").resolve(path + ".json"));
                }
                case "bitmap" -> loadBitmap(p);
                case "space" -> loadSpace(p);
                case "unihex" -> loadUnihex(p);
                case "legacy_unicode", "true_type", "ttf" -> HLog.warn("skip unsupported provider type: " + type);
                default -> HLog.warn("unknown provider type: " + type);
            }
        }
    }

    /** id 形如 ns:path；prefix 加在 path 段前面。 */
    private Path resolveAssetWithPrefix(String id, String prefix) {
        String ns = id.substring(0, id.indexOf(':'));
        String path = id.substring(id.indexOf(':') + 1);
        return assetsDir.resolve(ns).resolve(prefix).resolve(path);
    }

    private Path resolveAsset(String id) {
        String ns = id.substring(0, id.indexOf(':'));
        String path = id.substring(id.indexOf(':') + 1);
        return assetsDir.resolve(ns).resolve(path);
    }

    private void loadBitmap(JsonObject p) throws IOException {
        // vanilla BitmapProvider 语义：file.withPrefix("textures/") → assets/<ns>/textures/font/xx.png
        String fileId = p.get("file").getAsString();
        Path file = resolveAssetWithPrefix(fileId, "textures/");
        BufferedImage img = ImageIO.read(file.toFile());
        int height = p.has("height") ? p.get("height").getAsInt() : 8;
        int ascent = p.has("ascent") ? p.get("ascent").getAsInt() : 7;
        JsonArray rows = p.getAsJsonArray("chars");
        int cellH = img.getHeight() / rows.size();
        int cellW = img.getWidth() / rows.get(0).getAsString().codePointCount(0, rows.get(0).getAsString().length());
        float scale = (float) height / cellH;
        Map<Integer, Glyph> map = new HashMap<>();
        for (int r = 0; r < rows.size(); r++) {
            String row = rows.get(r).getAsString();
            int col = 0;
            for (int cp : row.codePoints().toArray()) {
                if (cp != 0) {
                    map.put(cp, new BitmapGlyph(img, col * cellW, r * cellH, cellW, cellH, scale, ascent));
                }
                col++;
            }
        }
        providers.add(map);
    }

    private void loadSpace(JsonObject p) {
        Map<Integer, Glyph> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : p.getAsJsonObject("advances").entrySet()) {
            int cp = e.getKey().codePointAt(0);
            map.put(cp, new SpaceGlyph(e.getValue().getAsFloat()));
        }
        providers.add(map);
    }

    private void loadUnihex(JsonObject p) throws IOException {
        Path zipPath = resolveAsset(p.get("hex_file").getAsString());
        Map<Integer, int[]> raw = new HashMap<>(); // codepoint -> 16 行（左对齐 32 位）
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.getName().endsWith(".hex")) continue;
                readHex(new InputStreamReader(zip, StandardCharsets.ISO_8859_1), raw);
            }
        }
        Map<Integer, Glyph> map = new HashMap<>();
        // size_overrides 优先固定左右列（CJK 全宽）
        for (JsonElement el : p.getAsJsonArray("size_overrides")) {
            JsonObject o = el.getAsJsonObject();
            int from = o.get("from").getAsString().codePointAt(0);
            int to = o.get("to").getAsString().codePointAt(0);
            int left = o.get("left").getAsInt();
            int right = o.get("right").getAsInt();
            for (int cp = from; cp <= to; cp++) {
                int[] lines = raw.remove(cp);
                if (lines != null) map.put(cp, new UnihexGlyph(lines, left, right));
            }
        }
        for (Map.Entry<Integer, int[]> e : raw.entrySet()) {
            int mask = 0;
            for (int line : e.getValue()) mask |= line;
            int left, right;
            if (mask == 0) {
                left = 0;
                right = 31;
            } else {
                left = Integer.numberOfLeadingZeros(mask);
                right = 31 - Integer.numberOfTrailingZeros(mask);
            }
            map.put(e.getKey(), new UnihexGlyph(e.getValue(), left, right));
        }
        providers.add(map);
    }

    private static void readHex(Reader reader, Map<Integer, int[]> out) throws IOException {
        StringBuilder code = new StringBuilder();
        StringBuilder data = new StringBuilder();
        int c;
        boolean inCode = true;
        while ((c = reader.read()) != -1) {
            if (inCode) {
                if (c == ':') inCode = false;
                else code.append((char) c);
            } else {
                if (c == '\n' || c == '\r') {
                    if (data.length() > 0) {
                        int cp = Integer.parseInt(code.toString(), 16);
                        int[] lines = decodeRows(data.toString());
                        if (lines != null) out.put(cp, lines);
                    }
                    code.setLength(0);
                    data.setLength(0);
                    inCode = true;
                } else {
                    data.append((char) c);
                }
            }
        }
    }

    private static int[] decodeRows(String hex) {
        int[] lines = new int[16];
        switch (hex.length()) {
            case 32 -> { // 8-bit rows
                for (int r = 0; r < 16; r++) {
                    lines[r] = (Integer.parseInt(hex.substring(r * 2, r * 2 + 2), 16)) << 24;
                }
            }
            case 64 -> { // 16-bit rows
                for (int r = 0; r < 16; r++) {
                    lines[r] = Integer.parseInt(hex.substring(r * 4, r * 4 + 4), 16) << 16;
                }
            }
            case 96 -> { // 24-bit rows
                for (int r = 0; r < 16; r++) {
                    lines[r] = Integer.parseInt(hex.substring(r * 6, r * 6 + 6), 16) << 8;
                }
            }
            case 128 -> { // 32-bit rows
                for (int r = 0; r < 16; r++) {
                    lines[r] = (int) Long.parseLong(hex.substring(r * 8, r * 8 + 8), 16);
                }
            }
            default -> {
                return null;
            }
        }
        return lines;
    }

    public Glyph glyph(int cp) {
        for (Map<Integer, Glyph> map : providers) {
            Glyph g = map.get(cp);
            if (g != null) return g;
        }
        return BlankGlyph.INSTANCE;
    }

    // ==================== 测量与绘制 ====================

    /** 逐字形步进测量；§ 格式码不占宽度。 */
    public float measure(String s) {
        float x = 0;
        for (String seg : splitFormatting(s).values()) {
            for (int cp : seg.codePoints().toArray()) {
                x += glyph(cp).advance();
            }
        }
        return x;
    }

    public int width(String s) {
        return (int) Math.ceil(measure(s));
    }

    /** 按 vanilla 语义截断：tail=false 取前段，tail=true 取溢出段。 */
    public String plainSubstrByWidth(String s, int maxWidth, boolean tail) {
        float acc = 0;
        for (int i = 0; i < s.length(); i += s.offsetByCodePoints(i, 1)) {
            int cp = s.codePointAt(i);
            float adv = glyph(cp).advance();
            if (acc + adv > maxWidth) {
                return tail ? s.substring(i) : s.substring(0, i);
            }
            acc += adv;
        }
        return tail ? "" : s;
    }

    public String plainSubstrByWidth(String s, int maxWidth) {
        return plainSubstrByWidth(s, maxWidth, false);
    }

    /**
     * 渲染一行字符串。格式码段作为整体绘制：每段一个颜色（style 颜色或 § 码），阴影先画。
     * 返回结束 x（font 像素，float）。
     */
    public float render(HCanvas canvas, String s, float x, float y, int baseColor, boolean shadow) {
        // vanilla：整串先画阴影（暗色，逐字形 shadowOffset 偏移），再画本体
        if (shadow) {
            for (FormattedSeg seg : splitFormatting(s).segs) {
                float cx = x;
                for (int cp : seg.text.codePoints().toArray()) {
                    Glyph g = glyph(cp);
                    int dim = dimColor(seg.argb(baseColor));
                    g.blit(canvas, cx * HCanvas.GS + g.shadowOffset() * HCanvas.GS,
                            y * HCanvas.GS + g.shadowOffset() * HCanvas.GS, cellPx(g), dim);
                    cx += g.advance();
                }
            }
        }
        for (FormattedSeg seg : splitFormatting(s).segs) {
            float cx = x;
            for (int cp : seg.text.codePoints().toArray()) {
                Glyph g = glyph(cp);
                g.blit(canvas, cx * HCanvas.GS, y * HCanvas.GS, cellPx(g), seg.argb(baseColor));
                cx += g.advance();
            }
        }
        return x + measure(s);
    }

    private float endX(String s, float x) {
        return x + measure(s);
    }

    private float cellPx(Glyph g) {
        if (g instanceof UnihexGlyph) return HCanvas.GS / 2.0f;
        if (g instanceof BitmapGlyph b) return HCanvas.GS * b.scale;
        return HCanvas.GS;
    }

    /** vanilla 阴影色 = (rgb & 0xFCFCFC) >> 2，alpha 保留。 */
    static int dimColor(int argb) {
        int a = argb & 0xFF000000;
        int rgb = argb & 0x00FFFFFF;
        return a | ((rgb & 0xFCFCFC) >> 2);
    }

    // ==================== § 格式码 ====================

    /** 一段同格式文本。formatChar = 0 表示无格式。 */
    record FormattedSeg(String text, int formatChar) {
        int argb(int base) {
            int color = switch (formatChar) {
                case '0' -> 0x000000;
                case '1' -> 0x0000AA;
                case '2' -> 0x00AA00;
                case '3' -> 0x00AAAA;
                case '4' -> 0xAA0000;
                case '5' -> 0xAA00AA;
                case '6' -> 0xFFAA00;
                case '7' -> 0xAAAAAA;
                case '8' -> 0x555555;
                case '9' -> 0x5555FF;
                case 'a' -> 0x55FF55;
                case 'b' -> 0x55FFFF;
                case 'c' -> 0xFF5555;
                case 'd' -> 0xFF55FF;
                case 'e' -> 0xFFFF55;
                case 'f' -> 0xFFFFFF;
                default -> -1;
            };
            if (color < 0) return base;
            return (base & 0xFF000000) | color;
        }
    }

    static final class Segs {
        final List<FormattedSeg> segs = new ArrayList<>();

        java.util.Collection<String> values() {
            List<String> out = new ArrayList<>();
            for (FormattedSeg s : segs) out.add(s.text);
            return out;
        }
    }

    static Segs splitFormatting(String s) {
        Segs out = new Segs();
        StringBuilder cur = new StringBuilder();
        int fmt = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '§' && i + 1 < s.length()) {
                char code = Character.toLowerCase(s.charAt(++i));
                if (cur.length() > 0) {
                    out.segs.add(new FormattedSeg(cur.toString(), fmt));
                    cur.setLength(0);
                }
                fmt = (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') ? code : (code == 'r' ? 0 : fmt);
            } else {
                cur.append(ch);
            }
        }
        if (cur.length() > 0) out.segs.add(new FormattedSeg(cur.toString(), fmt));
        return out;
    }
}
