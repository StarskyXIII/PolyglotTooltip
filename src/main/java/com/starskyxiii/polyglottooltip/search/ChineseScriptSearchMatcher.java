package com.starskyxiii.polyglottooltip.search;

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.starskyxiii.polyglottooltip.Config;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Reusable search helper that normalizes simplified/traditional Chinese script
 * variants without knowing anything about AE2, RS, or tooltip code.
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

    /**
     * Clears all caches. Call when the Chinese-script-matching config option changes
     * so that cached variant sets are recomputed with the updated setting.
     */
    public static void clearCaches() {
        QUERY_CACHE.set(new QueryCache(null, Set.of()));
        CANDIDATE_CACHE.clear();
    }

    private static boolean containsMatch(Set<String> queryVariants, String candidate) {
        Set<String> candidateVariants = CANDIDATE_CACHE.computeIfAbsent(
                normalize(candidate),
                k -> Collections.unmodifiableSet(normalizedVariants(candidate)));
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
        Set<String> variants = new LinkedHashSet<>();
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return variants;
        }

        variants.add(normalized);
        if (!isEnabled()) {
            return variants;
        }
        if (!ZhConverterUtil.containsChinese(normalized)) {
            return variants;
        }

        addConvertedVariant(variants, normalized, ZhConverterUtil::toSimple);
        addConvertedVariant(variants, normalized, ZhConverterUtil::toTraditional);
        return variants;
    }

    private static void addConvertedVariant(Set<String> variants,
                                            String source,
                                            UnaryOperator<String> converter) {
        try {
            String converted = normalize(converter.apply(source));
            if (!converted.isEmpty()) {
                variants.add(converted);
            }
        } catch (RuntimeException ignored) {
            // Fall back to the original normalized form if conversion fails.
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
