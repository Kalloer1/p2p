package com.kalloer1.p2p.client.compat.emi;

import com.kalloer1.p2p.client.screen.FilterEditor;
import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

/**
 * EMI 拖放桥（1.3.0 真机反馈：EMI 用户把物品拖到 FilterEditor 标记网格完全不通——
 * EMI 不像 JEI 那样对幽灵拖入有通用的容器槽位兜底，必须显式注册拖放处理器）。
 *
 * <p>EMI 通过注解扫描发现带 {@link EmiEntrypoint} 的 {@link EmiPlugin} 类；
 * 本类只在 EMI 在场时才会被 EMI 加载，EMI 缺席时不会有人触碰这里的任何类型，
 * 所以 EMI 依赖保持 compileOnly 即可。只处理物品堆（builtin item），
 * 落点在 {@link FilterEditor#getMarkerGridArea()} 且当前在物品页时调用
 * {@link FilterEditor#addMarker(ItemStack)}（去重/上限/物品页判定都由 FilterEditor 负责）；
 * <b>只有 addMarker 返回 true（真的写入了模板）本 handler 才返回 true 消费这次拖放</b>，
 * 否则交还给后续 handler（compat 附属的气体罐等拖放路径），两边 handler 注册序不保证。
 */
@EmiEntrypoint
public final class P2PEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addDragDropHandler(FilterEditor.class, new EmiDragDropHandler<FilterEditor>() {
            @Override
            public boolean dropStack(FilterEditor screen, EmiIngredient ingredient, int mx, int my) {
                if (!screen.isItemPage()) return false;
                Rect2i grid = screen.getMarkerGridArea();
                if (mx < grid.getX() || mx >= grid.getX() + grid.getWidth()
                        || my < grid.getY() || my >= grid.getY() + grid.getHeight()) return false;
                // 1.3.1：只在确实写入模板时才返回 true（addMarker 去重/上限失败返回 false），
                // 让注册序在后的 handler（compat 气体罐等附属拖放路径）有机会接手。
                for (EmiStack s : ingredient.getEmiStacks()) {
                    ItemStack st = s.getItemStack();
                    if (st != null && !st.isEmpty() && screen.addMarker(st)) return true;
                }
                return false;
            }
        });
    }
}
