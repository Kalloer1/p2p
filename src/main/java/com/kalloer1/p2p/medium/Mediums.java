package com.kalloer1.p2p.medium;

import com.kalloer1.p2p.Lang;
import com.kalloer1.p2p.transfer.EnergyTransfer;
import com.kalloer1.p2p.transfer.FluidTransfer;
import com.kalloer1.p2p.transfer.ItemTransfer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 介质注册表（双端一致）。内置三种介质在 {@link RegisterMediumEvent} 里由主模组自己注册；
 * 附属模组监听同一事件注册它们的介质（如 mekanism:gas / mekanism:heat）。
 *
 * <p>事件在 {@code FMLLoadCompleteEvent} 的 enqueueWork 里派发——此时所有 mod 的 mod-bus
 * 监听器都已注册完毕，不存在 mod 顺序问题。双端各自派发，所以客户端注册表与服务端天然一致，
 * 无需网络同步介质清单。
 */
public final class Mediums {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final Map<String, Medium> ALL = new LinkedHashMap<>();

    public static final Medium ITEM = register(new Medium("item", 0xFF8a5a2b, Lang.UI_TYPE_ITEM, 20, ItemTransfer::transfer));
    public static final Medium FLUID = register(new Medium("fluid", 0xFF2f5aa8, Lang.UI_TYPE_FLUID, 20, FluidTransfer::transfer));
    public static final Medium ENERGY = register(new Medium("energy", 0xFF9c3327, Lang.UI_TYPE_ENERGY, 5, EnergyTransfer::transfer));

    private Mediums() {}

    /** 注册一种介质；id 冲突时保留先注册者并告警（返回已存在的或新注册的）。 */
    public static synchronized Medium register(Medium medium) {
        Medium existing = ALL.get(medium.id);
        if (existing != null) {
            LOGGER.log(Level.WARN, "[p2p] medium id '{}' registered twice ({} wins)", medium.id, existing.id);
            return existing;
        }
        ALL.put(medium.id, medium);
        LOGGER.info("[p2p] medium registered: {} ({})", medium.id, medium.langKey);
        return medium;
    }

    public static List<Medium> all() {
        return Collections.unmodifiableList(new ArrayList<>(ALL.values()));
    }

    /** 精确取已注册介质；未注册返回 null（GUI 应改用 {@link #resolve} 拿"未知介质"占位）。 */
    public static synchronized Medium get(String id) {
        return ALL.get(id);
    }

    /**
     * 解析频道上存的介质 id：老存档的枚举名（"ITEM"/"FLUID"/"ENERGY"）归一为小写；
     * 未知 id（附属缺失）返回一个**带原 id** 的"未知介质"占位——不传输、显示为灰色
     * "未知介质"，且序列化回写原 id，装回附属后频道自动恢复。
     */
    public static synchronized Medium resolve(String raw) {
        if (raw == null || raw.isEmpty()) return all().get(0);
        String id = normalize(raw);
        Medium m = ALL.get(id);
        return m != null ? m : new Medium(id, 0xFF6b6b6b, Lang.UI_TYPE_UNKNOWN, 20, null);
    }

    private static String normalize(String raw) {
        if (raw.indexOf(':') >= 0) return raw;
        return switch (raw) {
            case "ITEM" -> "item";
            case "FLUID" -> "fluid";
            case "ENERGY" -> "energy";
            default -> raw.toLowerCase();
        };
    }
}
