package com.kalloer1.p2p.harness;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 离线 GuiGraphics：继承真实 GuiGraphics 以通过类型系统，把全部绘制原语重定向到 {@link HCanvas}。
 * super 构造传 mock Minecraft（getGuiSprites() 返回 null 即可——本类不触碰任何 sprite/buffer 路径）。
 */
public class HGuiGraphics extends GuiGraphics {

    public final HCanvas canvas;
    public final HFont hfont;
    private final Path vanillaAssets;

    public HGuiGraphics(net.minecraft.client.Minecraft minecraft, HCanvas canvas, HFont hfont, Path vanillaAssets) {
        super(minecraft, (MultiBufferSource.BufferSource) null);
        this.canvas = canvas;
        this.hfont = hfont;
        this.vanillaAssets = vanillaAssets;
    }

    // ==================== 工具 ====================

    /** Component/FormattedText → 带格式码的纯文本（样式颜色转 § 码，由 HFont 统一渲染）。 */
    static String flatten(FormattedText text) {
        StringBuilder sb = new StringBuilder();
        text.visit((style, s) -> {
            appendStyle(sb, style);
            sb.append(s);
            return Optional.empty();
        }, Style.EMPTY);
        return sb.toString();
    }

    static String flatten(FormattedCharSequence fcs) {
        StringBuilder sb = new StringBuilder();
        Style[] cur = {Style.EMPTY};
        fcs.accept((idx, style, cp) -> {
            if (!Objects.equals(style, cur[0])) {
                appendStyle(sb, style);
                cur[0] = style;
            }
            sb.appendCodePoint(cp);
            return true;
        });
        return sb.toString();
    }

    private static void appendStyle(StringBuilder sb, Style style) {
        net.minecraft.network.chat.TextColor textColor = style == null ? null : style.getColor();
        if (textColor != null) {
            int rgb = textColor.getValue() & 0xFFFFFF;
            Character code = nearestCode(rgb);
            if (code != null) sb.append('§').append(code.charValue());
        }
    }

    @Nullable
    private static Character nearestCode(int rgb) {
        int[][] palette = {
                {0x000000, 0}, {0x0000AA, 1}, {0x00AA00, 2}, {0x00AAAA, 3}, {0xAA0000, 4}, {0xAA00AA, 5},
                {0xFFAA00, 6}, {0xAAAAAA, 7}, {0x555555, 8}, {0x5555FF, 9}, {0x55FF55, 'a'}, {0x55FFFF, 'b'},
                {0xFF5555, 'c'}, {0xFF55FF, 'd'}, {0xFFFF55, 'e'}, {0xFFFFFF, 'f'}
        };
        for (int[] p : palette) {
            if ((p[0] | 0xFF000000) == (rgb | 0xFF000000)) return (char) p[1];
        }
        return null;
    }

    private int drawFlat(Font font, String s, float x, float y, int color, boolean shadow) {
        float end = hfont.render(canvas, s, x, y, color, shadow);
        return (int) end + (shadow ? 1 : 0);
    }

    // ==================== fill ====================

    @Override
    public void fill(int minX, int minY, int maxX, int maxY, int color) {
        canvas.rectFont(minX, minY, maxX, maxY, color);
    }

    @Override
    public void fill(int minX, int minY, int maxX, int maxY, int z, int color) {
        canvas.rectFont(minX, minY, maxX, maxY, color);
    }

    @Override
    public void fill(net.minecraft.client.renderer.RenderType renderType, int minX, int minY, int maxX, int maxY, int color) {
        canvas.rectFont(minX, minY, maxX, maxY, color);
    }

    @Override
    public void fill(net.minecraft.client.renderer.RenderType renderType, int minX, int minY, int maxX, int maxY, int z, int color) {
        canvas.rectFont(minX, minY, maxX, maxY, color);
    }

    @Override
    public void fillGradient(int x1, int y1, int x2, int y2, int colorFrom, int colorTo) {
        canvas.gradientFont(x1, y1, x2, y2, colorFrom, colorTo);
    }

    @Override
    public void fillGradient(int x1, int y1, int x2, int y2, int z, int colorFrom, int colorTo) {
        canvas.gradientFont(x1, y1, x2, y2, colorFrom, colorTo);
    }

    @Override
    public void fillGradient(net.minecraft.client.renderer.RenderType renderType, int x1, int y1, int x2, int y2, int colorFrom, int colorTo, int z) {
        canvas.gradientFont(x1, y1, x2, y2, colorFrom, colorTo);
    }

    // ==================== 文本 ====================

    @Override
    public int drawString(Font font, @Nullable String text, int x, int y, int color) {
        return drawString(font, text, x, y, color, false);
    }

    @Override
    public int drawString(Font font, @Nullable String text, int x, int y, int color, boolean dropShadow) {
        if (text == null) return x;
        return drawString(font, text, (float) x, (float) y, color, dropShadow);
    }

    @Override
    public int drawString(Font font, @Nullable String text, float x, float y, int color, boolean dropShadow) {
        if (text == null) return (int) x;
        return drawFlat(font, text, x, y, color, dropShadow);
    }

