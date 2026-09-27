package com.kalloer1.p2p.api;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;

import com.kalloer1.p2p.channel.Member;
import com.kalloer1.p2p.client.screen.FilterEditor;
import com.kalloer1.p2p.medium.Medium;
import com.kalloer1.p2p.network.ChannelActionC2SPacket;
import com.kalloer1.p2p.network.ModMessages;

/**
 * {@link FilterPageExtension} 的窄门面：包装本体 FilterEditor，暴露附属挂过滤页
 * 所需的频道上下文、ghost 容器、控件注册与提交能力。附属不需要 shadow/mixin。
 */
public final class FilterPageContext {
    private final FilterEditor screen;
    public final int channelId;
    public final Member member;
    /** INPUT 成员编辑抽取过滤器，OUTPUT 成员编辑注入过滤器。 */
    public final boolean extract;

    public FilterPageContext(FilterEditor screen, int channelId, Member member, boolean extract) {
        this.screen = screen;
        this.channelId = channelId;
        this.member = member;
        this.extract = extract;
    }

    public Medium type() { return screen.getType(); }
    public Rect2i markerGridArea() { return screen.getMarkerGridArea(); }
    public boolean isItemPage() { return screen.isItemPage(); }
    public Minecraft minecraft() { return screen.getMinecraft(); }

    /**
     * 标记网格的槽距（本体 18）。附属自定义色块必须按此对齐本体槽位井，
     * 而不是把 18 写死在自己的绘制代码里。
     */
    public int markerPitch() { return screen.getMarkerPitch(); }

    /**
     * 本体「模式」行（白名单/黑名单）的屏幕矩形。规格 §4 要求附属过滤页的模式钮
     * 与本体同行同规格，因此几何由本体给出，附属不再写死 {@code area.getY() + 102} 这类偏移。
     */
    public Rect2i modeRowArea() { return screen.getModeRowArea(); }

    /** 向界面注册一个可交互控件（按钮等），本体负责每帧绘制与点击派发。 */
    public <T extends GuiEventListener & Renderable & NarratableEntry> T addWidget(T widget) {
        return screen.addScreenWidget(widget);
    }

    /** ghost 槽位容器（本体只渲染前 27 格，附属可自行写入自定义显示）。 */
    public SimpleContainer ghostContainer() { return screen.ghostContainer(); }

    /** 让本体把默认模板列表重新镜像进 ghost 容器（附属自定义显示后如需恢复默认布局可调用）。 */
    public void syncGhost() { screen.syncGhost(); }

    /** 以 SET_FILTER 动作提交一个已序列化的过滤器（等价于本体"完成"的发送路径）。 */
    public void commitFilter(CompoundTag filterTag) {
        ModMessages.sendToServer(new ChannelActionC2SPacket(channelId,
                ChannelActionC2SPacket.Action.SET_FILTER, null, 0, member.key(), extract, filterTag, ""));
    }

    /** 关闭编辑器回到父界面。 */
    public void close() { screen.onClose(); }
}
