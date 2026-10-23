package com.kalloer1.p2p.client;

import com.kalloer1.p2p.client.screen.P2PTheme;
import com.kalloer1.p2p.client.screen.theme.ThemeMode;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 客户端配置：保存 UI 主题偏好设置。
 */
public final class P2PClientConfig {
    public static final ModConfigSpec.EnumValue<ThemeMode> themeMode;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("ui");
        themeMode = b.comment("UI theme mode: LIGHT or DARK. Default: LIGHT.")
                .defineEnum("themeMode", ThemeMode.LIGHT);
        b.pop();
        SPEC = b.build();
    }

    private P2PClientConfig() {}

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
    }

    public static void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            P2PTheme.setTheme(themeMode.get());
        }
    }
}
