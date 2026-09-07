package com.starskyxiii.polyglottooltip.core;

import com.github.houbb.opencc4j.util.ZhConverterUtil;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.UnaryOperator;

public final class ChineseScriptPolicy {
    private ChineseScriptPolicy() {
    }

    public static Set<String> normalizedVariants(String value, boolean enabled) {
        Set<String> variants = new LinkedHashSet<>();
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return Collections.emptySet();
        }

        variants.add(normalized);
        if (!enabled || !ZhConverterUtil.containsChinese(normalized)) {
            return Collections.unmodifiableSet(variants);
        }

        addConvertedVariant(variants, normalized, ZhConverterUtil::toSimple);
        addConvertedVariant(variants, normalized, ZhConverterUtil::toTraditional);
        return Collections.unmodifiableSet(variants);
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
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
        }
    }
}
