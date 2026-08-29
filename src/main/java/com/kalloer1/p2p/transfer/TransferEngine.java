package com.kalloer1.p2p.transfer;

import com.kalloer1.p2p.channel.Channel;
import com.kalloer1.p2p.channel.ChannelManager;
import com.kalloer1.p2p.channel.ChannelType;
import com.kalloer1.p2p.channel.Member;
import com.kalloer1.p2p.channel.Role;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side tick driver. Each channel is processed no more often than its own cooldown:
 * {@code max(speed, adaptiveBackoff)} ticks since its last run. A channel that actively moves
 * items stays at its base {@code speed}; an idle channel (nothing to transfer) is progressively
 * backed off so the engine spends its per-tick budget on channels that are actually doing work.
 *
 * <p>All channels in a tick share one {@link TransferCapabilityCache}, so a frequently-linked
 * neighbour resolves its capability at most once per tick instead of once per member visit.
 */
public final class TransferEngine {
    public static final TransferEngine INSTANCE = new TransferEngine();

    private static final float BACKOFF_MULTIPLIER = 1.6f;           // idle channel grows backoff x1.6 (wake less often sooner)
    private static final float BACKOFF_MAX_TICKS = 20f;             // cap for item/fluid channels (re-awaken latency ≤ 1s)
    private static final float BACKOFF_MAX_TICKS_ENERGY = 5f;       // energy is near-instant: small cap
    private static final float BACKOFF_FAST_FORWARD_RATIO = 0.6f;   // once backoff reaches 60% of cap, jump straight to cap

    private int globalTick = 0;

    private TransferEngine() {}

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        globalTick++;
        MinecraftServer server = event.getServer();
        if (server == null) return;

        Map<ResourceLocation, Level> levels = new HashMap<>();
        for (ServerLevel sl : server.getAllLevels()) levels.put(sl.dimension().location(), sl);

        TransferCapabilityCache cache = new TransferCapabilityCache();

        for (Channel channel : ChannelManager.INSTANCE.getAllChannels()) {
            if (channel.rate <= 0) continue;
            if (!isDue(channel)) continue;
            if (!redstoneGateOpen(channel, levels)) continue;

            List<Member> inputs = new ArrayList<>();
            List<Member> outputs = new ArrayList<>();
            for (Member m : channel.members) {
                if (m.role == Role.INPUT) inputs.add(m);
                else outputs.add(m);
            }
            if (inputs.isEmpty() || outputs.isEmpty()) continue;

            int moved = switch (channel.type) {
                case ITEM -> ItemTransfer.transfer(cache, channel, levels, inputs, outputs, channel.rate, globalTick);
                case FLUID -> FluidTransfer.transfer(cache, channel, levels, inputs, outputs, channel.rate, globalTick);
                case ENERGY -> EnergyTransfer.transfer(cache, channel, levels, inputs, outputs, channel.rate);
            };
            updateBackoff(channel, moved > 0);
        }
    }

    /** A channel is due when at least {@code max(speed, backoff)} ticks have elapsed since its last run. */
    private boolean isDue(Channel channel) {
        int speed = Math.max(1, channel.speed);
        long effectiveDelay = Math.max((long) speed, (long) Math.ceil(channel.backoffTicks));
        long elapsed = (long) globalTick - channel.lastRunTick;
        return elapsed >= effectiveDelay;
    }

    /**
     * Records the run and updates the adaptive idle backoff.
     * - active (moved > 0): decay backoff toward the base speed (but never below it)
     * - idle (moved == 0): grow backoff (x1.6) up to the type's cap; once it reaches 60% of the
     *   cap it fast-forwards straight to the cap so an empty channel stops consuming tick budget.
     */
    private void updateBackoff(Channel channel, boolean success) {
        channel.lastRunTick = globalTick;
        int speed = Math.max(1, channel.speed);
        float maxBackoff = (channel.type == ChannelType.ENERGY) ? BACKOFF_MAX_TICKS_ENERGY : BACKOFF_MAX_TICKS;
        if (success) {
            // Hard reset: a channel that moved items this round returns to FULL SPEED next tick.
            // (The old /3 decay made a woken channel ramp up over several rounds; Pipez is full
            // speed from tick one and we must match that — no start-up ramp.)
            channel.backoffTicks = 0f;
        } else {
            float cur = Math.max(channel.backoffTicks, speed);
            if (cur <= speed) {
                float next = (speed + 1.05f) / BACKOFF_MULTIPLIER;
                channel.backoffTicks = Math.min(maxBackoff, Math.max(speed + 0.1f, next));
            } else if (cur >= maxBackoff * BACKOFF_FAST_FORWARD_RATIO) {
                // Idle long enough: jump straight to the cap so the channel stops waking up
                // and the engine spends its tick budget on channels that actually move things.
                channel.backoffTicks = maxBackoff;
            } else {
                channel.backoffTicks = Math.min(maxBackoff, cur * BACKOFF_MULTIPLIER);
            }
        }
    }

    private boolean anyInputPowered(Map<ResourceLocation, Level> levels, Channel channel) {
        for (Member m : channel.members) {
            if (m.role != Role.INPUT) continue;
            Level level = levels.get(m.dim);
            if (level == null) continue;
            if (level.hasNeighborSignal(m.pos)) return true;
        }
        return false;
    }

    /**
     * Four-mode redstone gate (aligned with LogisticsNetworks): ALWAYS always passes,
     * HIGH needs any input powered, LOW needs no input powered, NEVER never passes.
     */
    private boolean redstoneGateOpen(Channel channel, Map<ResourceLocation, Level> levels) {
        return switch (channel.redstoneMode) {
            case ALWAYS -> true;
            case NEVER -> false;
            case HIGH -> anyInputPowered(levels, channel);
            case LOW -> !anyInputPowered(levels, channel);
        };
    }
}