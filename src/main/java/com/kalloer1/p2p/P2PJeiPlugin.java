package com.kalloer1.p2p;

import com.kalloer1.p2p.medium.Medium;
import com.kalloer1.p2p.medium.Mediums;
import com.kalloer1.p2p.client.screen.FilterEditor;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.Collections;
import java.util.List;

/**
 * JEI integration point. Loaded by JEI itself (via the @JeiPlugin annotation) — the main mod never references
 * this class, so when JEI is not installed there is no NoClassDefFoundError at runtime.
 *
 * Registers a ghost-ingredient handler for FilterEditor so players can drag an entry from JEI's list straight
 * onto the marker grid to add a filter marker: items on ITEM channels, fluids (or a filled bucket) on FLUID
 * channels. ENERGY channels have no templates, so they get no drop target.
 */
@JeiPlugin
public class P2PJeiPlugin implements IModPlugin {
    public static final ResourceLocation UID = new ResourceLocation(P2P.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration reg) {
        reg.addGhostIngredientHandler(FilterEditor.class, new FilterEditorGhostHandler());
    }

    private static class FilterEditorGhostHandler implements IGhostIngredientHandler<FilterEditor> {
        @Override
        public <I> List<IGhostIngredientHandler.Target<I>> getTargetsTyped(FilterEditor gui, ITypedIngredient<I> ingredient, boolean doStart) {
            Medium type = gui.getType();
            // 物品频道收物品模板，流体频道收流体模板；能量频道没有模板可填，直接拒绝
            if (type != Mediums.ITEM && type != Mediums.FLUID) return Collections.emptyList();
            if (!gui.isItemPage()) return Collections.emptyList();   // only the 物品 page uses the marker grid
            // ITypedIngredient.getItemStack() / getIngredient(type) 返回的是 Optional：
            // 空 Optional 表示"这个条目不是该类型"，不是"空堆叠"。
            boolean ok = (type == Mediums.ITEM)
                    ? ingredient.getItemStack().isPresent()
                    : ingredient.getIngredient(ForgeTypes.FLUID_STACK).isPresent()
                        || ingredient.getItemStack().isPresent();   // 流体频道也允许直接拖桶
            if (!ok) return Collections.emptyList();
            Rect2i area = gui.getMarkerGridArea();
            IGhostIngredientHandler.Target<I> target = new IGhostIngredientHandler.Target<>() {
                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(I ing) {
                    if (ing instanceof FluidStack fs) gui.addFluidMarker(fs);
                    else if (ing instanceof ItemStack stack) gui.addMarker(stack);
                }
            };
            return Collections.singletonList(target);
        }

        @Override
        public void onComplete() {
        }
    }
}
