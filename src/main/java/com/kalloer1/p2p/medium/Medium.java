package com.kalloer1.p2p.medium;

import com.kalloer1.p2p.transfer.TransferCapabilityCache;
import com.kalloer1.p2p.channel.Channel;
import com.kalloer1.p2p.channel.Member;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;

/**
 * 一种可传输的介质：频道的"类型"。由主模组（item/fluid/energy）或附属（mekanism:gas 等）
 * 通过 {@link RegisterMediumEvent} 注册到 {@link Mediums}。
 *
 * <p>不再是枚举——Java 枚举无法被附属扩展。频道的存档/网络同步里存的是 {@link #id} 字符串；
 * 附属缺失时 {@link Mediums#resolve} 返回"未知介质"占位（不传输、可显示），装回附属即恢复。
 */
public final class Medium {
    /** 规范 id：内置为 "item"/"fluid"/"energy"，附属为 namespaced（如 "mekanism:gas"）。 */
    public final String id;
    public final int argbColor;
    public final String langKey;
    /** 空闲退避上限（tick）：连续量介质（能量/热量）接近即时，上限更小。 */
    public final int backoffMaxTicks;
    private final MediumTransfer transfer;

    public Medium(String id, int argbColor, String langKey, int backoffMaxTicks, MediumTransfer transfer) {
        this.id = id;
        this.argbColor = argbColor;
        this.langKey = langKey;
        this.backoffMaxTicks = backoffMaxTicks;
        this.transfer = transfer;
    }

    public Component displayName() {
        return Component.translatable(langKey);
    }

    /** 服务端每 tick 由 TransferEngine 调用；"未知介质"占位的回调返回 0。 */
    public int transfer(TransferCapabilityCache cache, Channel channel, Map<ResourceLocation, Level> levels,
                        List<Member> inputs, List<Member> outputs, int rate, int globalTick) {
        return transfer == null ? 0 : transfer.transfer(cache, channel, levels, inputs, outputs, rate, globalTick);
    }

    @Override
    public String toString() {
        return "Medium[" + id + "]";
    }
}