    @Override
    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color) {
        return drawString(font, text, x, y, color, false);
    }

    @Override
    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean dropShadow) {
        return drawFlat(font, flatten(text), x, y, color, dropShadow);
    }

    @Override
    public int drawString(Font font, FormattedCharSequence text, float x, float y, int color, boolean dropShadow) {
        return drawFlat(font, flatten(text), x, y, color, dropShadow);
    }

    @Override
    public int drawString(Font font, Component text, int x, int y, int color) {
        return drawString(font, text, x, y, color, false);
    }

    @Override
    public int drawString(Font font, Component text, int x, int y, int color, boolean dropShadow) {
        return drawFlat(font, flatten(text), x, y, color, dropShadow);
    }

    @Override
    public void drawCenteredString(Font font, Component text, int centerX, int y, int color) {
        int w = hfont.width(flatten(text));
        drawFlat(font, flatten(text), centerX - w / 2.0f, y, color, false);
    }

    @Override
    public void drawCenteredString(Font font, String text, int centerX, int y, int color) {
        int w = hfont.width(text);
        drawFlat(font, text, centerX - w / 2.0f, y, color, false);
    }

    @Override
    public void drawCenteredString(Font font, FormattedCharSequence text, int centerX, int y, int color) {
        String s = flatten(text);
        int w = hfont.width(s);
        drawFlat(font, s, centerX - w / 2.0f, y, color, false);
    }

    // ==================== 裁剪 ====================

    @Override
    public void enableScissor(int minX, int minY, int maxX, int maxY) {
        canvas.pushScissor(minX, minY, maxX, maxY);
    }

    @Override
    public void disableScissor() {
        canvas.popScissor();
    }

    @Override
    public boolean containsPointInScissor(int x, int y) {
        return canvas.containsPoint(x, y);
    }

    // ==================== tooltip（简化版原样式） ====================

    @Override
    public void renderTooltip(Font font, Component text, int mouseX, int mouseY) {
        renderTooltipLines(font, List.of(flatten(text)), mouseX, mouseY);
    }

    @Override
    public void renderTooltip(Font font, List<Component> tooltips, Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> visual, ItemStack stack, int mouseX, int mouseY) {
        renderTooltipLines(font, tooltips.stream().map(HGuiGraphics::flatten).toList(), mouseX, mouseY);
    }

    @Override
    public void renderTooltip(Font font, List<? extends FormattedCharSequence> lines, int mouseX, int mouseY) {
        renderTooltipLines(font, lines.stream().map(HGuiGraphics::flatten).toList(), mouseX, mouseY);
    }

    private void renderTooltipLines(Font font, List<String> lines, int mx, int my) {
        if (lines.isEmpty()) return;
        int w = 0;
        for (String s : lines) w = Math.max(w, hfont.width(s));
        int h = lines.size() * 10 + (lines.size() > 1 ? 2 : 0) - 1;
        int x = Math.min(mx + 12, canvas.fontW - w - 8);
        int y = my;
        canvas.gradientFont(x - 3, y - 4, x + w + 3, y - 3, 0x505000FF, 0x5028007F);
        canvas.gradientFont(x - 3, y + h + 3, x + w + 3, y + h + 4, 0x505000FF, 0x5028007F);
        canvas.gradientFont(x - 4, y - 3, x - 3, y + h + 3, 0x505000FF, 0x5028007F);
        canvas.gradientFont(x + w + 3, y - 3, x + w + 4, y + h + 3, 0x505000FF, 0x5028007F);
        canvas.rectFont(x - 3, y - 3, x + w + 3, y + h + 3, 0xF0100010);
        for (int i = 0; i < lines.size(); i++) {
            hfont.render(canvas, lines.get(i), x, y + i * 10, 0xFFFFFF, true);
        }
    }

    // ==================== 物品图标 ====================

    @Override
    public void renderItem(ItemStack stack, int x, int y) {
        renderItemFlat(stack, x, y);
    }

    @Override
    public void renderItem(ItemStack stack, int x, int y, int seed) {
        renderItemFlat(stack, x, y);
    }

    @Override
    public void renderItem(ItemStack stack, int x, int y, int seed, int guiOffset) {
        renderItemFlat(stack, x, y);
    }

    @Override
    public void renderFakeItem(ItemStack stack, int x, int y) {
        renderItemFlat(stack, x, y);
    }

    private void renderItemFlat(ItemStack stack, int fx, int fy) {
        if (stack == null || stack.isEmpty()) return;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        BufferedImage tex = loadItemTexture(id.getNamespace() + "/textures/item/" + id.getPath() + ".png");
        int px0 = fx * HCanvas.GS, py0 = fy * HCanvas.GS, size = 16 * HCanvas.GS;
        if (tex != null) {
            for (int j = 0; j < size; j++) {
                int sj = j * tex.getHeight() / size;
                for (int i = 0; i < size; i++) {
                    int si = i * tex.getWidth() / size;
                    int argb = tex.getRGB(si, sj);
                    if (((argb >>> 24) & 0xFF) == 0) continue;
                    canvas.blendTinted(px0 + i, py0 + j, 1, 1, argb, 255);
                }
            }
        } else {
            // 方块类物品无扁平贴图：占位槽井 + 中性色块（差异清单记录）
            canvas.rectFont(fx, fy, fx + 16, fy + 16, 0xFFEBEBF0);
            canvas.rectFont(fx, fy, fx + 16, fy + 1, 0xFFD1D1D6);
            canvas.rectFont(fx + 3, fy + 3, fx + 13, fy + 13, 0xFFB8B8C0);
            HLog.once("item placeholder: " + id);
        }
    }

    @Nullable
    private BufferedImage loadItemTexture(String rel) {
        try {
            Path p = vanillaAssets.resolve(rel);
            if (Files.exists(p)) return ImageIO.read(p.toFile());
        } catch (Exception e) {
            HLog.warn("item texture load failed: " + rel);
        }
        return null;
    }

    // ==================== 尺寸 ====================

    @Override
    public int guiWidth() {
        return canvas.fontW;
    }

    @Override
    public int guiHeight() {
        return canvas.fontH;
    }
}
