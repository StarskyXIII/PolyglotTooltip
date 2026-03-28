package com.starskyxiii.polyglottooltip.integration.emi;

import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.starskyxiii.polyglottooltip.integration.ReflectionHelper;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import net.minecraft.world.item.ItemStack;

/**
 * Secondary-name fallback used by EMI name search.
 *
 * <p>We intentionally keep this on the name-query path instead of relying on
 * tooltip search, because some modpacks replace or bypass EMI's tooltip index
 * and unbaked tooltip matcher.
 */
public final class EmiNameSearchUtil {

    private EmiNameSearchUtil() {
    }

    public static boolean matchesSecondaryName(String query, Object emiStack) {
        if (query == null || query.isBlank() || emiStack == null || !SecondaryTooltipUtil.shouldShowSecondaryLanguage()) {
            return false;
        }

        ItemStack stack = ReflectionHelper.invokeMethod(emiStack, "getItemStack")
                .filter(ItemStack.class::isInstance)
                .map(ItemStack.class::cast)
                .orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) {
            return false;
        }

        return ChineseScriptSearchMatcher.containsMatch(
                query,
                LanguageCache.getInstance().resolveSearchNamesForAll(stack)
        );
    }
}
