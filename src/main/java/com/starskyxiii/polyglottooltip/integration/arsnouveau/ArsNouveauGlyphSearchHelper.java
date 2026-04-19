package com.starskyxiii.polyglottooltip.integration.arsnouveau;

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import com.starskyxiii.polyglottooltip.search.SearchTextCollector;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ArsNouveauGlyphSearchHelper {

    private static final Map<String, Optional<Method>> METHOD_CACHE = new ConcurrentHashMap<>();

    private ArsNouveauGlyphSearchHelper() {
    }

    public static String buildSearchText(Object spellPart) {
        if (spellPart == null) {
            return "";
        }

        return createGlyphNameComponent(spellPart)
                .map(component -> new SearchTextCollector().addComponent(component).build())
                .filter(text -> !text.isBlank())
                .orElseGet(() -> invokeString(spellPart, "getLocaleName").orElse(""));
    }

    public static void insertSecondaryNameLine(List<Component> tooltip, Object spellPart) {
        if (tooltip == null || tooltip.isEmpty() || spellPart == null || !SecondaryTooltipUtil.shouldShowSecondaryLanguage()) {
            return;
        }

        List<String> secondaryNames = createGlyphNameComponent(spellPart)
                .map(SecondaryTooltipUtil::getSecondaryNames)
                .orElse(List.of());
        if (secondaryNames.isEmpty()) {
            return;
        }

        tooltip.add(1, Component.literal(String.join(", ", secondaryNames)).withStyle(ChatFormatting.YELLOW));
    }

    private static Optional<Component> createGlyphNameComponent(Object spellPart) {
        return invokeString(spellPart, "getLocalizationKey").map(Component::translatable);
    }

    private static Optional<String> invokeString(Object target, String methodName) {
        return invokeMethod(target, methodName)
                .filter(String.class::isInstance)
                .map(String.class::cast);
    }

    private static Optional<Object> invokeMethod(Object target, String methodName) {
        String cacheKey = target.getClass().getName() + "#" + methodName;
        Optional<Method> method = METHOD_CACHE.computeIfAbsent(cacheKey,
                k -> findMethod(target.getClass(), methodName));
        return method.flatMap(m -> {
            try {
                return Optional.ofNullable(m.invoke(target));
            } catch (ReflectiveOperationException ignored) {
                return Optional.empty();
            }
        });
    }

    private static Optional<Method> findMethod(Class<?> startClass, String methodName) {
        try {
            Method method = startClass.getMethod(methodName);
            method.setAccessible(true);
            return Optional.of(method);
        } catch (NoSuchMethodException ignored) {
            // Fall through to declared-method lookup for non-public members.
        } catch (SecurityException ignored) {
            return Optional.empty();
        }

        Class<?> current = startClass;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return Optional.of(method);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            } catch (SecurityException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
