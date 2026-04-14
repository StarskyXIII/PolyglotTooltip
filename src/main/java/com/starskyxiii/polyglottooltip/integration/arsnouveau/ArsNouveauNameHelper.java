package com.starskyxiii.polyglottooltip.integration.arsnouveau;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class ArsNouveauNameHelper {

    private static final String PERK_ITEM = "com.hollingsworth.arsnouveau.common.items.PerkItem";
    private static final String RITUAL_TABLET = "com.hollingsworth.arsnouveau.common.items.RitualTablet";
    private static final String FAMILIAR_SCRIPT = "com.hollingsworth.arsnouveau.common.items.FamiliarScript";
    private static final String GLYPH = "com.hollingsworth.arsnouveau.common.items.Glyph";

    // Caches resolved Field/Method objects keyed by "className#memberName".
    // Item classes are registered singletons — their fields/methods never change.
    private static final Map<String, Optional<Field>> fieldCache = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Method>> methodCache = new ConcurrentHashMap<>();

    private ArsNouveauNameHelper() {
    }

    public static Optional<String> tryResolveSpecialName(ItemStack stack, Function<Component, Optional<String>> resolver) {
        return tryCreateSpecialName(stack).flatMap(resolver);
    }

    public static Optional<Component> tryCreateSpecialName(ItemStack stack) {
        return tryCreateSpecialName(stack.getItem());
    }

    public static Optional<Component> tryCreateSpecialName(Item item) {
        return switch (item.getClass().getName()) {
            case PERK_ITEM -> readField(item, "perk")
                    .flatMap(ArsNouveauNameHelper::createThreadName);
            case RITUAL_TABLET -> readField(item, "ritual")
                    .flatMap(ArsNouveauNameHelper::createRitualTabletName);
            case FAMILIAR_SCRIPT -> readField(item, "familiar")
                    .flatMap(ArsNouveauNameHelper::createBoundScriptName);
            case GLYPH -> readField(item, "spellPart")
                    .flatMap(ArsNouveauNameHelper::createGlyphName);
            default -> Optional.empty();
        };
    }

    private static Optional<Component> createThreadName(Object perk) {
        return invokeComponent(perk, "getPerkName")
                .map(perkName -> Component.translatable("ars_nouveau.thread_of", perkName));
    }

    private static Optional<Component> createRitualTabletName(Object ritual) {
        return invokeResourceLocation(ritual, "getRegistryName")
                .map(id -> Component.translatable("ars_nouveau.tablet_of", itemNameComponent(id)));
    }

    private static Optional<Component> createBoundScriptName(Object familiar) {
        return invokeComponent(familiar, "getLangName")
                .map(familiarName -> Component.translatable("ars_nouveau.bound_script", familiarName));
    }

    private static Optional<Component> createGlyphName(Object spellPart) {
        return invokeString(spellPart, "getLocalizationKey")
                .map(Component::translatable)
                .map(spellName -> Component.translatable("ars_nouveau.glyph_of", spellName));
    }

    private static Component itemNameComponent(ResourceLocation id) {
        return Component.translatable("item." + id.getNamespace() + "." + id.getPath());
    }

    private static Optional<Object> readField(Object target, String fieldName) {
        String cacheKey = target.getClass().getName() + "#" + fieldName;
        Optional<Field> field = fieldCache.computeIfAbsent(cacheKey,
                k -> findField(target.getClass(), fieldName));
        return field.flatMap(f -> {
            try {
                return Optional.ofNullable(f.get(target));
            } catch (ReflectiveOperationException ignored) {
                return Optional.empty();
            }
        });
    }

    private static Optional<Field> findField(Class<?> startClass, String fieldName) {
        Class<?> current = startClass;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return Optional.of(field);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (SecurityException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<Component> invokeComponent(Object target, String methodName) {
        return invokeMethod(target, methodName)
                .filter(Component.class::isInstance)
                .map(Component.class::cast);
    }

    private static Optional<String> invokeString(Object target, String methodName) {
        return invokeMethod(target, methodName)
                .filter(String.class::isInstance)
                .map(String.class::cast);
    }

    private static Optional<ResourceLocation> invokeResourceLocation(Object target, String methodName) {
        return invokeMethod(target, methodName)
                .filter(ResourceLocation.class::isInstance)
                .map(ResourceLocation.class::cast);
    }

    private static Optional<Object> invokeMethod(Object target, String methodName) {
        String cacheKey = target.getClass().getName() + "#" + methodName;
        Optional<Method> method = methodCache.computeIfAbsent(cacheKey,
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
