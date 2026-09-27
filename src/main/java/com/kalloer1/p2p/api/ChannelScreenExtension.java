package com.kalloer1.p2p.api;

import com.kalloer1.p2p.channel.Channel;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/**
 * 按介质（medium id）挂接频道详情面板的扩展契约。通过
 * {@link ChannelScreenExtensions#register(String, ChannelScreenExtension)} 注册。
 *
 * <p>回调全部发生在本体 ChannelScreen 的客户端绘制/输入路径上：
 * <ul>
 *   <li>{@link #replacesDefaultPanel}：true = 本体隐藏速率/节流控件、跳过默认端点表，
 *       把右侧详情区整体让给本扩展绘制（端点行、专属按钮等全部由扩展接管）。</li>
 *   <li>{@link #drawDetails}：每帧调用，即使频道 members 为空也会调用；
 *       replaces=false 时在本体默认面板之上叠加绘制。</li>
 *   <li>{@link #mouseClicked} / {@link #mouseScrolled}：先于本体处理；扩展只应对自己
 *       面板区域内的输入返回 true 消费，区域外返回 false 让本体继续。</li>
 * </ul>
 *
 * <p>纯客户端契约：注册与实现类加载都应在附属的客户端入口（JavaClient）里进行，
 * 附属的 common 代码不得引用本接口或其注册表所在类图。
 */
public interface ChannelScreenExtension {
    /** 是否整体接管右侧详情面板（隐藏默认速率/节流控件与端点表）。 */
    default boolean replacesDefaultPanel(@Nullable Channel channel) {
        return false;
    }

    /** 每帧绘制。channel 非 null（无选中频道时本体不调用扩展）。 */
    default void drawDetails(DetailContext ctx, GuiGraphics graphics, Channel channel) {}

    /** 返回 true 表示点击已被消费，本体不再处理。 */
    default boolean mouseClicked(DetailContext ctx, double mouseX, double mouseY, int button) {
        return false;
    }

    /** 返回 true 表示滚动已被消费，本体不再处理。 */
    default boolean mouseScrolled(DetailContext ctx, double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }
}
