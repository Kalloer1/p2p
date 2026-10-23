package com.kalloer1.p2p.harness;

import com.kalloer1.p2p.client.ClientChannelCache;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * fake 依赖面：
 * <ul>
 *   <li>语言：构造 zh_cn（原版 + 模组，en_us 兜底）合并表，通过 {@link Language#inject} 注入，
 *       让 {@code Component.getString()} 与离线渲染走同一份翻译；</li>
 *   <li>Minecraft：Mockito mock + 反射写入 {@code instance} 单例（P2PFlatButton 用
 *       {@code Minecraft.getInstance().font}）；level 字段注入 mock ClientLevel
 *       （overworld、区块视为已加载、方块为箱子 → 端点走 VALID + 物品图标路径）；</li>
 *   <li>网络：mockStatic(ModMessages) 把 sendToServer 变 no-op；</li>
 *   <li>数据：向 ClientChannelCache 预灌 NBT 快照。</li>
 * </ul>
 */
public final class HEnv implements AutoCloseable {

    public final Minecraft minecraft;
    private final MockedStatic<com.kalloer1.p2p.network.ModMessages> netMock;
    private final MockedStatic<net.neoforged.fml.loading.LoadingModList> fmlMock;

    public HEnv(Path vanillaAssets, FakeFont fakeFont) throws Exception {
        injectLanguage(vanillaAssets);

        // NeoForge 的 FeatureFlags.<clinit> 会经 FeatureFlagLoader 查询 FML 上下文；
        // 离线 harness 没有 FML，给一个空 ModList 让其跳过
        fmlMock = Mockito.mockStatic(net.neoforged.fml.loading.LoadingModList.class);
        net.neoforged.fml.loading.LoadingModList emptyModList = mock(net.neoforged.fml.loading.LoadingModList.class);
        when(emptyModList.getModFiles()).thenReturn(java.util.List.of());
        fmlMock.when(net.neoforged.fml.loading.LoadingModList::get).thenReturn(emptyModList);

        // DataFixers.<clinit> 需要 SharedConstants 当前版本（1.21.1 数据版本 3955）
        var worldVersion = mock(net.minecraft.WorldVersion.class);
        when(worldVersion.getDataVersion()).thenReturn(new net.minecraft.world.level.storage.DataVersion(3955));
        net.minecraft.SharedConstants.setVersion(worldVersion);

        net.minecraft.server.Bootstrap.bootStrap();

        RegistryAccess registryAccess = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

        Window window = mock(Window.class);
        when(window.getGuiScaledWidth()).thenReturn(960);
        when(window.getGuiScaledHeight()).thenReturn(540);
        when(window.getGuiScale()).thenReturn(2.0);
        when(window.getHeight()).thenReturn(1080);

        ClientLevel level = mock(ClientLevel.class);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getBlockState(any()))
                .thenReturn(Blocks.CHEST.defaultBlockState());
        when(level.registryAccess()).thenReturn(registryAccess);

        minecraft = mock(Minecraft.class);
        when(minecraft.getWindow()).thenReturn(window);
        when(minecraft.getGuiSprites()).thenReturn(null);
        when(minecraft.getLastInputType()).thenReturn(InputType.NONE);
        when(minecraft.getNarrator()).thenReturn(mock(net.minecraft.client.GameNarrator.class));
        // 字段注入（Mockito stub 管不到 public final 字段）：font / level / 单例
        setField(Minecraft.class, minecraft, "font", fakeFont);
        setField(Minecraft.class, minecraft, "level", level);
        setField(Minecraft.class, minecraft, "instance", minecraft);

        netMock = Mockito.mockStatic(com.kalloer1.p2p.network.ModMessages.class);
        netMock.when(() -> com.kalloer1.p2p.network.ModMessages.sendToServer(any())).thenAnswer(inv -> null);
    }

    public void installSnapshot(CompoundTag data) {
        ClientChannelCache.INSTANCE.setData(data);
        ClientChannelCache.INSTANCE.setCurrentNetworkId(-1);
        ClientChannelCache.INSTANCE.setSelectedChannelId(-1);
        ClientChannelCache.INSTANCE.setSelectedGroupId(-1);
        ClientChannelCache.INSTANCE.getOpenGroups().clear();
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    // ==================== 语言 ====================

    private static void injectLanguage(Path vanillaAssets) throws Exception {
        Map<String, String> map = new HashMap<>();
        loadClasspath(map, "/assets/minecraft/lang/en_us.json");
        try {
            loadFile(map, vanillaAssets.resolve("minecraft/lang/zh_cn.json"));
        } catch (Exception e) {
            HLog.warn("vanilla zh_cn.json missing: " + e);
        }
        loadClasspath(map, "/assets/p2p/lang/zh_cn.json");
        net.minecraft.locale.Language.inject(new HLanguage(map));
        HLog.info("language injected: " + map.size() + " keys");
    }

    private static void loadClasspath(Map<String, String> map, String resource) throws Exception {
        try (InputStream in = HEnv.class.getResourceAsStream(resource)) {
            if (in == null) {
                HLog.warn("classpath resource missing: " + resource);
                return;
            }
            Language.loadFromJson(in, map::put);
        }
    }

    private static void loadFile(Map<String, String> map, Path p) throws Exception {
        try (InputStream in = Files.newInputStream(p)) {
            Language.loadFromJson(in, map::put);
        }
    }

    private static final class HLanguage extends Language {
        private final Map<String, String> map;

        HLanguage(Map<String, String> map) {
            this.map = map;
        }

        @Override
        public String getOrDefault(String key, String defaultValue) {
            return map.getOrDefault(key, defaultValue);
        }

        @Override
        public boolean has(String id) {
            return map.containsKey(id);
        }

        @Override
        public boolean isDefaultRightToLeft() {
            return false;
        }

        @Override
        public FormattedCharSequence getVisualOrder(FormattedText text) {
            return sink -> text.visit((style, s) -> StringDecomposer.iterateFormatted(s, style, sink)
                            ? Optional.empty() : FormattedText.STOP_ITERATION, Style.EMPTY)
                    .isPresent();
        }
    }

    @Override
    public void close() {
        netMock.close();
        fmlMock.close();
    }
}
