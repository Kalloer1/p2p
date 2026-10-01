package com.kalloer1.p2p.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * Client -> Server: toggle bind mode on/off.
 * The server stores this flag to validate subsequent {@link BindClickC2SPacket}s
 * (prevents forged click packets when mode is inactive).
 */
public class BindModeC2SPacket {
    private final boolean active;

    public BindModeC2SPacket(boolean active) {
        this.active = active;
    }

    public BindModeC2SPacket(FriendlyByteBuf buf) {
        this.active = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
    }

    public boolean active() {
        return active;
    }

    public void handle(Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ModMessages.handleBindMode(this, ctx.get().getSender()));
    }
}
