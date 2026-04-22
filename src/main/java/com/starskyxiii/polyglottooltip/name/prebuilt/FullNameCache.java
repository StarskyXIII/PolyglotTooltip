package com.starskyxiii.polyglottooltip.name.prebuilt;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static volatile FullNameCacheMetadata metadata;

    private FullNameCache() {}

    /** Atomically replaces the cache. Called after a successful build or disk load. */
    public static void replace(Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> newCache) {
        replace(newCache, null);
    }

    /** Atomically replaces the cache + metadata. Called after a successful build or disk load. */
    public static void replace(Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> newCache,
            FullNameCacheMetadata newMetadata) {
        Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> copy =
            new HashMap<PrebuiltSecondaryNameIndexKey, Map<String, String>>();
        for (Map.Entry<PrebuiltSecondaryNameIndexKey, Map<String, String>> entry : newCache.entrySet()) {
            Map<String, String> langs = entry.getValue();
            Map<String, String> langCopy = langs == null
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new HashMap<String, String>(langs));
            copy.put(entry.getKey(), langCopy);
        }
        cache = Collections.unmodifiableMap(copy);
        metadata = newMetadata;
    }

    public static void clear() {
        cache = Collections.emptyMap();
        metadata = null;
    }

    public static void clearDataPreservingMetadata() {
        cache = Collections.emptyMap();
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

    public static FullNameCacheMetadata snapshotMetadata() {
        return metadata;
    }

    public static List<String> getAvailableLanguages() {
        FullNameCacheMetadata currentMetadata = metadata;
        if (currentMetadata != null) {
            return currentMetadata.getLanguages();
        }

        LinkedHashSet<String> languages = new LinkedHashSet<String>();
        for (Map<String, String> langs : cache.values()) {
            if (langs == null || langs.isEmpty()) {
                continue;
            }
            for (String languageCode : langs.keySet()) {
                if (languageCode != null && !languageCode.trim().isEmpty()) {
                    languages.add(languageCode.trim());
                }
            }
        }

        return Collections.unmodifiableList(FullNameCacheMetadata.normalizeLanguages(
            new java.util.ArrayList<String>(languages)));
    }

    public static Set<String> collectAvailableLanguageSet() {
        return new LinkedHashSet<String>(getAvailableLanguages());
    }

    /** Returns a snapshot of the current cache (for reporting / rebuild). */
    public static Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> snapshot() {
        return cache;
    }
}
