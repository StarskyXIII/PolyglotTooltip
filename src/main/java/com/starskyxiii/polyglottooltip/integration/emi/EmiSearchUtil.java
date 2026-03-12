package com.starskyxiii.polyglottooltip.integration.emi;

import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import net.minecraft.client.searchtree.SuffixArray;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Set;

/**
 * Reflection-backed helpers for optional EMI search integration.
 *
 * <p>EMI is not on the compile classpath for this module, so we resolve the
 * baked search indices and SearchStack wrapper lazily at runtime.
 */
public final class EmiSearchUtil {

    private static final String EMI_SEARCH_CLASS = "dev.emi.emi.search.EmiSearch";
    private static final String SEARCH_STACK_CLASS = "dev.emi.emi.search.SearchStack";

    private static volatile Field namesField;
    private static volatile Field tooltipsField;
    private static volatile Field searchStackField;

    private EmiSearchUtil() {
    }

    public static void appendNameMatches(Set<Object> valid, String query) {
        appendVariantMatches(valid, query, "names");
    }

    public static void appendTooltipMatches(Set<Object> valid, String query) {
        appendVariantMatches(valid, query, "tooltips");
    }

    private static void appendVariantMatches(Set<Object> valid, String query, String fieldName) {
        if (!ChineseScriptSearchMatcher.isEnabled() || valid == null || query == null || query.isBlank()) {
            return;
        }

        SuffixArray<?> index = getSuffixArray(fieldName);
        if (index == null) {
            return;
        }

        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        for (String variant : ChineseScriptSearchMatcher.getSearchVariants(query)) {
            if (variant.equals(normalizedQuery)) {
                continue;
            }

            for (Object searchStack : index.search(variant)) {
                Object stack = unwrapSearchStack(searchStack);
                if (stack != null) {
                    valid.add(stack);
                }
            }
        }
    }

    private static SuffixArray<?> getSuffixArray(String fieldName) {
        try {
            Field field = getSearchField(fieldName);
            Object value = field == null ? null : field.get(null);
            return value instanceof SuffixArray<?> suffixArray ? suffixArray : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Field getSearchField(String fieldName) throws ReflectiveOperationException {
        if ("names".equals(fieldName)) {
            if (namesField == null) {
                namesField = resolveSearchField(fieldName);
            }
            return namesField;
        }

        if (tooltipsField == null) {
            tooltipsField = resolveSearchField(fieldName);
        }
        return tooltipsField;
    }

    private static Field resolveSearchField(String fieldName) throws ReflectiveOperationException {
        Field field = Class.forName(EMI_SEARCH_CLASS).getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }

    private static Object unwrapSearchStack(Object searchStack) {
        if (searchStack == null) {
            return null;
        }

        try {
            if (searchStackField == null) {
                Field field = Class.forName(SEARCH_STACK_CLASS).getDeclaredField("stack");
                field.setAccessible(true);
                searchStackField = field;
            }
            return searchStackField.get(searchStack);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
