package com.kalloer1.p2p.network;

import com.kalloer1.p2p.P2P;
import com.kalloer1.p2p.channel.Channel;
import com.kalloer1.p2p.channel.ChannelManager;
import com.kalloer1.p2p.channel.Member;
import com.kalloer1.p2p.channel.Role;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> Server: 绑定模式下的一次点击。
 * <ul>
 *   <li>{@code rightButton=true}（右键）= 设为抽取端 INPUT；{@code false}（左键）= 设为注入端 OUTPUT。</li>
 *   <li>{@code ctrl=false}：单击该面 = 绑定 / 再点一次解绑。</li>
 *   <li>{@code ctrl=true}：以该方块为根做 6 邻接洪水填充，连锁绑定 / 连锁解绑周围相同方块。</li>
 * </ul>
 * 携带实时 Ctrl 状态，因为 Forge 1.20.1 的交互事件没有提供修饰键 API，服务端读不到。
 */
public class BindClickC2SPacket {
    private final BlockPos pos;
    private final Direction face;
    private final boolean rightButton;
    private final boolean control;

    public BindClickC2SPacket(BlockPos pos, Direction face, boolean rightButton, boolean control) {
        this.pos = pos;
        this.face = face;
        this.rightButton = rightButton;
        this.control = control;
    }

    public BindClickC2SPacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readEnum(Direction.class), buf.readBoolean(), buf.readBoolean());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeEnum(face);
        buf.writeBoolean(rightButton);
        buf.writeBoolean(control);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ChannelManager cm = ChannelManager.INSTANCE;
            // 只有真正处于绑定模式的玩家才能改动端点，避免被伪造包利用
            if (!cm.isBindModeActive(player)) {
                P2P.LOGGER.warn("[p2p] BindClick 被忽略：{} 未处于绑定模式", player.getName().getString());
                return;
            }
            int channelId = cm.getBoundChannel(player);
            if (channelId < 0) {
                player.displayClientMessage(Component.literal("还没有设置当前绑定频道：在 P2P 界面选中频道后点『设为当前绑定频道』。")
                        .withStyle(ChatFormatting.YELLOW), true);
                return;
            }
            Channel channel = cm.getChannel(channelId);
            if (channel == null) {
                player.displayClientMessage(Component.literal("当前绑定频道 #" + channelId + " 已不存在。")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            if (!cm.canEditChannel(player, channel)) {
                player.displayClientMessage(Component.literal("频道 #" + channelId + " 属于别人的网络，你没有改动权限。")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }

            Role role = rightButton ? Role.INPUT : Role.OUTPUT;
            Level level = player.level();
            P2P.LOGGER.info("[p2p] BindClick: player={} pos={} face={} button={} ctrl={} channel={} role={}",
                    player.getName().getString(), pos, face, rightButton ? "右键" : "左键", control, channelId, role);

            if (control) {
                // 该面已绑定 -> 连锁解绑；否则连锁绑定
                ResourceLocation dim = level.dimension().location();
                boolean alreadyBound = channel.members.stream()
                        .anyMatch(m -> m.pos.equals(pos) && m.face == face && m.dim.equals(dim));
                if (alreadyBound) cm.unbindChain(level, player, pos, face, channelId, dim);
                else cm.bindChain(level, player, pos, face, channelId, role);
            } else {
                cm.toggleEndpoint(level, player, pos, face, channelId, role);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
