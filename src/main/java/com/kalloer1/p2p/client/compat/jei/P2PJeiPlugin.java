package com.kalloer1.p2p.client.compat.jei;

import java.util.ArrayList;
import java.util.List;

import com.kalloer1.p2p.client.screen.FilterEditor;
import com.kalloer1.p2p.medium.Mediums;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 本体自带的 JEI 插件（1.3.0）：FilterEditor 的物品幽灵拖入。
 *
 * <p>此前物品拖入由 JEI 对容器槽位的通用兜底勉强承担，附属只补了化学品拖入；
 * 现改为显式注册：目标 = 标记网格的 27 个槽位矩形（由 {@link FilterEditor#getMarkerGridArea()}
 * 与槽距 {@link FilterEditor#getMarkerPitch()} 推算，每格 16x16），拖入即
 * {@link FilterEditor#addMarker(ItemStack)}，仅物品页生效。
 *
 * <p>{@code @JeiPlugin} 类由 JEI 自己通过注解扫描加载：JEI 不在场时该类永远不会被
 * 触碰，因此 JEI 依赖保持 compileOnly、运行时无需声明。与附属的 MekanismJeiPlugin
 * （化学品目标）互不干扰，可同时注册。
 */
@JeiPlugin
public final class P2PJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID =
            new ResourceLocation("p2p", "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(FilterEditor.class, new IGhostIngredientHandler<FilterEditor>() {
            @Override
            public <I> List<Target<I>> getTargetsTyped(FilterEditor screen, ITypedIngredient<I> ingredient,
                                                        boolean doStart) {
                if (!screen.isItemPage()) return List.of();
                // 介质门卫：GAS 这类 addMarker 必然 no-op 的频道不放行（accept 是 void，
                // JEI 视为已消费），否则会吞掉附属化学品插件对同一网格注册的目标。
                if (screen.getType() != Mediums.ITEM && screen.getType() != Mediums.FLUID) return List.of();
                ItemStack stack = ingredient.getIngredient(VanillaTypes.ITEM_STACK).orElse(null);
                if (stack == null || stack.isEmpty()) return List.of();
                Rect2i grid = screen.getMarkerGridArea();
                int pitch = screen.getMarkerPitch();
                int slot = 16;   // 槽位井 16x16，井起点即槽位原点
                List<Target<I>> targets = new ArrayList<>(FilterEditor.GRID_COLS * FilterEditor.GRID_ROWS);
                for (int row = 0; row < FilterEditor.GRID_ROWS; row++) {
                    for (int col = 0; col < FilterEditor.GRID_COLS; col++) {
                        final int sx = grid.getX() + col * pitch;
                        final int sy = grid.getY() + row * pitch;
                        targets.add(new Target<I>() {
                            @Override
                            public Rect2i getArea() {
                                return new Rect2i(sx, sy, slot, slot);
                            }

                            @Override
                            public void accept(I value) {
                                screen.addMarker((ItemStack) value);
                            }
                        });
                    }
                }
                return targets;
            }

            @Override
            public void onComplete() {
            }
        });
    }
}
