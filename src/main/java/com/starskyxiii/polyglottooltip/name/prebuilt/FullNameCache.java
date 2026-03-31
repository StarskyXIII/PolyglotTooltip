package com.starskyxiii.polyglottooltip.name.prebuilt;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory full-registry name cache: (registryName, damage) → (langCode → displayName).
 *
 * Populated by FullNameCacheBuilder via the /polyglotbuild command.
 * Loaded from disk at startup if full-name-cache.tsv exists.
 * Checked first in DisplayNameResolver as a fast path before dynamic resolution.
 *
 * Replaced atomically after each successful build or disk load; safe for
 * concurrent reads via volatile.
 */
public final class FullNameCache {

    private static volatile Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> cache =
        Collections.emptyMap();

    private FullNameCache() {}

    /** Atomically replaces the cache. Called after a successful build or disk load. */
    public static void replace(Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> newCache) {
        Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> copy =
            new HashMap<PrebuiltSecondaryNameIndexKey, Map<String, String>>(newCache);
        cache = Collections.unmodifiableMap(copy);
    }

    /**
     * Returns the cached display name, or null if not found.
     * Safe to call from tooltip/render paths — no locking required.
     */
    public static String lookup(String registryName, int damage, String languageCode) {
        if (registryName == null || languageCode == null) return null;
        Map<String, String> langs = cache.get(new PrebuiltSecondaryNameIndexKey(registryName, damage));
        if (langs == null) return null;
        String name = langs.get(languageCode);
        return (name == null || name.isEmpty()) ? null : name;
    }

    public static boolean isEmpty() {
        return cache.isEmpty();
    }

    public static int size() {
        int total = 0;
        for (Map<String, String> langs : cache.values()) total += langs.size();
        return total;
    }

    /** Returns a snapshot of the current cache (for reporting / rebuild). */
    public static Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> snapshot() {
        return cache;
    }
}
