package com.kalloer1.p2p.harness;

import com.kalloer1.p2p.client.screen.ChannelScreen;
import com.kalloer1.p2p.client.screen.P2PTheme;
import com.kalloer1.p2p.client.screen.theme.ThemeMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 离线渲染入口（不启动游戏）：
 * <pre>gradlew renderChannelScreen [-Pguirender.out=输出目录]</pre>
 * 真实 ChannelScreen.init/render 跑在 CanvasGuiGraphics 上，分别输出浅色与深色主题 PNG。
 */
public final class RenderMain {

    public static void main(String[] args) throws Exception {
        Path assets = Paths.get(System.getProperty("guirender.assets", "build/gui-render/va/assets"));
        Path outDir = Paths.get(System.getProperty("guirender.out", "build/gui-render"));
        Files.createDirectories(outDir);

        HLog.info("loading font from " + assets);
        HFont hfont = new HFont(assets);
        hfont.loadDefaultFont();
        FakeFont fakeFont = new FakeFont(hfont);

        try (HEnv env = new HEnv(assets, fakeFont)) {
            env.installSnapshot(HSnap.build());

            // 1. 浅色主题
            P2PTheme.setTheme(ThemeMode.LIGHT);
            HGuiGraphics ggLight = new HGuiGraphics(env.minecraft, new HCanvas(960, 540), hfont, assets.getParent());
            ChannelScreen screenLight = new ChannelScreen();
            screenLight.init(env.minecraft, 960, 540);
            screenLight.render(ggLight, -1, -1, 0.0f);
            Path outLight = outDir.resolve("channelscreen_1211_light.png");
            ggLight.canvas.savePng(outLight.toFile());
            HLog.info("saved light: " + outLight);

            // 同时保留默认 1080p 文件名
            Path outDefault = outDir.resolve("channelscreen_1211_1080p.png");
            ggLight.canvas.savePng(outDefault.toFile());

            // 2. 深色主题
            P2PTheme.setTheme(ThemeMode.DARK);
            HGuiGraphics ggDark = new HGuiGraphics(env.minecraft, new HCanvas(960, 540), hfont, assets.getParent());
            ChannelScreen screenDark = new ChannelScreen();
            screenDark.init(env.minecraft, 960, 540);
            screenDark.render(ggDark, -1, -1, 0.0f);
            Path outDark = outDir.resolve("channelscreen_1211_dark.png");
            ggDark.canvas.savePng(outDark.toFile());
            HLog.info("saved dark: " + outDark);
        }
    }
}
