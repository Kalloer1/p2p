package com.kalloer1.p2p.client;

import com.kalloer1.p2p.network.BindModeC2SPacket;
import com.kalloer1.p2p.network.ModMessages;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 绑定模式的客户端镜像状态。服务端才是权威（{@link BindModeC2SPacket} 会同步开关），
 * 但这里保存一份本地副本，用来取消原版点击、画高亮与 HUD —— 这些都发生在客户端，
 * 且不能等服务端回包（点击是即时的）。
 */
public final class ClientBindState {
    private static boolean active = false;
    private static int boundChannel = -1;

    private ClientBindState() {}

    public static boolean isActive() { return active; }

    /** 当前绑定频道（服务端同步过来；-1 表示还没选）。 */
    public static int getBoundChannel() { return boundChannel; }

    /** 服务端推送网络快照时调用，把"当前绑定频道"同步到客户端。 */
    public static void setBoundChannel(int id) { boundChannel = id; }

    /** 切换绑定模式：翻本地标记 + 通知服务端 + 给玩家一句提示。 */
    public static void toggle() {
        active = !active;
        ModMessages.INSTANCE.sendToServer(new BindModeC2SPacket(active));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (active) {
            String ch = boundChannel >= 0 ? "（当前频道 #" + boundChannel + "）" : "（还没选当前频道，在 P2P 界面点『设为当前绑定频道』）";
            mc.player.displayClientMessage(
                    Component.literal("绑定模式已开启：空手右键=输入 / 左键=输出，按住 Ctrl=连锁 " + ch)
                            .withStyle(ChatFormatting.GREEN), true);
        } else {
            mc.player.displayClientMessage(
                    Component.literal("绑定模式已关闭").withStyle(ChatFormatting.YELLOW), true);
        }
    }
}
