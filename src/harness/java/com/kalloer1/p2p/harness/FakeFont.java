package com.kalloer1.p2p.harness;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;

import java.util.List;

/**
 * 离线 Font：super(null, false) 不触碰任何资源，被调用的测量/截断方法全部委托 {@link HFont}。
 */
public class FakeFont extends Font {

    public final HFont hfont;

    public FakeFont(HFont hfont) {
        super(null, false);
        this.hfont = hfont;
    }

    @Override
    public int width(String text) {
        return hfont.width(text);
    }

    @Override
    public int width(FormattedText text) {
        return hfont.width(HGuiGraphics.flatten(text));
    }

    @Override
    public int width(FormattedCharSequence text) {
        return hfont.width(HGuiGraphics.flatten(text));
    }

    @Override
    public String plainSubstrByWidth(String text, int maxWidth, boolean tail) {
        return hfont.plainSubstrByWidth(text, maxWidth, tail);
    }

    @Override
    public String plainSubstrByWidth(String text, int maxWidth) {
        return hfont.plainSubstrByWidth(text, maxWidth);
    }

    @Override
    public List<FormattedCharSequence> split(FormattedText text, int maxWidth) {
        // 简化换行：逐字符累积，超宽则断行（本 harness 的调用点不需要精确断词）
        String flat = HGuiGraphics.flatten(text);
        List<FormattedCharSequence> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        float acc = 0;
        for (int i = 0; i < flat.length(); i += flat.offsetByCodePoints(i, 1)) {
            int cp = flat.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            float adv = hfont.glyph(cp).advance();
            if (acc + adv > maxWidth && acc > 0) {
                out.add(toFcs(line.toString()));
                line.setLength(0);
                acc = 0;
            }
            line.append(ch);
            acc += adv;
        }
        if (line.length() > 0) out.add(toFcs(line.toString()));
        return out;
    }

    private static FormattedCharSequence toFcs(String s) {
        return sink -> StringDecomposer.iterateFormatted(s, net.minecraft.network.chat.Style.EMPTY, sink);
    }
}
