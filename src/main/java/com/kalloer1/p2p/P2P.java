package com.kalloer1.p2p;

import com.kalloer1.p2p.medium.Mediums;
import com.kalloer1.p2p.medium.RegisterMediumEvent;
import com.kalloer1.p2p.network.ModMessages;
import com.kalloer1.p2p.transfer.TransferEngine;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(P2P.MODID)
public class P2P {
    public static final String MODID = "p2p";
    public static final Logger LOGGER = LogManager.getLogger();

    private final IEventBus modEventBus;

    public P2P() {
        modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::onCommonSetup);
        // 介质注册点：load-complete 时派发——所有 mod 的 mod-bus 监听器此时都已注册，
        // 附属（p2p-mekanism 等）无论加载顺序先后都能收到本事件并注册它们的介质。
        modEventBus.addListener(this::onLoadComplete);
        P2PConfig.register();
        // 绑定模式走按键 + 交互事件，不再依赖任何物品/创造标签页注册
        MinecraftForge.EVENT_BUS.register(TransferEngine.INSTANCE);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModMessages::register);
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            // 内置介质（item/fluid/energy）在 Mediums 类加载时已注册（引用稳定，供 == 比较与回调）；
            // 这里派发事件让附属模组注册它们的介质（如 mekanism:gas / mekanism:heat）。
            // Forge 全局总线：一条总线所有人都能收到（mod-bus 事件不会跨 mod 传播，故不用）。
            MinecraftForge.EVENT_BUS.post(new RegisterMediumEvent());
            P2P.LOGGER.info("[p2p] RegisterMediumEvent dispatched (media={})", Mediums.all().size());
        });
    }
}
