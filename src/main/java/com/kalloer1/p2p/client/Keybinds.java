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

    /** 进入 / 退出"绑定模式"：开启后空手右键=输入、左键=输出、Ctrl=连锁。 */
    public static final KeyMapping BIND_MODE = new KeyMapping(
            "key.p2p.bindmode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.p2p"
    );

    private Keybinds() {}
}
