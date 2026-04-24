package com.starskyxiii.polyglottooltip.integration.emi;

import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

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

        List<String> secondaryNames = resolveSecondaryNames(emiStack);
        if (secondaryNames.isEmpty()) {
            return false;
        }

        return ChineseScriptSearchMatcher.containsMatch(query, secondaryNames);
    }

    private static List<String> resolveSecondaryNames(Object emiStack) {
        ItemStack stack = invokeMethod(emiStack, "getItemStack")
                .filter(ItemStack.class::isInstance)
                .map(ItemStack.class::cast)
                .orElse(ItemStack.EMPTY);
        if (!stack.isEmpty()) {
            return LanguageCache.getInstance().resolveSearchNamesForAll(stack);
        }

        return invokeMethod(emiStack, "getName")
                .filter(Component.class::isInstance)
                .map(Component.class::cast)
                .map(component -> LanguageCache.getInstance().resolveComponentsForAll(component))
                .orElse(List.of());
    }

    private static Optional<Object> invokeMethod(Object target, String methodName) {
        if (target == null) {
            return Optional.empty();
        }

        try {
            Method method = target.getClass().getMethod(methodName);
            return Optional.ofNullable(method.invoke(target));
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }
}
