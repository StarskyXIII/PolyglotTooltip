package com.starskyxiii.polyglottooltip.integration.controlling;

import com.starskyxiii.polyglottooltip.search.SearchTextCollector;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.controls.KeyBindsList;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflection-backed helpers for Controlling's keybind list entries.
 *
 * <p>Controlling is optional at runtime, so we avoid a direct compile dependency
 * on its entry classes and only rely on their method names.
 */
public final class ControllingSearchUtil {

    private static final String CATEGORY_ENTRY = "com.blamejared.controlling.client.NewKeyBindsList$CategoryEntry";
    private static final String KEY_ENTRY = "com.blamejared.controlling.client.NewKeyBindsList$KeyEntry";
    private static final String INPUT_ENTRY = "com.blamejared.controlling.client.FreeKeysList$InputEntry";

    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    private ControllingSearchUtil() {
    }

    public static Optional<String> resolveCategoryText(KeyBindsList.Entry entry) {
        if (isEntry(entry, CATEGORY_ENTRY)) {
            return componentText(invoke(entry, "name"));
        }
        if (isEntry(entry, KEY_ENTRY)) {
            return componentText(invoke(entry, "categoryName"));
        }
        return Optional.empty();
    }

    public static boolean matchesCategoryText(KeyBindsList.Entry entry, String query) {
        Optional<String> haystack = resolveCategorySearchText(entry);
        return haystack.isPresent() && StringUtils.containsIgnoreCase(haystack.get(), query);
    }

    public static Optional<String> resolveKeyText(KeyBindsList.Entry entry) {
        if (!isEntry(entry, KEY_ENTRY)) {
            return Optional.empty();
        }

        Object key = invoke(entry, "getKey");
        if (key instanceof KeyMapping mapping) {
            return Optional.of(mapping.getTranslatedKeyMessage().getString());
        }
        return Optional.empty();
    }

    public static boolean matchesKeyText(KeyBindsList.Entry entry, String query) {
        return resolveKeySearchText(entry)
                .map(text -> StringUtils.containsIgnoreCase(text, query))
                .orElse(false);
    }

    public static Optional<String> resolveNameText(KeyBindsList.Entry entry) {
        if (isEntry(entry, KEY_ENTRY)) {
            return componentText(invoke(entry, "getKeyDesc"));
        }
        if (isEntry(entry, INPUT_ENTRY)) {
            Object input = invoke(entry, "getInput");
            return input == null ? Optional.empty() : Optional.ofNullable(invokeString(input, "getName"));
        }
        return Optional.empty();
    }

    public static boolean matchesNameText(KeyBindsList.Entry entry, String query) {
        return resolveNameSearchText(entry)
                .map(text -> StringUtils.containsIgnoreCase(text, query))
                .orElse(false);
    }

    private static Optional<String> resolveCategorySearchText(KeyBindsList.Entry entry) {
        if (isEntry(entry, CATEGORY_ENTRY)) {
            return componentSearchText(invoke(entry, "name"));
        }
        if (isEntry(entry, KEY_ENTRY)) {
            return componentSearchText(invoke(entry, "categoryName"));
        }
        return Optional.empty();
    }

    private static Optional<String> resolveKeySearchText(KeyBindsList.Entry entry) {
        if (!isEntry(entry, KEY_ENTRY)) {
            return Optional.empty();
        }

        return invokeMethod(entry, "getKey")
                .filter(KeyMapping.class::isInstance)
                .map(KeyMapping.class::cast)
                .map(KeyMapping::getTranslatedKeyMessage)
                .flatMap(ControllingSearchUtil::componentSearchText);
    }

    private static Optional<String> resolveNameSearchText(KeyBindsList.Entry entry) {
        if (isEntry(entry, KEY_ENTRY)) {
            return componentSearchText(invokeMethod(entry, "getKeyDesc").orElse(null));
        }
        if (isEntry(entry, INPUT_ENTRY)) {
            return invokeMethod(entry, "getInput")
                    .flatMap(input -> invokeMethod(input, "getName"))
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .flatMap(ControllingSearchUtil::stringSearchText);
        }
        return Optional.empty();
    }

    private static Optional<String> componentSearchText(Object value) {
        if (!(value instanceof Component component)) {
            return Optional.empty();
        }

        String built = new SearchTextCollector().addComponent(component).build();
        return built.isBlank() ? Optional.empty() : Optional.of(built);
    }

    private static Optional<String> stringSearchText(String value) {
        String built = new SearchTextCollector().addText(value).build();
        return built.isBlank() ? Optional.empty() : Optional.of(built);
    }

    private static Optional<String> componentText(Object value) {
        return value instanceof Component component ? Optional.of(component.getString()) : Optional.empty();
    }

    private static boolean isEntry(Object entry, String expectedClassName) {
        return entry != null && expectedClassName.equals(entry.getClass().getName());
    }

    private static Object invoke(Object target, String methodName, Object... args) {
        if (target == null) {
            return null;
        }

        try {
            Method method = findMethod(target.getClass(), methodName, args.length);
            return method == null ? null : method.invoke(target, args);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Optional<Object> invokeMethod(Object target, String methodName, Object... args) {
        return Optional.ofNullable(invoke(target, methodName, args));
    }

    private static String invokeString(Object target, String methodName, Object... args) {
        Object value = invoke(target, methodName, args);
        return value instanceof String string ? string : null;
    }

    private static Method findMethod(Class<?> owner, String methodName, int argCount) {
        String cacheKey = owner.getName() + "#" + methodName + "#" + argCount;
        return METHOD_CACHE.computeIfAbsent(cacheKey, key -> {
            for (Method method : owner.getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == argCount) {
                    return method;
                }
            }
            return null;
        });
    }
}
