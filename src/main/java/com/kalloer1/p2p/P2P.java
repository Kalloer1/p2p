package com.kalloer1.p2p;

import com.kalloer1.p2p.client.P2PClientConfig;
import com.kalloer1.p2p.network.ModMessages;
import com.kalloer1.p2p.transfer.TransferEngine;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
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
        P2PConfig.register();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            P2PClientConfig.register();
        }
        // 绑定模式走按键 + 交互事件，不再依赖任何物品/创造标签页注册
        MinecraftForge.EVENT_BUS.register(TransferEngine.INSTANCE);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModMessages::register);
    }
}
