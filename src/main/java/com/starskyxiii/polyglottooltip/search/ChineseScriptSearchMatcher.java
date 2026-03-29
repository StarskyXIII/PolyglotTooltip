package com.starskyxiii.polyglottooltip.search;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.starskyxiii.polyglottooltip.config.Config;

public final class ChineseScriptSearchMatcher {

    private static final Map<String, Set<String>> VARIANT_CACHE = new ConcurrentHashMap<String, Set<String>>();

    private ChineseScriptSearchMatcher() {}

    public static Set<String> getSearchVariants(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return Collections.emptySet();
        }

        if (!Config.enableChineseScriptMatching) {
            return Collections.singleton(normalized);
        }

        Set<String> cached = VARIANT_CACHE.get(normalized);
        if (cached != null) {
            return cached;
        }

        Set<String> variants = new LinkedHashSet<String>();
        variants.add(normalized);

        if (containsChinese(normalized)) {
            addConvertedVariant(variants, normalized, true);
            addConvertedVariant(variants, normalized, false);
        }

        Set<String> immutable = Collections.unmodifiableSet(variants);
        Set<String> previous = VARIANT_CACHE.putIfAbsent(normalized, immutable);
        return previous != null ? previous : immutable;
    }

    public static void clearCaches() {
        VARIANT_CACHE.clear();
    }

    private static boolean containsChinese(String value) {
        try {
            return ZhConverterUtil.containsChinese(value);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void addConvertedVariant(Set<String> variants, String source, boolean toSimplified) {
        try {
            String converted = toSimplified ? ZhConverterUtil.toSimple(source) : ZhConverterUtil.toTraditional(source);
            converted = normalize(converted);
            if (!converted.isEmpty()) {
                variants.add(converted);
            }
        } catch (RuntimeException ignored) {
            // Fall back to the original normalized form if conversion fails.
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
