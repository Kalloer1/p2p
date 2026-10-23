package com.kalloer1.p2p.client.screen;

import com.kalloer1.p2p.client.screen.theme.DarkPalette;
import com.kalloer1.p2p.client.screen.theme.LightPalette;
import com.kalloer1.p2p.client.screen.theme.ThemeMode;
import com.kalloer1.p2p.client.screen.theme.ThemePalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * iOS 风格主题层：色彩 token、形态 token 与组件绘制原语。本体与附属共用的公开 API，
 * 取代原 {@code P2PGuiTextures} 的 bevel 三件套（raised / inset / button 已全部退役）。
 *
 * <p>所有绘制都是即时模式的一次性 fill，无纹理图集、无投影、无动效；圆角由
 * {@link #drawRoundRect} 扫描线生成。规格见 .grill-research/ios-ui-spec.md §1–§3。
 *
 * <p>支持动态多主题切换（Light / Dark），色彩 token 由当前活动的 {@link ThemePalette} 驱动。
 */
public final class P2PTheme {

    private static ThemePalette activePalette = LightPalette.INSTANCE;

    // ==================== §1 色彩 token（ARGB） ====================
    // 注：为保证已有代码调用点无感知兼容且能动态换肤，色彩 Token 为非 final 静态变量，
    // 在主题切换时由 activePalette 全量同步赋值。
    /** 全屏遮罩。 */
    public static int BG_OVERLAY = 0x80000000;
    /** 面板底色（systemGroupedBackground）。 */
    public static int BG_GROUP = 0xFFF2F2F7;
    /** 卡片 / 列表 / 工具条底。 */
    public static int CARD = 0xFFFFFFFF;
    /** 选中行 / 悬停行。 */
    public static int CARD_SEL = 0xFFE9E9EE;
    /** 1px 分隔线。 */
    public static int SEPARATOR = 0xFFD1D1D6;
    /** 标题、值。 */
    public static int TEXT_PRIMARY = 0xFF1C1C1E;
    /** 标签、说明。 */
    public static int TEXT_SECOND = 0xFF8E8E93;
    /** 占位符、禁用。 */
    public static int TEXT_TERT = 0xFFAEAEB2;
    /** 主操作、OUT 徽牌、链接钮、选中页签。 */
    public static int TINT = 0xFF007AFF;
    /** 删除钮/×、IN 徽牌、错误。 */
    public static int DANGER = 0xFFFF3B30;
    /** toggle ON、已连接状态点。 */
    public static int SUCCESS = 0xFF34C759;
    /** 应力/转速数值、注意状态（旧金字全部映射到此）。 */
    public static int WARNING = 0xFFFF9500;
    /** 搜索框、输入框底。 */
    public static int FIELD_BG = 0xFFE5E5EA;
    /** 槽位井底（比 FIELD_BG 更浅一档）。 */
    public static int SLOT_WELL = 0xFFEBEBF0;

    /** 末地维度紫：维度 pill 只用 §1 的四个语义色，其余三色直接取 token。 */
    public static int DIM_END = 0xFFAF52DE;

    private static int TINT_DARK = 0xFF0062CC;
    private static int DANGER_DARK = 0xFFCC2E26;

    static {
        applyPalette(activePalette);
    }

    /** 切换主题模式。 */
    public static void setTheme(ThemeMode mode) {
        if (mode == ThemeMode.DARK) {
            setPalette(DarkPalette.INSTANCE);
        } else {
            setPalette(LightPalette.INSTANCE);
        }
    }

    /** 设置自定义调色板。 */
    public static void setPalette(ThemePalette palette) {
        if (palette == null) palette = LightPalette.INSTANCE;
        activePalette = palette;
        applyPalette(palette);
    }

    private static void applyPalette(ThemePalette p) {
        BG_OVERLAY = p.bgOverlay();
        BG_GROUP = p.bgGroup();
        CARD = p.card();
        CARD_SEL = p.cardSel();
        SEPARATOR = p.separator();
        TEXT_PRIMARY = p.textPrimary();
        TEXT_SECOND = p.textSecond();
        TEXT_TERT = p.textTert();
        TINT = p.tint();
        TINT_DARK = p.tintDark();
        DANGER = p.danger();
        DANGER_DARK = p.dangerDark();
        SUCCESS = p.success();
        WARNING = p.warning();
        FIELD_BG = p.fieldBg();
        SLOT_WELL = p.slotWell();
        DIM_END = p.dimEnd();
    }

    public static ThemePalette getPalette() {
        return activePalette;
    }

    public static ThemeMode getMode() {
        return activePalette.mode();
    }

    public static boolean isDark() {
        return activePalette.mode() == ThemeMode.DARK;
    }

    // ==================== §2 形态 token ====================
    public static final int R_CARD = 4;
    public static final int R_SLOT = 2;
    public static final int R_DIALOG = 6;
    /** 8pt 网格：面板内边距 / 卡片间距 / 卡片内边距 / 行内左右。 */
    public static final int PAD = 8;
    /** 列表行、端点行高。 */
    public static final int ROW_H = 18;
    /** 工具条高。 */
    public static final int TOOLBAR_H = 20;
    /** 导航条高。 */
    public static final int NAV_H = 20;
    /** pill / 搜索框高。 */
    public static final int PILL_H = 14;
    /** iOS toggle 24x14，滑块 12x12。 */
    public static final int TOGGLE_W = 24, TOGGLE_H = 14, TOGGLE_KNOB = 12;
    /** IN/OUT 徽牌 22x12。 */
    public static final int BADGE_W = 22, BADGE_H = 12;
    /** 圆形删除×与 stepper 圆钮同为 14x14。 */
    public static final int CIRCLE = 14;
    /** 槽位井 16x16（槽距仍为 18）。 */
    public static final int WELL = 16;

    // ==================== 基础形状 ====================
    /**
     * 扫描线圆角矩形：四角各按圆弧逐行裁切，中间三段整块 fill，因此一个圆角矩形只有
     * {@code 3 + 2r} 次 fill，而不是每行一次。
     */
    public static void drawRoundRect(GuiGraphics gg, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            gg.fill(x, y, x + w, y + h, color);
            return;
        }
        gg.fill(x + r, y, x + w - r, y + h, color);
        gg.fill(x, y + r, x + r, y + h - r, color);
        gg.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int dy = r - 1 - i;
            int inset = r - (int) Math.floor(Math.sqrt((double) r * r - (double) dy * dy));
            int x0 = x + inset, x1 = x + w - inset;
            if (x1 <= x0) continue;
            gg.fill(x0, y + i, x1, y + i + 1, color);
            gg.fill(x0, y + h - 1 - i, x1, y + h - i, color);
        }
    }

    /** 全圆角（半径 = 高/2）：pill、徽牌、搜索框、圆形钮都走这里。 */
    public static void drawPill(GuiGraphics gg, int x, int y, int w, int h, int color) {
        drawRoundRect(gg, x, y, w, h, h / 2, color);
    }

    /** 白色/深色卡片。 */
    public static void card(GuiGraphics gg, int x, int y, int w, int h) {
        drawRoundRect(gg, x, y, w, h, R_CARD, CARD);
    }

    /** 卡中卡：底色（分组底色），用于白卡/深灰卡内部的分区底板。 */
    public static void plate(GuiGraphics gg, int x, int y, int w, int h) {
        drawRoundRect(gg, x, y, w, h, R_CARD, BG_GROUP);
    }

    /** 1px 分隔线，左右各缩进 8（规格 §2）。 */
    public static void separator(GuiGraphics gg, int x, int y, int w) {
        if (w <= PAD * 2) return;
        gg.fill(x + PAD, y, x + w - PAD, y + 1, SEPARATOR);
    }

    /** 输入框 / 搜索框底：FIELD_BG 全圆角。 */
    public static void field(GuiGraphics gg, int x, int y, int w, int h) {
        drawPill(gg, x, y, w, h, FIELD_BG);
    }

    /** 搜索框：FIELD_BG 全圆角 + 左侧简化放大镜（圆环 + 斜柄）。 */
    public static void searchField(GuiGraphics gg, int x, int y, int w, int h) {
        field(gg, x, y, w, h);
        int gx = x + 5, gy = y + h / 2 - 2;
        gg.fill(gx, gy, gx + 4, gy + 1, TEXT_TERT);
        gg.fill(gx, gy + 3, gx + 4, gy + 4, TEXT_TERT);
        gg.fill(gx, gy + 1, gx + 4, gy + 4, TEXT_TERT);
        gg.fill(gx + 3, gy, gx + 4, gy + 4, TEXT_TERT);
        gg.fill(gx + 4, gy + 3, gx + 5, gy + 4, TEXT_TERT);
        gg.fill(gx + 5, gy + 4, gx + 6, gy + 5, TEXT_TERT);
    }

    /** 槽位井：16x16 圆角 2 + 1px 顶暗缘表达内陷。 */
    public static void slotWell(GuiGraphics gg, int x, int y) {
        drawRoundRect(gg, x, y, WELL, WELL, R_SLOT, SLOT_WELL);
        gg.fill(x + R_SLOT, y, x + WELL - R_SLOT, y + 1, SEPARATOR);
    }

    // ==================== 文字 ====================
    /**
     * 标题：单次绘制 + 原版阴影（spec v1.1 修订：汉字位图字体多次叠绘不合并、产生重影，
     * 双向偏移模拟加粗的画法已废除，签名保持不变所以调用点零改动）。
     */
    public static void drawStringBold(GuiGraphics gg, Font font, Component text, int x, int y, int color) {
        gg.drawString(font, text, x, y, color, true);
    }

    /** 标题居中加粗。 */
    public static void drawCenteredBold(GuiGraphics gg, Font font, Component text, int x, int y, int w, int color) {
        drawStringBold(gg, font, text, x + (w - font.width(text)) / 2, y, color);
    }

    public static void drawCentered(GuiGraphics gg, Font font, Component text, int x, int y, int w, int color) {
        gg.drawString(font, text, x + (w - font.width(text)) / 2, y, color, false);
    }

    // ==================== 组件 ====================
    /**
     * iOS toggle：OFF=FIELD_BG，ON=SUCCESS，滑块 12x12 白。整体 24x14 与滑块尺寸不变，
     * 轨道四周留 1px SEPARATOR 描边。滑块使用高亮白保证在深色/浅色均清晰。
     */
    public static void toggle(GuiGraphics gg, int x, int y, boolean on) {
        drawPill(gg, x, y, TOGGLE_W, TOGGLE_H, SEPARATOR);          // 1px 描边（外圈）
        drawPill(gg, x + 1, y + 1, TOGGLE_W - 2, TOGGLE_H - 2, on ? SUCCESS : FIELD_BG);
        int knobY = y + (TOGGLE_H - TOGGLE_KNOB) / 2;
        int knobX = on ? x + TOGGLE_W - TOGGLE_KNOB - 1 : x + 1;
        drawRoundRect(gg, knobX, knobY, TOGGLE_KNOB, TOGGLE_KNOB, TOGGLE_KNOB / 2, activePalette.toggleKnob());
    }

    /** pill 钮底：FIELD_BG，按下态 CARD_SEL。 */
    public static void pillBackground(GuiGraphics gg, int x, int y, int w, int h, boolean pressed, boolean active) {
        drawPill(gg, x, y, w, h, active ? (pressed ? CARD_SEL : FIELD_BG) : BG_GROUP);
    }

    /** 文本钮：无边框，按下时才浮出 CARD_SEL 圆角底。 */
    public static void textBackground(GuiGraphics gg, int x, int y, int w, int h, boolean pressed) {
        if (pressed) drawPill(gg, x, y, w, h, CARD_SEL);
    }

    /** 文本钮墨色：TINT（危险操作 DANGER），禁用 TEXT_TERT。 */
    public static int textInk(boolean active, boolean danger) {
        if (!active) return TEXT_TERT;
        return danger ? DANGER : TINT;
    }

    /** pill 钮墨色：正文色，禁用 TEXT_TERT。 */
    public static int pillInk(boolean active) {
        return active ? TEXT_PRIMARY : TEXT_TERT;
    }

    /** segmented 轨道：FIELD_BG 圆角槽。 */
    public static void segmentedTrack(GuiGraphics gg, int x, int y, int w, int h) {
        drawPill(gg, x, y, w, h, FIELD_BG);
    }

    /** segmented 选中段：内缩 1px 的高亮圆角块。 */
    public static void segmentedCell(GuiGraphics gg, int x, int y, int w, int h) {
        drawPill(gg, x + 1, y + 1, w - 2, h - 2, activePalette.segmentedCell());
    }

    /** stepper 圆钮：TINT 底 14x14，白符号由调用方绘制。 */
    public static void stepperButton(GuiGraphics gg, int x, int y, boolean pressed, boolean active) {
        int fill = !active ? TEXT_TERT : (pressed ? TINT_DARK : TINT);
        drawRoundRect(gg, x, y, CIRCLE, CIRCLE, CIRCLE / 2, fill);
    }

    /** stepper 数值框：FIELD_BG 全圆角。 */
    public static void stepperField(GuiGraphics gg, int x, int y, int w, int h) {
        field(gg, x, y, w, h);
    }

    /** IN/OUT 徽牌：22x12 全圆角，IN=DANGER、OUT=TINT，白字。 */
    public static void roleBadge(GuiGraphics gg, int x, int y, boolean input) {
        drawPill(gg, x, y, BADGE_W, BADGE_H, input ? DANGER : TINT);
    }

    /**
     * 圆形删除×：14x14 DANGER 底 + 白色 ×。
     * 符号保证在深色/浅色模式下均保持纯白鲜明。
     */
    public static void deleteCross(GuiGraphics gg, int x, int y, boolean pressed) {
        drawRoundRect(gg, x, y, CIRCLE, CIRCLE, CIRCLE / 2, pressed ? DANGER_DARK : DANGER);
        for (int i = -3; i <= 3; i++) {
            gg.fill(x + 7 + i, y + 7 + i, x + 8 + i, y + 8 + i, 0xFFFFFFFF);
            gg.fill(x + 7 + i, y + 7 - i, x + 8 + i, y + 8 - i, 0xFFFFFFFF);
        }
    }

    /** 色牌（介质 chip / 状态 pill）：底色圆角块 + 白字由调用方绘制。 */
    public static void chip(GuiGraphics gg, int x, int y, int w, int h, int color) {
        drawPill(gg, x, y, w, h, color);
    }

    /** 维度 pill：4px 宽的圆角竖条。 */
    public static void dimPill(GuiGraphics gg, int x, int y, int h, int color) {
        drawRoundRect(gg, x, y, 4, h, 2, color);
    }

    /**
     * 维度语义色（沿用原语义，但提亮到 iOS 饱和度）：主界绿 / 下界红 / 末地紫 / 其他模组维度青蓝。
     */
    public static int dimColor(String dim) {
        if (dim.contains("nether")) return DANGER;
        if (dim.contains("end")) return DIM_END;
        if (!dim.startsWith("minecraft:")) return TINT;
        return SUCCESS;
    }

    // ==================== 对话框 ====================
    /** 遮罩 + 居中白卡/深灰卡（圆角 6）。 */
    public static void dialog(GuiGraphics gg, int screenW, int screenH, int x, int y, int w, int h,
            Font font, Component title) {
        gg.fill(0, 0, screenW, screenH, BG_OVERLAY);
        drawRoundRect(gg, x, y, w, h, R_DIALOG, CARD);
        if (title != null) drawCenteredBold(gg, font, title, x, y + 7, w, TEXT_PRIMARY);
    }

    private P2PTheme() {}
}
