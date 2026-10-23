package com.kalloer1.p2p.harness;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 离线画布：真实像素分辨率（font 坐标 × GS），ARGB src-over 合成，矩形裁剪栈。
 * 背景为不透明黑（游戏里这里是变暗的世界）。
 */
public final class HCanvas {

    public static final int GS = 2; // GUI Scale 2

    public final int fontW, fontH;
    public final int realW, realH;
    private final BufferedImage img;
    private final int[] px;
    private final Deque<int[]> scissors = new ArrayDeque<>(); // font 坐标 {x0,y0,x1,y1}

    public HCanvas(int fontW, int fontH) {
        this.fontW = fontW;
        this.fontH = fontH;
        this.realW = fontW * GS;
        this.realH = fontH * GS;
        this.img = new BufferedImage(realW, realH, BufferedImage.TYPE_INT_ARGB);
        this.px = new int[realW * realH];
        java.util.Arrays.fill(px, 0xFF000000);
    }

    // ==================== 裁剪 ====================

    public void pushScissor(int fx0, int fy0, int fx1, int fy1) {
        int[] cur = scissors.peek();
        if (cur != null) {
            fx0 = Math.max(fx0, cur[0]);
            fy0 = Math.max(fy0, cur[1]);
            fx1 = Math.min(fx1, cur[2]);
            fy1 = Math.min(fy1, cur[3]);
        }
        scissors.push(new int[]{fx0, fy0, fx1, fy1});
    }

    public void popScissor() {
        scissors.pop();
    }

    public boolean containsPoint(int fx, int fy) {
        int[] top = scissors.peek();
        return top == null || (fx >= top[0] && fx < top[2] && fy >= top[1] && fy < top[3]);
    }

    private boolean clipped(int rx, int ry) {
        int[] top = scissors.peek();
        if (top == null) return false;
        int x0 = top[0] * GS, y0 = top[1] * GS, x1 = top[2] * GS, y1 = top[3] * GS;
        return !(rx >= x0 && rx < x1 && ry >= y0 && ry < y1);
    }

    // ==================== 基元 ====================

    /** font 坐标矩形填充（[x1,x2) × [y1,y2)）。 */
    public void rectFont(int fx1, int fy1, int fx2, int fy2, int argb) {
        rect(fx1 * GS, fy1 * GS, fx2 * GS, fy2 * GS, argb);
    }

    /** 真实像素矩形填充。 */
    public void rect(int rx1, int ry1, int rx2, int ry2, int argb) {
        for (int y = Math.max(0, ry1); y < Math.min(realH, ry2); y++) {
            for (int x = Math.max(0, rx1); x < Math.min(realW, rx2); x++) {
                if (clipped(x, y)) continue;
                px[y * realW + x] = over(px[y * realW + x], argb);
            }
        }
    }

    /** 垂直渐变（vanilla fillGradient 语义：colorFrom 在顶部，colorTo 在底部，alpha 也插值）。 */
    public void gradientFont(int fx1, int fy1, int fx2, int fy2, int from, int to) {
        int height = fy2 - fy1;
        for (int i = 0; i < height; i++) {
            float t = height == 1 ? 0f : (float) i / (height - 1);
            int a = (int) lerp((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
            int r = (int) lerp((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, t);
            int g = (int) lerp((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, t);
            int b = (int) lerp(from & 0xFF, to & 0xFF, t);
            rectFont(fx1, fy1 + i, fx2, fy1 + i + 1, (a << 24) | (r << 16) | (g << 8) | b);
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * 带亮度系数的染色混合（字形像素：源为白色位图 × 文本色 × alpha）。
     * size=1 时单像素。
     */
    public void blendTinted(int rx, int ry, int rw, int rh, int argb, int lum) {
        for (int y = ry; y < ry + rh; y++) {
            for (int x = rx; x < rx + rw; x++) {
                if (x < 0 || y < 0 || x >= realW || y >= realH || clipped(x, y)) continue;
                int dst = px[y * realW + x];
                px[y * realW + x] = over(dst, tint(argb, lum));
            }
        }
    }

    private static int tint(int argb, int lum) {
        int a = ((argb >>> 24) * lum / 255) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int over(int dst, int src) {
        int sa = (src >>> 24) & 0xFF;
        if (sa == 255) return src | 0xFF000000;
        if (sa == 0) return dst;
        int dr = (dst >>> 16) & 0xFF, dg = (dst >>> 8) & 0xFF, db = dst & 0xFF;
        int sr = (src >>> 16) & 0xFF, sg = (src >>> 8) & 0xFF, sb = src & 0xFF;
        int da = (dst >>> 24) & 0xFF;
        int oa = sa + da * (255 - sa) / 255;
        int or = (sr * sa + dr * da * (255 - sa) / 255) / Math.max(1, oa);
        int og = (sg * sa + dg * da * (255 - sa) / 255) / Math.max(1, oa);
        int ob = (sb * sa + db * da * (255 - sa) / 255) / Math.max(1, oa);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    // ==================== 输出 ====================

    public void savePng(File file) throws IOException {
        BufferedImage out = new BufferedImage(realW, realH, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < realH; y++) {
            for (int x = 0; x < realW; x++) {
                int v = px[y * realW + x];
                out.setRGB(x, y, v | 0xFF000000);
            }
        }
        ImageIO.write(out, "png", file);
    }

    public BufferedImage image() {
        return img;
    }
}
