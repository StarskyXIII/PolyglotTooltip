package com.starskyxiii.polyglottoolchainprobe;

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ProbePolicy {
    private static final AtomicBoolean MIXIN_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean MAPPED_CALL_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean POLICY_LOGGED = new AtomicBoolean();

    private ProbePolicy() {
    }

    public static void entrypoint(String minecraftVersion) {
        System.out.println("[PolyglotToolchainProbe] ENTRYPOINT " + minecraftVersion);
    }

    public static void mixinApplied(String minecraftVersion) {
        if (MIXIN_LOGGED.compareAndSet(false, true)) {
            System.out.println("[PolyglotToolchainProbe] MIXIN_APPLIED " + minecraftVersion);
        }
    }

    public static void mappedCallOk(String minecraftVersion) {
        if (MAPPED_CALL_LOGGED.compareAndSet(false, true)) {
            System.out.println("[PolyglotToolchainProbe] MAPPED_CALL_OK " + minecraftVersion);
        }
    }

    public static void verifyPolicy(String minecraftVersion) {
        String converted = ZhConverterUtil.toSimple("臺灣");
        if (!"台湾".equals(converted)) {
            throw new IllegalStateException("Unexpected OpenCC conversion: " + converted);
        }
        if (POLICY_LOGGED.compareAndSet(false, true)) {
            System.out.println("[PolyglotToolchainProbe] POLICY_OK " + minecraftVersion);
        }
    }
}
