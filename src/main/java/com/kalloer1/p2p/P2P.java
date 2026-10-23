package com.kalloer1.p2p;

import com.kalloer1.p2p.client.P2PClientConfig;
import com.kalloer1.p2p.transfer.TransferEngine;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(P2P.MODID)
public class P2P {
    public static final String MODID = "p2p";
    public static final Logger LOGGER = LogManager.getLogger();

    public P2P(ModContainer modContainer) {
        P2PConfig.register(modContainer);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            P2PClientConfig.register(modContainer);
        }
        NeoForge.EVENT_BUS.register(TransferEngine.INSTANCE);
    }
}
