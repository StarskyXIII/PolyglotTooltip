package com.starskyxiii.polyglottooltip.search;

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Shared wrapper for search integrations that want to preserve a mod's native
 * matcher while adding script-conversion and secondary-language fallback.
 */
public final class WrappedSearchMatcher {

    private WrappedSearchMatcher() {
    }

    public static boolean matches(String query,
                                  String primaryName,
                                  BooleanSupplier nativeMatcher,
                                  Supplier<? extends Iterable<String>> secondaryNames) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }

        if (ChineseScriptSearchMatcher.containsMatch(query, primaryName)) {
            return true;
        }

        if (nativeMatcher.getAsBoolean()) {
            return true;
        }

        if (!SecondaryTooltipUtil.shouldShowSecondaryLanguage()) {
            return false;
        }

        return matchesAny(query, secondaryNames.get());
    }

    private static boolean matchesAny(String query, Iterable<String> candidates) {
        if (candidates == null) {
            return false;
        }

        for (String candidate : candidates) {
            if (ChineseScriptSearchMatcher.containsMatch(query, candidate)) {
                return true;
            }
        }
        return false;
    }
}
