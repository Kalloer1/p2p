package com.kalloer1.p2p.client;

import com.kalloer1.p2p.P2P;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** MOD-bus client events: register key bindings. */
@EventBusSubscriber(modid = P2P.MODID, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(Keybinds.OPEN_GUI);
        event.register(Keybinds.BIND_MODE);
    }
}
