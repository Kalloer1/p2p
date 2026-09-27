package com.kalloer1.p2p.api;

import com.kalloer1.p2p.channel.Filter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

/**
 * 按介质（medium id）为过滤器编辑器（{@code FilterEditor}）挂一页自定义模板的扩展契约。
 * 通过 {@link FilterPageExtensions#register(String, FilterPageExtension)} 注册。
 *
 * <p>回调全部发生在本体 FilterEditor 的客户端生命周期里：
 * <ul>
 *   <li>{@link #onOpen}：编辑器构造完成后，把成员上已有的 {@link Filter#addonData}
 *       读回扩展自己的界面状态。</li>
 *   <li>{@link #onInit}：本体 init() 尾部；在此注册自己的按钮/输入控件
 *       （{@link FilterPageContext#addWidget}）。窗口尺寸变化会再次调用，控件如已注册需自行幂等。</li>
 *   <li>{@link #finish}：本体"完成"按钮按下时先于默认逻辑调用；返回 true 表示扩展已自行
 *       {@link FilterPageContext#commitFilter} 并提交，本体跳过默认提交。</li>
 *   <li>{@link #slotClicked}：任何槽位点击先于默认逻辑调用（含非 ITEM 页守卫之前）；
 *       返回 true 消费该点击。</li>
 *   <li>{@link #renderOverlay}：本体 render 尾部叠加绘制。</li>
 * </ul>
 *
 * <p>纯客户端契约；数据侧用 {@code Filter.addonData}（namespaced NBT，随存档与
 * SET_FILTER 同步透传），本体不解释其内容。
 */
public interface FilterPageExtension {
    /** 编辑器打开：existing 为该成员当前方向的过滤器（可能为 null）。 */
    default void onOpen(FilterPageContext ctx, @Nullable Filter existing) {}

    /** 本体 init() 尾部：注册扩展控件。窗口 resize 会再次调用，需幂等。 */
    default void onInit(FilterPageContext ctx) {}

    /** 返回 true = 扩展已自行 commitFilter 并接管提交；本体不再走默认提交。 */
    default boolean finish(FilterPageContext ctx) {
        return false;
    }

    /** 返回 true = 消费该槽位点击。 */
    default boolean slotClicked(FilterPageContext ctx, Slot slot, int slotId, int mouseButton, ClickType clickType) {
        return false;
    }

    /** 本体 render 尾部叠加绘制。 */
    default void renderOverlay(FilterPageContext ctx, GuiGraphics graphics, int mouseX, int mouseY) {}
}
