package com.starskyxiii.polyglottooltip.name.prebuilt;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory prebuilt secondary name cache for ManaMetal items.
 * Replaced atomically on rebuild; read lock-free via volatile.
 */
public final class PrebuiltSecondaryNameCache {

    // Replaced atomically on rebuild; safe for concurrent reads
    private static volatile Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> cache =
        Collections.emptyMap();

    private PrebuiltSecondaryNameCache() {}

    /** Replaces the entire in-memory cache (called after a successful rebuild or load). */
    public static void replace(Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> newCache) {
        Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> copy =
            new HashMap<PrebuiltSecondaryNameIndexKey, Map<String, String>>(newCache);
        cache = Collections.unmodifiableMap(copy);
    }

    /**
     * Returns the cached secondary display name, or null if not found.
     * Safe to call from tooltip/render paths — no language switching.
     */
    public static String lookup(String registryName, int damage, String languageCode) {
        if (registryName == null || languageCode == null) {
            return null;
        }
        Map<String, String> langs = cache.get(new PrebuiltSecondaryNameIndexKey(registryName, damage));
        if (langs == null) {
            return null;
        }
        String name = langs.get(languageCode);
        return (name == null || name.isEmpty()) ? null : name;
    }

    public static boolean isEmpty() {
        return cache.isEmpty();
    }
}
