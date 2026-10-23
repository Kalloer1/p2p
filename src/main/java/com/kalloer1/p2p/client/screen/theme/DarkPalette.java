package com.kalloer1.p2p.client.screen.theme;

/**
 * 现代深色 iOS 风格调色板（Dark Mode Palette）。
 * 对标 Apple 官方 Dark 规范：深黑底板 + 深灰卡片 + 凹陷暗井 + 高对比纯白文字与明艳语义色。
 */
public final class DarkPalette implements ThemePalette {
    public static final DarkPalette INSTANCE = new DarkPalette();

    private DarkPalette() {}

    @Override
    public ThemeMode mode() {
        return ThemeMode.DARK;
    }

    @Override
    public int bgOverlay() {
        return 0x80000000;
    }

    @Override
    public int bgGroup() {
        return 0xFF121214;
    }

    @Override
    public int card() {
        return 0xFF1C1C1E;
    }

    @Override
    public int cardSel() {
        return 0xFF2C2C2E;
    }

    @Override
    public int separator() {
        return 0xFF38383A;
    }

    @Override
    public int textPrimary() {
        return 0xFFFFFFFF;
    }

    @Override
    public int textSecond() {
        return 0xFF98989D;
    }

    @Override
    public int textTert() {
        return 0xFF636366;
    }

    @Override
    public int tint() {
        return 0xFF0A84FF;
    }

    @Override
    public int tintDark() {
        return 0xFF0066CC;
    }

    @Override
    public int danger() {
        return 0xFFFF453A;
    }

    @Override
    public int dangerDark() {
        return 0xFFD70015;
    }

    @Override
    public int success() {
        return 0xFF30D158;
    }

    @Override
    public int warning() {
        return 0xFFFF9F0A;
    }

    @Override
    public int fieldBg() {
        return 0xFF262629;
    }

    @Override
    public int slotWell() {
        return 0xFF141416;
    }

    @Override
    public int dimEnd() {
        return 0xFFBF5AF2;
    }

    @Override
    public int toggleKnob() {
        return 0xFFFFFFFF;
    }

    @Override
    public int segmentedCell() {
        return 0xFF3A3A3C;
    }
}
