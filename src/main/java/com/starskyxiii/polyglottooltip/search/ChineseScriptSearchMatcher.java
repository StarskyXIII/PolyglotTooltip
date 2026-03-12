package com.starskyxiii.polyglottooltip.search;

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.starskyxiii.polyglottooltip.Config;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.UnaryOperator;

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

    // Single-entry cache for query variants.
    // Search runs on the client thread; all items in one search pass share the same query,
    // so caching the last query avoids repeated Set allocations and OpenCC conversions.
    private static String cachedQuery = null;
    private static Set<String> cachedQueryVariants = Set.of();

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
        if (!query.equals(cachedQuery)) {
            cachedQuery = query;
            cachedQueryVariants = Collections.unmodifiableSet(normalizedVariants(query));
        }
        return cachedQueryVariants;
    }

    private static boolean containsMatch(Set<String> queryVariants, String candidate) {
        for (String candidateVariant : getSearchVariants(candidate)) {
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
