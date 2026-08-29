package com.kalloer1.p2p.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Client-only key bindings. Registered on the MOD event bus. */
public final class Keybinds {
    public static final KeyMapping OPEN_GUI = new KeyMapping(
            "key.p2p.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.p2p"
    );

    private Keybinds() {}
}
