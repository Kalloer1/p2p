package com.kalloer1.p2p;

import com.kalloer1.p2p.client.ClientBindState;
import com.kalloer1.p2p.channel.ChannelManager;
import com.kalloer1.p2p.network.BindClickC2SPacket;
import com.kalloer1.p2p.network.ModMessages;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.level.ServerPlayer;
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
 *   <li>绑定模式拦截：玩家在绑定模式下空手点击方块时，<b>客户端与服务端两侧</b>都取消原版的交互 / 破坏，
 *       改由 {@link BindClickC2SPacket} 把这次点击带给服务端（右键=输入 / 左键=输出 / Ctrl=连锁）。
 *       只取消客户端不够——创作模式的服务端 {@code handleBlockBreakAction} 仍会按收到的
 *       {@code START_DESTROY_BLOCK} 包拆掉方块，生存模式的服务端 {@code useItemOn} 仍会与方块交互，
 *       所以服务端这一侧也必须拦截。真正的绑定动作由 {@link BindClickC2SPacket} 在服务端处理。</li>
 *   <li>存档加载：ChannelManager 挂在主世界 SavedData 上，所有网络 / 频道 / 端点随游戏存档持久化。</li>
 * </ol>
 *
 * 注册在双方（不限制 Dist）：客户端侧负责取消原版表现 + 发包；服务端侧负责取消权威的破坏 / 交互。
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

    /** 左键：绑定模式下空手点击 = 设置输出（OUTPUT）。两侧都取消破坏，避免误删方块。 */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock evt) {
        Player player = evt.getEntity();
        if (player == null) return;
        if (evt.getHand() != InteractionHand.MAIN_HAND) return;
        if (!player.getMainHandItem().isEmpty()) return;

        if (evt.getLevel().isClientSide()) {
            // 客户端：取消原版破坏表现，并通知服务端执行绑定
            if (!ClientBindState.isActive()) return;
            evt.setCanceled(true);
            evt.setCancellationResult(InteractionResult.CONSUME);
            ModMessages.INSTANCE.sendToServer(
                    new BindClickC2SPacket(evt.getPos(), evt.getFace(), false, Screen.hasControlDown()));
        } else {
            // 服务端：玩家处于绑定模式时空手左键不能拆方块（绑定动作由 BindClickC2SPacket 完成）
            if (!(player instanceof ServerPlayer sp)) return;
            if (!ChannelManager.INSTANCE.isBindModeActive(sp)) return;
            evt.setCanceled(true);
            evt.setCancellationResult(InteractionResult.CONSUME);
        }
    }

    /** 右键：绑定模式下空手点击 = 设置输入（INPUT）。两侧都取消原版交互，避免打开容器 GUI。 */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock evt) {
        Player player = evt.getEntity();
        if (player == null) return;
        if (evt.getHand() != InteractionHand.MAIN_HAND) return;
        if (!player.getMainHandItem().isEmpty()) return;

        if (evt.getLevel().isClientSide()) {
            // 客户端：取消原版交互，并通知服务端执行绑定
            if (!ClientBindState.isActive()) return;
            evt.setCanceled(true);
            evt.setCancellationResult(InteractionResult.CONSUME);
            ModMessages.INSTANCE.sendToServer(
                    new BindClickC2SPacket(evt.getPos(), evt.getFace(), true, Screen.hasControlDown()));
        } else {
            // 服务端：玩家处于绑定模式时空手右键不能与方块交互（绑定动作由 BindClickC2SPacket 完成）
            if (!(player instanceof ServerPlayer sp)) return;
            if (!ChannelManager.INSTANCE.isBindModeActive(sp)) return;
            evt.setCanceled(true);
            evt.setCancellationResult(InteractionResult.CONSUME);
        }
    }
}
