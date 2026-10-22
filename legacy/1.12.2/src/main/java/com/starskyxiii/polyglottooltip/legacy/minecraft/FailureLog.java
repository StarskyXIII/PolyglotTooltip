package com.starskyxiii.polyglottooltip.legacy.minecraft;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.starskyxiii.polyglottooltip.legacy.PolyglotTooltipMod;

final class FailureLog {
    private static final Logger LOGGER = LogManager.getLogger(PolyglotTooltipMod.MOD_ID);
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    private FailureLog() {}

    static void warn(String key, String message, Throwable error) {
        if (SEEN.add("warn:" + key)) {
            LOGGER.warn(message, error);
        }
    }

    static void debug(String key, String message, Throwable error) {
        if (SEEN.add("debug:" + key)) {
            LOGGER.debug(message, error);
        }
    }

    static String failureKey(Object owner, Throwable error) {
        String ownerName = owner == null ? "null" : owner.getClass().getName();
        return ownerName + "|" + error.getClass().getName();
    }
}
