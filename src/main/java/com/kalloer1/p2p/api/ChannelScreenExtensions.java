package com.kalloer1.p2p.api;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 频道详情面板扩展注册表（按 medium id 键控，纯客户端使用）。
 * 附属须仅在客户端调用 {@link #register}：独立客户端入口（JavaClient），或单 jar
 * 附属在公共构造器里以 {@code FMLEnvironment.dist == Dist.CLIENT} 守卫后注册——
 * 注册方法体惰性解析，服务端因此永不加载 GUI 扩展类。未注册的介质
 * 本体 ChannelScreen 行为与无扩展时完全一致。重复注册同一 id 以警告并保留先注册者。
 */
public final class ChannelScreenExtensions {
    private static final Map<String, ChannelScreenExtension> ALL = new ConcurrentHashMap<>();
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();

    private ChannelScreenExtensions() {}

    /** 注册某介质的详情面板扩展；id 冲突时保留先注册者。 */
    public static void register(String mediumId, ChannelScreenExtension extension) {
        ChannelScreenExtension previous = ALL.putIfAbsent(mediumId, extension);
        if (previous != null)
            LOGGER.warn("[p2p] channel screen extension for medium '{}' registered twice ({}) ignored",
                    mediumId, extension.getClass().getName());
        else
            LOGGER.info("[p2p] channel screen extension registered for medium '{}'", mediumId);
    }

    @Nullable
    public static ChannelScreenExtension get(@Nullable String mediumId) {
        return mediumId == null ? null : ALL.get(mediumId);
    }
}
