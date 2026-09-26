package com.starskyxiii.polyglottooltip.integration.projecte;

import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.starskyxiii.polyglottooltip.core.ChineseScriptPolicy;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.function.BiPredicate;

public final class LegacyProjectESearch {
    private LegacyProjectESearch() {
    }

    public static boolean matches(String nativeText, CharSequence query, Object itemInfo,
                                  BiPredicate<String, CharSequence> contains)
            throws ReflectiveOperationException {
        if (matchesCandidate(nativeText, query, contains)) {
            return true;
        }
        if (!SecondaryTooltipUtil.shouldShowSecondaryLanguage()) {
            return false;
        }
        Method method = itemInfo.getClass().getMethod("createStack");
        Object value = method.invoke(itemInfo);
        if (!(value instanceof ItemStack stack)) {
            return false;
        }
        for (String name : LanguageCache.getInstance().resolveSearchNamesForAll(stack)) {
            if (matchesCandidate(name, query, contains)) {
                return true;
            }
        }
        return false;
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
}
