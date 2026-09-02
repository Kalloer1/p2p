package com.kalloer1.p2p.client.screen;

import com.kalloer1.p2p.Lang;
import com.kalloer1.p2p.channel.ChannelType;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * UI-side medium registry. Keeps the list of transport media that the player can choose
 * when creating a channel, plus their display name and tint color.
 *
 * The list is intentionally mutable so future addon mods (e.g. mana / gas) can register
 * new media without touching this class. The actual transfer behaviour for those extra
 * media still requires a matching ChannelType / transfer engine on the server.
 */
public final class MediumRegistry {

    public record Medium(ChannelType type, String langKey, int argbColor) {}

    private static final List<Medium> ALL = new ArrayList<>();

    public static final Medium ITEM   = register(new Medium(ChannelType.ITEM,   Lang.UI_TYPE_ITEM,   0xFF8a5a2b));
    public static final Medium FLUID  = register(new Medium(ChannelType.FLUID,  Lang.UI_TYPE_FLUID,  0xFF2f5aa8));
    public static final Medium ENERGY = register(new Medium(ChannelType.ENERGY, Lang.UI_TYPE_ENERGY, 0xFF9c3327));

    private static Medium register(Medium m) {
        ALL.add(m);
        return m;
    }

    public static List<Medium> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static Medium of(ChannelType type) {
        for (Medium m : ALL) if (m.type() == type) return m;
        return ITEM;
    }

    public static Component nameOf(ChannelType type) {
        return Lang.t(of(type).langKey());
    }

    public static int colorOf(ChannelType type) {
        return of(type).argbColor();
    }
}
