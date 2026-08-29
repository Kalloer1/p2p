package com.kalloer1.p2p;

import com.kalloer1.p2p.item.ModItems;
import com.kalloer1.p2p.network.ModMessages;
import com.kalloer1.p2p.tab.ModTabs;
import com.kalloer1.p2p.transfer.TransferEngine;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(P2P.MODID)
public class P2P {
    public static final String MODID = "p2p";
    public static final Logger LOGGER = LogManager.getLogger();

    public P2P() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modEventBus);
        ModTabs.CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.register(TransferEngine.INSTANCE);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModMessages::register);
    }
}
