package com.starskyxiii.polyglottooltip.integration.controlling;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.StatCollector;

import com.starskyxiii.polyglottooltip.ChineseScriptSearchMatcher;
import com.starskyxiii.polyglottooltip.Config;
import com.starskyxiii.polyglottooltip.LanguageCache;

public final class ControllingSearchUtil {

    private static final Map<String, Set<String>> CATEGORY_SEARCH_TEXT_CACHE =
        new ConcurrentHashMap<String, Set<String>>();
    private static final Map<Class<?>, Method> ENTRY_KEYBINDING_METHOD_CACHE =
        new ConcurrentHashMap<Class<?>, Method>();

    private ControllingSearchUtil() {}

    public static Predicate<Object> createCategoryPredicate(String searchText) {
        final Set<String> normalizedQueryVariants = normalizeVariants(searchText);
        if (normalizedQueryVariants.isEmpty()) {
            return new Predicate<Object>() {
                @Override
                public boolean test(Object entry) {
                    return true;
                }
            };
        }

        return new Predicate<Object>() {
            @Override
            public boolean test(Object entry) {
                String categoryKey = extractCategoryKey(entry);
                if (categoryKey == null || categoryKey.isEmpty()) {
                    return false;
                }

                for (String searchTextCandidate : resolveCategorySearchTexts(categoryKey)) {
                    if (matches(searchTextCandidate, normalizedQueryVariants)) {
                        return true;
                    }
                }

                return false;
            }
        };
    }

    public static void clearCaches() {
        CATEGORY_SEARCH_TEXT_CACHE.clear();
    }

    private static Set<String> resolveCategorySearchTexts(String categoryKey) {
        Set<String> cached = CATEGORY_SEARCH_TEXT_CACHE.get(categoryKey);
        if (cached != null) {
            return cached;
        }

        LinkedHashSet<String> resolved = new LinkedHashSet<String>();
        addResolvedText(resolved, categoryKey);
        addResolvedText(resolved, StatCollector.translateToLocal(categoryKey));
        addResolvedText(resolved, LanguageCache.translate("en_US", categoryKey));

        for (String languageCode : Config.displayLanguages) {
            addResolvedText(resolved, LanguageCache.translate(languageCode, categoryKey));
        }

        Set<String> immutable = Collections.unmodifiableSet(resolved);
        Set<String> previous = CATEGORY_SEARCH_TEXT_CACHE.putIfAbsent(categoryKey, immutable);
        return previous != null ? previous : immutable;
    }

    private static boolean matches(String candidate, Set<String> normalizedQueryVariants) {
        Set<String> candidateVariants = normalizeVariants(candidate);
        for (String candidateVariant : candidateVariants) {
            for (String queryVariant : normalizedQueryVariants) {
                if (candidateVariant.contains(queryVariant)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> normalizeVariants(String value) {
        LinkedHashSet<String> normalized = new LinkedHashSet<String>();
        for (String variant : ChineseScriptSearchMatcher.getSearchVariants(value)) {
            String lowered = normalize(variant);
            if (!lowered.isEmpty()) {
                normalized.add(lowered);
            }
        }
        return normalized;
    }

    private static String extractCategoryKey(Object entry) {
        if (entry == null) {
            return null;
        }

        Method method = getKeybindingMethod(entry.getClass());
        if (method == null) {
            return null;
        }

        try {
            Object keybinding = method.invoke(entry);
            if (keybinding instanceof KeyBinding) {
                return ((KeyBinding) keybinding).getKeyCategory();
            }
        } catch (ReflectiveOperationException ignored) {
            // Ignore and fall back to not matching this entry.
        }

        return null;
    }

    private static Method getKeybindingMethod(Class<?> entryClass) {
        Method cached = ENTRY_KEYBINDING_METHOD_CACHE.get(entryClass);
        if (cached != null) {
            return cached;
        }

        try {
            Method method = entryClass.getMethod("getKeybinding");
            method.setAccessible(true);
            ENTRY_KEYBINDING_METHOD_CACHE.put(entryClass, method);
            return method;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static void addResolvedText(Set<String> target, String text) {
        if (text == null) {
            return;
        }

        String normalized = text.trim();
        if (!normalized.isEmpty()) {
            target.add(normalized);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
