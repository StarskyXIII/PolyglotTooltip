package com.starskyxiii.polyglottooltip.integration.storagedrawers;
//~ minecraft_legacy_components

import com.starskyxiii.polyglottooltip.LanguageCache;
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
        //? if <=1.19.2 {
        /*if (stack == null || stack.isEmpty()
                || !isSupportedBlock(Block.byItem(stack.getItem()))) {
            return Optional.empty();
        }
        Optional<String> currentName = componentResolver.apply(
                Component.translatable(stack.getItem().getDescriptionId()));
        String primaryName = stack.getItem().getName(stack).getString();
        Optional<String> englishName = LanguageCache.getInstance().resolveEnglishComponent(
                Component.translatable(stack.getItem().getDescriptionId()));
        Optional<String> legacyName = tryCreateSpecialName(stack).flatMap(componentResolver);
        return preferTranslatedName(currentName, primaryName, englishName, legacyName);
        *///?} else {
        return tryCreateSpecialName(stack).flatMap(componentResolver);
        //?}
    }

    public static Optional<Component> tryCreateSpecialName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }

        Item item = stack.getItem();
        //? if >1.19.2 {
        if (!isSupportedItem(item)) {
            return Optional.empty();
        }
        //?}

        Block block = Block.byItem(item);
        if (block == null || !isSupportedBlock(block)) {
            return Optional.empty();
        }

        //? if <=1.19.2 {
        /*return createLegacyName(item.getDescriptionId());
        *///?} else {

        Optional<String> matKey = invokeString(block, "getMatKey");
        Optional<String> matNameKey = invokeString(block, "getNameMatKey");
        Optional<String> typeNameKey = invokeString(block, "getNameTypeKey");
        if (matKey.isEmpty() || matKey.get() == null || matKey.get().isBlank() || matNameKey.isEmpty() || typeNameKey.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(Component.translatable(typeNameKey.get(), Component.translatable(matNameKey.get())));
        //?}
    }

    static Optional<String> preferTranslatedName(Optional<String> currentName,
                                                  String primaryName,
                                                  Optional<String> englishName,
                                                  Optional<String> legacyName) {
        if (currentName.isPresent()
                && englishName.map(english -> !currentName.get().equals(english))
                .orElseGet(() -> !currentName.get().equals(primaryName))) {
            return currentName;
        }
        return legacyName.isPresent() ? legacyName : currentName;
    }

    //? if <=1.19.2 {
    /*private static Optional<Component> createLegacyName(String descriptionId) {
        String prefix = "block.storagedrawers.";
        if (descriptionId == null || !descriptionId.startsWith(prefix)) {
            return Optional.empty();
        }

        String path = descriptionId.substring(prefix.length());
        if (path.endsWith("_trim")) {
            String material = path.substring(0, path.length() - "_trim".length());
            if ("dark_oak".equals(material)) {
                material = "big_oak";
            }
            return Optional.of(Component.translatable("tile.storagedrawers.trim." + material + ".name"));
        }

        String layout = drawerLayout(path);
        if (layout == null) {
            return Optional.empty();
        }
        return Optional.of(Component.translatable(
                "tile.storagedrawers.basicDrawers." + layout + ".name"));
    }

    private static String drawerLayout(String path) {
        if (path.endsWith("_full_drawers_1")) return "fulldrawers1";
        if (path.endsWith("_full_drawers_2")) return "fulldrawers2";
        if (path.endsWith("_full_drawers_4")) return "fulldrawers4";
        if (path.endsWith("_half_drawers_2")) return "halfdrawers2";
        if (path.endsWith("_half_drawers_4")) return "halfdrawers4";
        return null;
    }
    *///?}

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
