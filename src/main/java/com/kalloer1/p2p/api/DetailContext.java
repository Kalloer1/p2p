package com.kalloer1.p2p.api;

import com.kalloer1.p2p.network.ChannelActionC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import java.util.function.Consumer;

/**
 * {@link ChannelScreenExtension} 每次回调时由本体新建的窄门面：给出频道详情面板
 * （右侧区域）所需的几何、字体与发包能力。坐标语义与本体 ChannelScreen 一致（1.2.0 起，
 * 规格 .grill-research/ios-ui-spec.md §4）：
 * 面板左上角 (leftPos, topPos)，面板尺寸 408x240，导航行 20 高、底工具条 y=212..232；
 * 左栏 x=leftPos+8 宽 140，右侧详情区 x=leftPos+156 宽 244；
 * 附属接管区（详情视口）为右栏全帧 244x160，即 topPos+44 .. topPos+204。
 * 原 384x208 面板与 topPos-16 的顶栏标签条已废除，附属不得再依赖那组常量。
 *
 * <p>纯客户端类型：只在 {@link ChannelScreenExtension} 回调（本体客户端 GUI 派发）中出现，
 * 附属的扩展实现类不得在通用（common）代码路径上加载。
 */
public final class DetailContext {
    /** 面板宽（1.2.0 几何契约）。 */
    public static final int PANEL_W = 408;
    /** 面板高（1.2.0 几何契约）。 */
    public static final int PANEL_H = 240;
    /** 导航行高。 */
    public static final int NAV_H = 20;
    /** 配置 pill 行所在的纵向区间（相对 topPos），左列搜索框同行。 */
    public static final int CONFIG_TOP = 20;
    public static final int CONFIG_BOTTOM = 34;
    /** 附属接管区（详情视口）上下沿（相对 topPos）。 */
    public static final int FRAME_TOP = 44;
    public static final int FRAME_BOTTOM = 204;
    /** 接管帧高度 = FRAME_BOTTOM - FRAME_TOP。 */
    public static final int FRAME_H = FRAME_BOTTOM - FRAME_TOP;
    /** 端点行/列表行高。 */
    public static final int ROW_H = 18;

    public final int leftPos;
    public final int topPos;
    public final int panelWidth;
    public final int panelHeight;
    /** 右侧详情区左边界 x。 */
    public final int rightAreaX;
    /** 右侧详情区宽度。 */
    public final int rightAreaWidth;
    public final Font font;
    public final Minecraft minecraft;

    private final Consumer<ChannelActionC2SPacket> sender;

    public DetailContext(int leftPos, int topPos, int panelWidth, int panelHeight,
                         int rightAreaX, int rightAreaWidth, Font font, Minecraft minecraft,
                         Consumer<ChannelActionC2SPacket> sender) {
        this.leftPos = leftPos;
        this.topPos = topPos;
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
        this.rightAreaX = rightAreaX;
        this.rightAreaWidth = rightAreaWidth;
        this.font = font;
        this.minecraft = minecraft;
        this.sender = sender;
    }

    /** 附属接管帧左边界（= rightAreaX）。 */
    public int frameX() { return rightAreaX; }
    /** 附属接管帧宽度（= rightAreaWidth）。 */
    public int frameWidth() { return rightAreaWidth; }
    /** 附属接管帧上沿（屏幕坐标）。 */
    public int frameTop() { return topPos + FRAME_TOP; }
    /** 附属接管帧下沿（屏幕坐标，不含）。 */
    public int frameBottom() { return topPos + FRAME_BOTTOM; }
    /** 配置 pill 行上沿（屏幕坐标）：附属若需让出该行以外的空间可用它作参照。 */
    public int configTop() { return topPos + CONFIG_TOP; }

    /** 发送任意本体频道动作包（等价于附属直接调 ModMessages.sendToServer）。 */
    public void sendAction(ChannelActionC2SPacket packet) {
        sender.accept(packet);
    }

    /** 便捷：不带 memberKey 的频道级动作（SET_RATE / TOGGLE_REDSTONE / ...）。 */
    public void sendChannelAction(int channelId, ChannelActionC2SPacket.Action action, int intVal) {
        sender.accept(new ChannelActionC2SPacket(channelId, action, null, intVal, null, false, null, ""));
    }

    /** 便捷：针对某成员 key 的动作（SET_ROLE / SET_FACE / REMOVE_MEMBER / SET_PRIORITY...）。 */
    public void sendMemberAction(int channelId, ChannelActionC2SPacket.Action action, int intVal, String memberKey) {
        sender.accept(new ChannelActionC2SPacket(channelId, action, null, intVal, memberKey, false, null, ""));
    }
}
