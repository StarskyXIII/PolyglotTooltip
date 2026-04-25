package com.starskyxiii.polyglottooltip.integration.controlling;

import com.starskyxiii.polyglottooltip.search.SearchTextCollector;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflection-backed helpers for Controlling's keybind list entries.
 *
 * <p>Controlling is optional at runtime, so we avoid a direct compile
 * dependency on its entry interfaces and resolve everything lazily.
 */
public final class ControllingSearchUtil {

    private static final String CATEGORY_ENTRY = "com.blamejared.controlling.api.entries.ICategoryEntry";
    private static final String KEY_ENTRY = "com.blamejared.controlling.api.entries.IKeyEntry";
    private static final String INPUT_ENTRY = "com.blamejared.controlling.api.entries.IInputEntry";

    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    private ControllingSearchUtil() {
    }

    public static Optional<String> resolveCategoryText(KeyBindsList.Entry entry) {
        if (hasType(entry, CATEGORY_ENTRY)) {
            return categoryComponent(entry).flatMap(ControllingSearchUtil::componentText);
        }
        if (hasType(entry, KEY_ENTRY)) {
            return componentText(invokeMethod(entry, "categoryName").orElse(null));
        }
        return Optional.empty();
    }

    public static boolean matchesCategoryText(KeyBindsList.Entry entry, String query) {
        return resolveCategorySearchText(entry)
                .map(text -> StringUtils.containsIgnoreCase(text, query))
                .orElse(false);
    }

    public static Optional<String> resolveKeyText(KeyBindsList.Entry entry) {
        if (!hasType(entry, KEY_ENTRY)) {
            return Optional.empty();
        }

        return invokeMethod(entry, "getKey")
                .filter(KeyMapping.class::isInstance)
                .map(KeyMapping.class::cast)
                .map(mapping -> mapping.getTranslatedKeyMessage().getString());
    }

    public static boolean matchesKeyText(KeyBindsList.Entry entry, String query) {
        return resolveKeySearchText(entry)
                .map(text -> StringUtils.containsIgnoreCase(text, query))
                .orElse(false);
    }

    public static Optional<String> resolveNameText(KeyBindsList.Entry entry) {
        if (hasType(entry, KEY_ENTRY)) {
            return componentText(invokeMethod(entry, "getName").orElse(null));
        }
        if (hasType(entry, INPUT_ENTRY)) {
            return invokeMethod(entry, "getInput")
                    .flatMap(input -> invokeMethod(input, "getName"))
                    .filter(String.class::isInstance)
                    .map(String.class::cast);
        }
        return Optional.empty();
    }

    public static boolean matchesNameText(KeyBindsList.Entry entry, String query) {
        return resolveNameSearchText(entry)
                .map(text -> StringUtils.containsIgnoreCase(text, query))
                .orElse(false);
    }

    private static Optional<String> resolveCategorySearchText(KeyBindsList.Entry entry) {
        if (hasType(entry, CATEGORY_ENTRY)) {
            return categoryComponent(entry).flatMap(ControllingSearchUtil::componentSearchText);
        }
        if (hasType(entry, KEY_ENTRY)) {
            return componentSearchText(invokeMethod(entry, "categoryName").orElse(null));
        }
        return Optional.empty();
    }

    private static Optional<String> resolveKeySearchText(KeyBindsList.Entry entry) {
        if (!hasType(entry, KEY_ENTRY)) {
            return Optional.empty();
        }

        return invokeMethod(entry, "getKey")
                .filter(KeyMapping.class::isInstance)
                .map(KeyMapping.class::cast)
                .map(KeyMapping::getTranslatedKeyMessage)
                .flatMap(ControllingSearchUtil::componentSearchText);
    }

    private static Optional<String> resolveNameSearchText(KeyBindsList.Entry entry) {
        if (hasType(entry, KEY_ENTRY)) {
            return componentSearchText(invokeMethod(entry, "getName").orElse(null));
        }
        if (hasType(entry, INPUT_ENTRY)) {
            return invokeMethod(entry, "getInput")
                    .flatMap(input -> invokeMethod(input, "getName"))
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .flatMap(ControllingSearchUtil::stringSearchText);
        }
        return Optional.empty();
    }

    private static Optional<Object> categoryComponent(KeyBindsList.Entry entry) {
        return invokeMethod(entry, "category")
                .flatMap(category -> invokeMethod(category, "label"));
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

    private static Optional<Object> invokeMethod(Object target, String methodName) {
        if (target == null) {
            return Optional.empty();
        }

        try {
            Method method = findMethod(target.getClass(), methodName);
            return method == null ? Optional.empty() : Optional.ofNullable(method.invoke(target));
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    private static boolean hasType(Object instance, String expectedTypeName) {
        return instance != null && hasType(instance.getClass(), expectedTypeName);
    }

    private static boolean hasType(Class<?> type, String expectedTypeName) {
        if (type == null) {
            return false;
        }
        if (expectedTypeName.equals(type.getName())) {
            return true;
        }
        for (Class<?> iface : type.getInterfaces()) {
            if (hasType(iface, expectedTypeName)) {
                return true;
            }
        }
        return hasType(type.getSuperclass(), expectedTypeName);
    }

    private static Method findMethod(Class<?> owner, String methodName) {
        String cacheKey = owner.getName() + "#" + methodName;
        return METHOD_CACHE.computeIfAbsent(cacheKey, key -> {
            try {
                return owner.getMethod(methodName);
            } catch (NoSuchMethodException ignored) {
                Class<?> current = owner;
                while (current != null) {
                    try {
                        Method method = current.getDeclaredMethod(methodName);
                        method.setAccessible(true);
                        return method;
                    } catch (NoSuchMethodException ignoredAgain) {
                        current = current.getSuperclass();
                    }
                }
                return null;
            }
        });
    }
}
