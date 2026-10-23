package com.kalloer1.p2p.harness;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 轻量日志：只打 harness 自己的运行信息，去重一次性告警。 */
final class HLog {
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    static void warn(String msg) {
        System.err.println("[guirender] " + msg);
    }

    static void info(String msg) {
        System.out.println("[guirender] " + msg);
    }

    static void once(String msg) {
        if (SEEN.add(msg)) info(msg);
    }
}
