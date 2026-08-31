package com.kalloer1.p2p;

import com.kalloer1.p2p.client.ClientBindState;
import com.kalloer1.p2p.channel.ChannelManager;
import com.kalloer1.p2p.network.BindClickC2SPacket;
import com.kalloer1.p2p.network.ModMessages;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge bus. Two responsibilities:
 *
 * <ol>
 *   <li>绑定模式拦截：玩家在绑定模式下空手点击方块时，取消原版的交互 / 破坏，改由
 *       {@link BindClickC2SPacket} 把这次点击带给服务端（右键=输入 / 左键=输出 / Ctrl=连锁）。
 *       取消只在客户端侧发生，避免原版把方块破坏掉或与容器 GUI 冲突；真正的绑定动作由服务端包处理。</li>
 *   <li>存档加载：ChannelManager 挂在主世界 SavedData 上，所有网络 / 频道 / 端点随游戏存档持久化。</li>
 * </ol>
 *
 * 注册在双方（不限制 Dist），因为单机（集成服务器）下 CLIENT 侧订阅才会触发，
 * 而下面的逻辑用 {@code isClientSide()} 严格限定只在客户端执行拦截与发包。
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

    /** 左键：绑定模式下空手点击 = 设置输出（OUTPUT）。取消破坏，避免误删方块。 */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock evt) {
        if (!evt.getLevel().isClientSide()) return;
        if (!ClientBindState.isActive()) return;
        if (evt.getHand() != InteractionHand.MAIN_HAND) return;
        Player player = evt.getEntity();
        if (player == null || !player.getMainHandItem().isEmpty()) return;
        evt.setCanceled(true);
        evt.setCancellationResult(InteractionResult.CONSUME);
        ModMessages.INSTANCE.sendToServer(
                new BindClickC2SPacket(evt.getPos(), evt.getFace(), false, Screen.hasControlDown()));
    }

    /** 右键：绑定模式下空手点击 = 设置输入（INPUT）。取消原版交互，避免打开容器 GUI。 */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock evt) {
        if (!evt.getLevel().isClientSide()) return;
        if (!ClientBindState.isActive()) return;
        if (evt.getHand() != InteractionHand.MAIN_HAND) return;
        Player player = evt.getEntity();
        if (player == null || !player.getMainHandItem().isEmpty()) return;
        evt.setCanceled(true);
        evt.setCancellationResult(InteractionResult.CONSUME);
        ModMessages.INSTANCE.sendToServer(
                new BindClickC2SPacket(evt.getPos(), evt.getFace(), true, Screen.hasControlDown()));
    }
}
