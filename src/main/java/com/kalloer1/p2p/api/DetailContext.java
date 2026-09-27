package com.kalloer1.p2p.api;

import com.kalloer1.p2p.network.ChannelActionC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import java.util.function.Consumer;

/**
 * {@link ChannelScreenExtension} 每次回调时由本体新建的窄门面：给出频道详情面板
 * （右侧区域）所需的几何、字体与发包能力。坐标语义与本体 ChannelScreen 一致：
 * 面板左上角 (leftPos, topPos)，面板尺寸 384x208，
 * 右侧详情区从 leftPos+148 起、宽 384-148-4；顶栏标签画在 topPos-16 处。
 *
 * <p>纯客户端类型：只在 {@link ChannelScreenExtension} 回调（本体客户端 GUI 派发）中出现，
 * 附属的扩展实现类不得在通用（common）代码路径上加载。
 */
public final class DetailContext {
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
