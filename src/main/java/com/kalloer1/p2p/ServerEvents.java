package com.kalloer1.p2p;

import com.kalloer1.p2p.channel.ChannelManager;
import com.kalloer1.p2p.item.ChannelWrench;
import com.kalloer1.p2p.network.ModMessages;
import com.kalloer1.p2p.network.WrenchUseC2SPacket;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge bus: when the player right-clicks a block while holding the P2P wrench, cancel the interaction so the
 * targeted block's own GUI never opens. The wrench binding / mode / chain logic runs server-side from the
 * WrenchUseC2SPacket sent by ChannelWrench.useOn (which carries the live shift/control modifier state).
 *
 * Registered on BOTH sides (no Dist restriction) so it also fires in single-player (integrated server), where
 * FMLEnvironment.dist == Dist.CLIENT and a Dist.DEDICATED_SERVER-only subscriber would NOT be registered.
 * The isClientSide() guard below ensures the logic only runs on the server level.
 *
 * Persistence: the ChannelManager is attached to the OVERWORLD level's SavedData on ServerStartedEvent, so all
 * networks / channels / bound endpoints survive a game restart. Forge flushes SavedData when the level unloads
 * on server stop; we also mark it dirty on ServerStoppingEvent as a safety net.
 */
@Mod.EventBusSubscriber(modid = P2P.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEvents {

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        ServerLevel overworld = evt.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            ChannelManager loaded = overworld.getDataStorage().computeIfAbsent(ChannelManager::load, ChannelManager::new, "p2p_channels");
            ChannelManager.INSTANCE = loaded;
            P2P.LOGGER.info("[p2p] ChannelManager loaded from world save (channels={})",
                    loaded.getAllChannels().size());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        ChannelManager.INSTANCE.setDirty();
        P2P.LOGGER.info("[p2p] ChannelManager marked dirty for world-save flush (channels={})",
                ChannelManager.INSTANCE.getAllChannels().size());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock evt) {
        if (evt.getHand() != InteractionHand.MAIN_HAND) return;
        Player player = evt.getEntity();
        if (player == null) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ChannelWrench)) return;
        // Cancel on BOTH sides so the targeted block (e.g. a chest, whose client-side use() consumes the click
        // and would otherwise swallow the wrench's useOn) never opens its own GUI. The bind / mode / chain logic
        // runs from the WrenchUseC2SPacket we send on the client side, carrying the live shift/control state.
        evt.setCanceled(true);
        evt.setCancellationResult(InteractionResult.CONSUME);
        if (evt.getLevel().isClientSide()) {
            boolean shift = player.isShiftKeyDown();
            boolean control = Screen.hasControlDown();
            P2P.LOGGER.info("[p2p] ServerEvents(client): wrench right-click pos={} face={} shift={} ctrl={}",
                    evt.getPos(), evt.getFace(), shift, control);
            ModMessages.INSTANCE.sendToServer(new WrenchUseC2SPacket(evt.getPos(), evt.getFace(), shift, control));
        }
    }
}
