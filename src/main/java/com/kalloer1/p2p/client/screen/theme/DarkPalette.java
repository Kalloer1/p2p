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
        // iOS systemGroupedBackground / secondarySystemBackground 层次基底
        return 0xFF121214;
    }

    @Override
    public int card() {
        // iOS secondarySystemGroupedBackground 深灰卡片
        return 0xFF1C1C1E;
    }

    @Override
    public int cardSel() {
        // iOS tertiarySystemGroupedBackground 悬停 / 选中行
        return 0xFF2C2C2E;
    }

    @Override
    public int separator() {
        // 柔和的分隔线
        return 0xFF38383A;
    }

    @Override
    public int textPrimary() {
        // 主要标题、重要文字（纯白）
        return 0xFFFFFFFF;
    }

    @Override
    public int textSecond() {
        // iOS secondaryLabel
        return 0xFF98989D;
    }

    @Override
    public int textTert() {
        // iOS tertiaryLabel 弱提示 / 占位符
        return 0xFF636366;
    }

    @Override
    public int tint() {
        // iOS Dark 鲜艳系统蓝
        return 0xFF0A84FF;
    }

    @Override
    public int tintDark() {
        return 0xFF0066CC;
    }

    @Override
    public int danger() {
        // iOS Dark 鲜艳系统红
        return 0xFFFF453A;
    }

    @Override
    public int dangerDark() {
        return 0xFFD70015;
    }

    @Override
    public int success() {
        // iOS Dark 鲜艳系统绿
        return 0xFF30D158;
    }

    @Override
    public int warning() {
        // iOS Dark 鲜艳系统橙
        return 0xFFFF9F0A;
    }

    @Override
    public int fieldBg() {
        // 凹陷输入框底色
        return 0xFF262629;
    }

    @Override
    public int slotWell() {
        // 槽位井底色（比卡片更深产生下沉感）
        return 0xFF141416;
    }

    @Override
    public int dimEnd() {
        // iOS Dark 鲜艳紫色
        return 0xFFBF5AF2;
    }

    @Override
    public int toggleKnob() {
        // 开关滑块保持鲜明白色
        return 0xFFFFFFFF;
    }

    @Override
    public int segmentedCell() {
        // 选中页签浮起高亮
        return 0xFF3A3A3C;
    }
}
