package com.kalloer1.p2p.api;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 过滤页扩展注册表（按 medium id 键控，纯客户端使用）。
 * 附属须仅在客户端调用 {@link #register}：独立客户端入口（JavaClient），或单 jar
 * 附属在公共构造器里以 {@code FMLEnvironment.dist == Dist.CLIENT} 守卫后注册——
 * 注册方法体惰性解析，服务端因此永不加载 GUI 扩展类；重复注册同一 id
 * 以警告并保留先注册者。
 */
public final class FilterPageExtensions {
    private static final Map<String, FilterPageExtension> ALL = new ConcurrentHashMap<>();
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();

    private FilterPageExtensions() {}

    /** 注册某介质的过滤页扩展；id 冲突时保留先注册者。 */
    public static void register(String mediumId, FilterPageExtension extension) {
        FilterPageExtension previous = ALL.putIfAbsent(mediumId, extension);
        if (previous != null)
            LOGGER.warn("[p2p] filter page extension for medium '{}' registered twice ({}) ignored",
                    mediumId, extension.getClass().getName());
        else
            LOGGER.info("[p2p] filter page extension registered for medium '{}'", mediumId);
    }

    @Nullable
    public static FilterPageExtension get(@Nullable String mediumId) {
        return mediumId == null ? null : ALL.get(mediumId);
    }
}
