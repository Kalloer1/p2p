package com.kalloer1.p2p.client;

import com.kalloer1.p2p.client.screen.P2PTheme;
import com.kalloer1.p2p.client.screen.theme.ThemeMode;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 客户端配置：保存 UI 主题偏好设置（Forge 1.20.1）。
 */
public final class P2PClientConfig {
    public static final ForgeConfigSpec.EnumValue<ThemeMode> themeMode;
    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("ui");
        themeMode = b.comment("UI theme mode: LIGHT or DARK. Default: LIGHT.")
                .defineEnum("themeMode", ThemeMode.LIGHT);
        b.pop();
        SPEC = b.build();
    }

    private P2PClientConfig() {}

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(P2PClientConfig::onConfigLoad);
    }

    public static void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            P2PTheme.setTheme(themeMode.get());
        }
    }
}
