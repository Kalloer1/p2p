package com.kalloer1.p2p.client.screen.theme;

/**
 * 经典浅色 iOS 风格调色板（默认原版白底风格）。
 */
public final class LightPalette implements ThemePalette {
    public static final LightPalette INSTANCE = new LightPalette();

    private LightPalette() {}

    @Override
    public ThemeMode mode() {
        return ThemeMode.LIGHT;
    }

    @Override
    public int bgOverlay() {
        return 0x80000000;
    }

    @Override
    public int bgGroup() {
        return 0xFFF2F2F7;
    }

    @Override
    public int card() {
        return 0xFFFFFFFF;
    }

    @Override
    public int cardSel() {
        return 0xFFE9E9EE;
    }

    @Override
    public int separator() {
        return 0xFFD1D1D6;
    }

    @Override
    public int textPrimary() {
        return 0xFF1C1C1E;
    }

    @Override
    public int textSecond() {
        return 0xFF8E8E93;
    }

    @Override
    public int textTert() {
        return 0xFFAEAEB2;
    }

    @Override
    public int tint() {
        return 0xFF007AFF;
    }

    @Override
    public int tintDark() {
        return 0xFF0062CC;
    }

    @Override
    public int danger() {
        return 0xFFFF3B30;
    }

    @Override
    public int dangerDark() {
        return 0xFFCC2E26;
    }

    @Override
    public int success() {
        return 0xFF34C759;
    }

    @Override
    public int warning() {
        return 0xFFFF9500;
    }

    @Override
    public int fieldBg() {
        return 0xFFE5E5EA;
    }

    @Override
    public int slotWell() {
        return 0xFFEBEBF0;
    }

    @Override
    public int dimEnd() {
        return 0xFFAF52DE;
    }

    @Override
    public int toggleKnob() {
        return 0xFFFFFFFF;
    }

    @Override
    public int segmentedCell() {
        return 0xFFFFFFFF;
    }
}
