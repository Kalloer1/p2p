package com.kalloer1.p2p.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 本体统一的按钮皮肤：所有配置界面（含附属）都通过 {@link Style} 选用 P2PTheme 的组件画法，
 * 不再各自持有 bevel/硬编码色的私有绘制。默认 {@link Style#PILL}，因此附属不改调用也能编译。
 */
public class P2PFlatButton extends Button {

    /** 组件样式（规格 §3）。 */
    public enum Style {
        /** FIELD_BG 圆角底 + 正文色字（配置行）。 */
        PILL,
        /** 无边框 TINT 蓝字，危险操作红字（新建/删除/取消/完成）。 */
        TEXT,
        /** 左标签 + 右 24x14 iOS 开关（区块加载这类布尔态）。 */
        TOGGLE,
        /** segmented 的一段：选中=白底 TINT 字，未选中=透明灰字（轨道由界面自绘）。 */
        SEGMENT,
        /** 介质色牌：调用方给的色底 + 白字。 */
        CHIP,
        /** −／+ 圆钮：TINT 底 14x14 白符号（规格 §3 stepper）。 */
        STEPPER,
        /** 次级圆钮：FIELD_BG 底正文色符号（导航 ‹ ›）。 */
        CIRCLE
    }

    private Style style = Style.PILL;
    /** true = 该控件是其分组里的当前选择（页签）。 */
    private boolean selected;
    private boolean danger;
    private int chipColor = P2PTheme.FIELD_BG;

    public P2PFlatButton(Builder builder) {
        super(builder);
    }

    /** 指定样式；返回 {@code this} 便于链式调用。 */
    public P2PFlatButton setStyle(Style style) {
        this.style = style == null ? Style.PILL : style;
        return this;
    }

    public Style getStyle() { return style; }

    /** 标记为分组的当前选择（segmented / 选中态）。 */
    public P2PFlatButton setSelected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public boolean isSelected() { return selected; }

    /** 文本钮是否为危险操作（红字）。 */
    public P2PFlatButton setDanger(boolean danger) {
        this.danger = danger;
        return this;
    }

    /** {@link Style#CHIP} 的底色。 */
    public P2PFlatButton setChipColor(int color) {
        this.chipColor = color;
        return this;
    }

    /** 标签之外还需占用的宽度（TOGGLE 要给开关留位），供调用方做宽度重排。 */
    public int extraWidth() {
        return style == Style.TOGGLE ? P2PTheme.TOGGLE_W + 4 : 0;
    }

    @Override
    protected void renderWidget(GuiGraphics gg, int mx, int my, float pt) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        // 只看鼠标悬停，不看键盘焦点：vanilla 会在点击后把键盘焦点留在按钮上，
        // 如果把焦点也画成悬停高亮，按钮看起来就永远卡在按下状态弹不起来。
        boolean hovered = isHovered();
        Component label = getMessage();
        Font f = Minecraft.getInstance().font;
        int textY = y + (h - 8) / 2;
        switch (style) {
            case TEXT -> {
                P2PTheme.textBackground(gg, x, y, w, h, hovered && active);
                if (label != null) {
                    gg.drawString(f, label, x + (w - f.width(label)) / 2, textY,
                            P2PTheme.textInk(active, danger), false);
                }
            }
            case TOGGLE -> {
                P2PTheme.pillBackground(gg, x, y, w, h, hovered && active, active);
                int switchX = x + w - P2PTheme.TOGGLE_W - 2;
                P2PTheme.toggle(gg, switchX, y + (h - P2PTheme.TOGGLE_H) / 2, selected);
                if (label != null) gg.drawString(f, label, x + 6, textY, P2PTheme.pillInk(active), false);
            }
            case SEGMENT -> {
                int ink = !active ? P2PTheme.TEXT_TERT : (selected ? P2PTheme.TINT : P2PTheme.TEXT_SECOND);
                if (selected) P2PTheme.segmentedCell(gg, x, y, w, h);
                if (label != null) {
                    int lw = f.width(label);
                    gg.drawString(f, label, x + (w - lw) / 2, textY, ink, false);
                }
            }
            case CHIP -> {
                P2PTheme.chip(gg, x, y, w, h, chipColor);
                if (label != null) {
                    int lw = f.width(label);
                    gg.drawString(f, label, x + (w - lw) / 2, y + (h - 8) / 2,
                            active ? 0xFFFFFFFF : P2PTheme.TEXT_TERT, false);
                }
            }
            case STEPPER -> {
                P2PTheme.stepperButton(gg, x, y, hovered && active, active);
                if (label != null) {
                    gg.drawString(f, label, x + (w - f.width(label)) / 2, y + (h - 8) / 2,
                            active ? 0xFFFFFFFF : P2PTheme.CARD, false);
                }
            }
            case CIRCLE -> {
                P2PTheme.drawRoundRect(gg, x, y, w, h, Math.min(w, h) / 2,
                        hovered && active ? P2PTheme.CARD_SEL : P2PTheme.FIELD_BG);
                if (label != null) {
                    gg.drawString(f, label, x + (w - f.width(label)) / 2, y + (h - 8) / 2,
                            P2PTheme.pillInk(active), false);
                }
            }
            default -> {
                P2PTheme.pillBackground(gg, x, y, w, h, (hovered && active) || selected, active);
                if (label != null) {
                    int ink = !active ? P2PTheme.TEXT_TERT
                            : (selected || hovered ? P2PTheme.TINT : P2PTheme.TEXT_PRIMARY);
                    gg.drawString(f, label, x + (w - f.width(label)) / 2, textY, ink, false);
                }
            }
        }
    }
}
