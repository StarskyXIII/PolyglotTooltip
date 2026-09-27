package com.starskyxiii.polyglottooltip.integration.controlling;
//~ minecraft_116_packages minecraft_legacy_components minecraft_118_factories

import com.starskyxiii.polyglottooltip.minecraft.ComponentFactory;
import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.starskyxiii.polyglottooltip.core.ChineseScriptPolicy;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;

public final class LegacyControllingSearch {
    private LegacyControllingSearch() {
    }

    public static boolean matchesName(String nativeText, CharSequence query, Object entry,
                                      BiPredicate<String, CharSequence> contains) {
        if (matchesPrimary(nativeText, query, contains)) {
            return true;
        }
        KeyMapping mapping = getKeyBinding(entry);
        return mapping != null && matches(query, ComponentFactory.translatable(mapping.getName()), contains);
    }

    public static boolean matchesCategory(String nativeText, CharSequence query, Object entry,
                                          BiPredicate<String, CharSequence> contains) {
        if (matchesPrimary(nativeText, query, contains)) {
            return true;
        }
        KeyMapping mapping = getKeyBinding(entry);
        return mapping != null && matches(query, ComponentFactory.translatable(mapping.getCategory()), contains);
    }

    public static boolean matchesKey(String nativeText, CharSequence query, Object entry,
                                     BiPredicate<String, CharSequence> contains) {
        if (matchesPrimary(nativeText, query, contains)) {
            return true;
        }
        KeyMapping mapping = getKeyBinding(entry);
        return mapping != null && matches(query, mapping.getTranslatedKeyMessage(), contains);
    }

    private static boolean matches(CharSequence query, Component component,
                                   BiPredicate<String, CharSequence> contains) {
        if (!SecondaryTooltipUtil.shouldShowSecondaryLanguage()) {
            return false;
        }
        List<String> names = LanguageCache.getInstance().resolveComponentsForAll(component);
        for (String name : names) {
            if (matchesCandidate(name, query, contains)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPrimary(String nativeText, CharSequence query,
                                          BiPredicate<String, CharSequence> contains) {
        return contains.test(nativeText, query) || matchesCandidate(nativeText, query, contains);
    }

    private static boolean matchesCandidate(String candidate, CharSequence query,
                                            BiPredicate<String, CharSequence> contains) {
        boolean scriptEnabled = ChineseScriptSearchMatcher.isEnabled();
        for (String variant : ChineseScriptPolicy.scriptVariants(candidate, scriptEnabled)) {
            if (contains.test(variant.toLowerCase(Locale.ROOT), query)) {
                return true;
            }
        }
        return false;
    }

    private static KeyMapping getKeyBinding(Object entry) {
        if (entry == null) {
            return null;
        }
        try {
            Method method = entry.getClass().getMethod("getKeybinding");
            Object value = method.invoke(entry);
            return value instanceof KeyMapping ? (KeyMapping) value : null;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}
