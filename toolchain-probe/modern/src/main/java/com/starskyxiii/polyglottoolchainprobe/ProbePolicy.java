package com.starskyxiii.polyglottoolchainprobe;

import com.github.houbb.opencc4j.util.ZhConverterUtil;

public final class ProbePolicy {
    private ProbePolicy() {
    }

    public static String startupMarker(String loader, String minecraftVersion) {
        String converted = ZhConverterUtil.toSimple("臺灣");
        if (!"台湾".equals(converted)) {
            throw new IllegalStateException("OpenCC conversion failed: " + converted);
        }
        return "[PolyglotToolchainProbe] POLICY_OK " + loader + " " + minecraftVersion;
    }
}
