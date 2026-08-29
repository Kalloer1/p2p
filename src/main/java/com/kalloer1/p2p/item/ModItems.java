package com.kalloer1.p2p.item;

import com.kalloer1.p2p.P2P;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, P2P.MODID);

    public static final RegistryObject<Item> CHANNEL_WRENCH = ITEMS.register("channel_wrench",
            () -> new ChannelWrench(new Item.Properties().stacksTo(1).fireResistant()));
}
