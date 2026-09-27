package com.kalloer1.p2p;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

/**
 * 服务端配置（common）。区块强制加载默认整体关闭——这是有服务器性能代价的功能，
 * 必须由服主在配置文件里显式打开，而不是玩家随手就能用。
 */
public final class P2PConfig {
    /** 区块加载总开关（默认关）：关掉时界面上的「区块加载」按钮点击会被服务端拒绝。 */
    public static ModConfigSpec.BooleanValue chunkLoadEnabled;
    /** 允许非创造玩家使用区块加载（默认关）：总开关打开后，创造玩家始终可用，生存玩家看这一项。 */
    public static ModConfigSpec.BooleanValue chunkLoadAllowSurvival;

    public static final ModConfigSpec SPEC;

    private P2PConfig() {}

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("chunkload");
        chunkLoadEnabled = b.comment("Master switch for per-network chunk loading. Default: off.")
                .define("enabled", false);
        chunkLoadAllowSurvival = b.comment("Allow non-creative players to toggle network chunk loading. Default: off.")
                .define("allowSurvival", false);
        b.pop();
        SPEC = b.build();
    }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC);
    }

    /** 服务端权限判定：总开关 + （创造 || 开放给生存）。 */
    public static boolean canUseChunkLoad(boolean creative) {
        return chunkLoadEnabled.get() && (creative || chunkLoadAllowSurvival.get());
    }
}
