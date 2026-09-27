package com.kalloer1.p2p;

import com.kalloer1.p2p.transfer.TransferEngine;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(P2P.MODID)
public class P2P {
    public static final String MODID = "p2p";
    public static final Logger LOGGER = LogManager.getLogger();

    public P2P(ModContainer modContainer) {
        P2PConfig.register(modContainer);
        NeoForge.EVENT_BUS.register(TransferEngine.INSTANCE);
    }
}
