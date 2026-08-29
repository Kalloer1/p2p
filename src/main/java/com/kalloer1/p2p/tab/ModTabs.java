package com.kalloer1.p2p.tab;

import com.kalloer1.p2p.P2P;
import com.kalloer1.p2p.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.RegistryObject;

public class ModTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, P2P.MODID);

    public static final RegistryObject<CreativeModeTab> ETHERLINK_TAB = CREATIVE_MODE_TABS.register("p2p_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.p2p.p2p_tab"))
                    .icon(() -> new ItemStack(ModItems.CHANNEL_WRENCH.get()))
                    .displayItems((params, output) -> output.accept(ModItems.CHANNEL_WRENCH.get()))
                    .build());
}
