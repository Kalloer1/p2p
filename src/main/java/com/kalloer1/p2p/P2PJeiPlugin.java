package com.kalloer1.p2p;

import com.kalloer1.p2p.channel.ChannelType;
import com.kalloer1.p2p.client.screen.FilterEditor;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

/**
 * JEI integration point. Loaded by JEI itself (via the @JeiPlugin annotation) — the main mod never references
 * this class, so when JEI is not installed there is no NoClassDefFoundError at runtime.
 *
 * Registers a ghost-ingredient handler for FilterEditor so players can drag an item from JEI's list straight
 * onto the marker grid to add a filter marker.
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
            if (gui.getType() != ChannelType.ITEM) return Collections.emptyList();
            if (ingredient.getItemStack().isEmpty()) return Collections.emptyList();
            Rect2i area = gui.getMarkerGridArea();
            IGhostIngredientHandler.Target<I> target = new IGhostIngredientHandler.Target<>() {
                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(I ing) {
                    if (ing instanceof ItemStack stack) gui.addMarker(stack);
                }
            };
            return Collections.singletonList(target);
        }

        @Override
        public void onComplete() {
        }
    }
}
