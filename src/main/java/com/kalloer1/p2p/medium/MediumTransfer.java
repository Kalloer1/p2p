package com.kalloer1.p2p.medium;

import com.kalloer1.p2p.transfer.TransferCapabilityCache;
import com.kalloer1.p2p.channel.Channel;
import com.kalloer1.p2p.channel.Member;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;

/**
 * 一种介质的传输例程（服务端每 tick 调用）。签名与现有 ItemTransfer/FluidTransfer/
 * EnergyTransfer.transfer 完全一致，直接方法引用接入。返回本轮实际移动量（驱动退避）。
 */
@FunctionalInterface
public interface MediumTransfer {
    int transfer(TransferCapabilityCache cache, Channel channel, Map<ResourceLocation, Level> levels,
                 List<Member> inputs, List<Member> outputs, int rate, int globalTick);
}
