package com.starskyxiii.polyglottooltip.search;

import com.starskyxiii.polyglottooltip.Config;
import com.starskyxiii.polyglottooltip.core.ChineseScriptPolicy;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reusable search helper that normalizes simplified/traditional Chinese script
 * variants without knowing anything about AE2, RS2, or tooltip code.
 *
 * <p>This keeps the OpenCC dependency isolated so the feature can be moved into
 * a dedicated module later with minimal changes to call sites.
 */
public final class ChineseScriptSearchMatcher {

    private ChineseScriptSearchMatcher() {
    }

    private record QueryCache(String query, Set<String> variants) {
    }

    // Single-entry cache for query variants.
    // Search may run on worker and render threads, so keep the pair atomic.
    private static final AtomicReference<QueryCache> QUERY_CACHE =
            new AtomicReference<>(new QueryCache(null, Set.of()));

    // Permanent per-string cache for candidate variants.
    // Item names are fixed within a game session; caching them avoids redundant OpenCC
    // conversions when the same item is tested across multiple search passes.
    private static final Map<String, Set<String>> CANDIDATE_CACHE = new ConcurrentHashMap<>();

    public static boolean isEnabled() {
        return Config.ENABLE_CHINESE_SCRIPT_MATCHING.get();
    }

    public static Set<String> getSearchVariants(String value) {
        return Collections.unmodifiableSet(normalizedVariants(value));
    }

    public static boolean containsMatch(String query, String candidate) {
        Set<String> queryVariants = queryVariants(query);
        if (queryVariants.isEmpty()) {
            return true;
        }
        return containsMatch(queryVariants, candidate);
    }

    public static boolean containsMatch(String query, Collection<String> candidates) {
        Set<String> queryVariants = queryVariants(query);
        if (queryVariants.isEmpty()) {
            return true;
        }

        for (String candidate : candidates) {
            if (containsMatch(queryVariants, candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Clears all caches. Call when the Chinese-script-matching config option changes
     * so that cached variant sets are recomputed with the updated setting.
     */
    public static void clearCaches() {
        QUERY_CACHE.set(new QueryCache(null, Set.of()));
        CANDIDATE_CACHE.clear();
    }

    /** Returns cached variants for {@code query}, recomputing only when the query string changes. */
    private static Set<String> queryVariants(String query) {
        QueryCache cache = QUERY_CACHE.get();
        if (Objects.equals(query, cache.query())) {
            return cache.variants();
        }
        Set<String> variants = Collections.unmodifiableSet(normalizedVariants(query));
        QUERY_CACHE.set(new QueryCache(query, variants));
        return variants;
    }

    private static boolean containsMatch(Set<String> queryVariants, String candidate) {
        Set<String> candidateVariants = CANDIDATE_CACHE.computeIfAbsent(
                ChineseScriptPolicy.normalize(candidate),
                key -> normalizedVariants(candidate));
        for (String candidateVariant : candidateVariants) {
            for (String queryVariant : queryVariants) {
                if (candidateVariant.contains(queryVariant)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> normalizedVariants(String value) {
        String normalized = ChineseScriptPolicy.normalize(value);
        if (normalized.isEmpty()) {
            return Collections.emptySet();
        }
        return ChineseScriptPolicy.normalizedVariants(normalized, isEnabled());
    }
}
