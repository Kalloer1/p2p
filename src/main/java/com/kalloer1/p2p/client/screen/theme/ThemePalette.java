package com.kalloer1.p2p.client.screen.theme;

/**
 * 主题调色板策略契约：定义所有色彩 Token（ARGB）。
 */
public interface ThemePalette {

    ThemeMode mode();

    /** 全屏遮罩。 */
    int bgOverlay();

    /** 面板底色（systemGroupedBackground）。 */
    int bgGroup();

    /** 卡片 / 列表 / 工具条底。 */
    int card();

    /** 选中行 / 悬停行。 */
    int cardSel();

    /** 1px 分隔线。 */
    int separator();

    /** 标题、主要文本。 */
    int textPrimary();

    /** 标签、次要说明文本。 */
    int textSecond();

    /** 占位符、禁用态文本。 */
    int textTert();

    /** 主操作高亮、链接、OUT 徽牌、选中项。 */
    int tint();

    /** 主操作高亮按下态。 */
    int tintDark();

    /** 危险操作、删除、IN 徽牌、错误。 */
    int danger();

    /** 危险操作按下态。 */
    int dangerDark();

    /** 成功状态、已连接、toggle 开启。 */
    int success();

    /** 警告状态、注意提示。 */
    int warning();

    /** 输入框、搜索框底色。 */
    int fieldBg();

    /** 槽位井底色。 */
    int slotWell();

    /** 末地维度紫。 */
    int dimEnd();

    /** toggle 开关滑块 knob 颜色。 */
    int toggleKnob();

    /** 分段控制器（segmented）激活段背景色。 */
    int segmentedCell();
}
