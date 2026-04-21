package com.starskyxiii.polyglottooltip.integration.storagedrawers;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class StorageDrawersNameHelper {

    private static final String ITEM_DRAWERS = "com.jaquadro.minecraft.storagedrawers.item.ItemDrawers";
    private static final String ITEM_TRIM = "com.jaquadro.minecraft.storagedrawers.item.ItemTrim";
    private static final String BLOCK_STANDARD_DRAWERS = "com.jaquadro.minecraft.storagedrawers.block.BlockStandardDrawers";
    private static final String BLOCK_TRIM = "com.jaquadro.minecraft.storagedrawers.block.BlockTrim";

    private static final Map<String, Optional<Method>> METHOD_CACHE = new ConcurrentHashMap<>();

    private StorageDrawersNameHelper() {
    }

    public static Optional<String> tryResolveSpecialName(ItemStack stack,
                                                         Function<Component, Optional<String>> componentResolver) {
        return tryCreateSpecialName(stack).flatMap(componentResolver);
    }

    public static Optional<Component> tryCreateSpecialName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }

        Item item = stack.getItem();
        if (!isSupportedItem(item)) {
            return Optional.empty();
        }

        Block block = Block.byItem(item);
        if (block == null || !isSupportedBlock(block)) {
            return Optional.empty();
        }

        Optional<String> matKey = invokeString(block, "getMatKey");
        Optional<String> matNameKey = invokeString(block, "getNameMatKey");
        Optional<String> typeNameKey = invokeString(block, "getNameTypeKey");
        if (matKey.isEmpty() || matKey.get() == null || matKey.get().isBlank() || matNameKey.isEmpty() || typeNameKey.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(Component.translatable(typeNameKey.get(), Component.translatable(matNameKey.get())));
    }

    private static boolean isSupportedItem(Item item) {
        return isClassOrSuperclass(item, ITEM_DRAWERS) || isClassOrSuperclass(item, ITEM_TRIM);
    }

    private static boolean isSupportedBlock(Block block) {
        return isClassOrSuperclass(block, BLOCK_STANDARD_DRAWERS) || isClassOrSuperclass(block, BLOCK_TRIM);
    }

    private static boolean isClassOrSuperclass(Object target, String className) {
        Class<?> current = target.getClass();
        while (current != null) {
            if (className.equals(current.getName())) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
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
