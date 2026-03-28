package com.starskyxiii.polyglottooltip.integration.rs;

import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import com.starskyxiii.polyglottooltip.search.WrappedSearchMatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Search helpers for the Refined Storage grid.
 *
 * <p>RS is not on the compile classpath, so all method invocations go through
 * reflection.  Results are cached by {@code "className#methodName#argCount"} so
 * the lookup cost is paid only once per unique method signature.
 */
public final class RsSearchUtil {

    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    private RsSearchUtil() {
    }

    /**
     * Returns {@code true} if {@code query} matches the grid stack's primary name
     * (with Chinese script variant expansion) or any secondary-language name.
     */
    public static boolean matchesNameSearch(String query, Object gridStack) {
        if (query == null || query.isBlank()) {
            return true;
        }

        return WrappedSearchMatcher.matches(
                query,
                invokeString(gridStack, "getName"),
                () -> false,
                () -> resolveSecondaryNames(gridStack)
        );
    }

    /**
     * Returns {@code true} if {@code query} matches any tooltip line of the grid stack
     * (skipping the first line which is the item name, already handled by name search).
     * Uses Chinese script variant expansion when enabled.
     */
    public static boolean matchesTooltipSearch(String query, Object gridStack) {
        if (query == null || query.isBlank()) {
            return true;
        }

        return ChineseScriptSearchMatcher.containsMatch(query, resolveTooltipLines(gridStack));
    }

    public static Component getIngredientDisplayName(Object gridStack) {
        Object ingredient = invoke(gridStack, "getIngredient");
        if (ingredient instanceof ItemStack itemStack) {
            return itemStack.getHoverName();
        }

        Object displayName = invoke(ingredient, "getDisplayName");
        return displayName instanceof Component component ? component : null;
    }

    private static List<String> resolveSecondaryNames(Object gridStack) {
        Component displayName = getIngredientDisplayName(gridStack);
        if (displayName == null) {
            return List.of();
        }

        Object ingredient = invoke(gridStack, "getIngredient");
        if (ingredient instanceof ItemStack itemStack) {
            return LanguageCache.getInstance().resolveSearchNamesForAll(itemStack);
        }
        return LanguageCache.getInstance().resolveComponentsForAll(displayName);
    }

    private static List<String> resolveTooltipLines(Object gridStack) {
        Object tooltip = invoke(gridStack, "getTooltip", false);
        if (!(tooltip instanceof List<?> lines) || lines.size() <= 1) {
            return List.of();
        }

        List<String> resolved = new ArrayList<>(lines.size() - 1);
        for (int i = 1; i < lines.size(); i++) {
            Object line = lines.get(i);
            if (line instanceof Component component) {
                resolved.add(component.getString());
            }
        }
        return resolved;
    }

    private static Object invoke(Object target, String methodName, Object... args) {
        if (target == null) {
            return null;
        }

        try {
            Method method = findMethod(target.getClass(), methodName, args);
            return method == null ? null : method.invoke(target, args);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String invokeString(Object target, String methodName, Object... args) {
        Object value = invoke(target, methodName, args);
        return value instanceof String string ? string : "";
    }

    private static Method findMethod(Class<?> owner, String methodName, Object... args) {
        String cacheKey = owner.getName() + "#" + methodName + "#" + args.length;
        return METHOD_CACHE.computeIfAbsent(cacheKey, key -> {
            for (Method method : owner.getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                    continue;
                }

                Class<?>[] parameterTypes = method.getParameterTypes();
                boolean compatible = true;
                for (int i = 0; i < parameterTypes.length; i++) {
                    if (!isCompatible(parameterTypes[i], args[i])) {
                        compatible = false;
                        break;
                    }
                }

                if (compatible) {
                    return method;
                }
            }
            return null;
        });
    }

    private static boolean isCompatible(Class<?> parameterType, Object arg) {
        if (arg == null) {
            return !parameterType.isPrimitive();
        }
        if (parameterType.isPrimitive()) {
            return parameterType == boolean.class && arg instanceof Boolean
                    || parameterType == int.class && arg instanceof Integer
                    || parameterType == long.class && arg instanceof Long
                    || parameterType == double.class && arg instanceof Double
                    || parameterType == float.class && arg instanceof Float
                    || parameterType == byte.class && arg instanceof Byte
                    || parameterType == short.class && arg instanceof Short
                    || parameterType == char.class && arg instanceof Character;
        }
        return parameterType.isInstance(arg);
    }
}
