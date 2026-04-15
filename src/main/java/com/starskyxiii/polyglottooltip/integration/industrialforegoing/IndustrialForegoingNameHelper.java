package com.starskyxiii.polyglottooltip.integration.industrialforegoing;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class IndustrialForegoingNameHelper {

    private static final String LASER_LENS_ITEM = "com.buuz135.industrial.item.LaserLensItem";
    private static final String RANGE_ADDON_ITEM = "com.buuz135.industrial.item.addon.RangeAddonItem";
    private static final String SPEED_ADDON_ITEM = "com.buuz135.industrial.item.addon.SpeedAddonItem";
    private static final String EFFICIENCY_ADDON_ITEM = "com.buuz135.industrial.item.addon.EfficiencyAddonItem";
    private static final String PROCESSING_ADDON_ITEM = "com.buuz135.industrial.item.addon.ProcessingAddonItem";
    private static final String RANGE_ADDON_KEY = "item.industrialforegoing.range_addon";
    private static final String SPEED_ADDON_KEY = "item.industrialforegoing.speed";
    private static final String EFFICIENCY_ADDON_KEY = "item.industrialforegoing.efficiency";
    private static final String PROCESSING_ADDON_KEY = "item.industrialforegoing.processing";

    // Cache resolved Field objects keyed by "className#fieldName".
    // Item instances are registered singletons — their fields never change.
    private static final Map<String, Optional<Field>> fieldCache = new HashMap<>();

    private IndustrialForegoingNameHelper() {
    }

    public static Optional<Component> tryCreateSpecialName(ItemStack stack) {
        return tryCreateSpecialName(stack.getItem());
    }

    public static Optional<String> tryResolveSpecialName(ItemStack stack, Function<Component, Optional<String>> componentResolver) {
        Item item = stack.getItem();
        if (isClassOrSuperclass(item, LASER_LENS_ITEM)) {
            return readField(item, "color", DyeColor.class)
                    .flatMap(color -> componentResolver.apply(createLaserLensName(color)));
        }

        return resolveAddonDescriptor(item)
                .flatMap(descriptor -> readIntField(item, "tier")
                        .flatMap(tier -> resolveAddonName(descriptor.translationKey(), tier + descriptor.tierOffset(), componentResolver)));
    }

    public static Optional<Component> tryCreateSpecialName(Item item) {
        if (isClassOrSuperclass(item, LASER_LENS_ITEM)) {
            return readField(item, "color", DyeColor.class)
                    .map(IndustrialForegoingNameHelper::createLaserLensName);
        }

        return resolveAddonDescriptor(item)
                .flatMap(descriptor -> readIntField(item, "tier")
                        .map(tier -> createAddonName(descriptor.translationKey(), tier + descriptor.tierOffset())));
    }

    public static Component createLaserLensName(DyeColor color) {
        return Component.translatable(
                "item.industrialforegoing.laser_lens",
                Component.translatable("color.minecraft." + color.getName())
        );
    }

    public static Component createAddonName(String addonTypeKey, int tier) {
        return Component.empty()
                .append(Component.translatable("item.industrialforegoing.addon"))
                .append(Component.translatable(addonTypeKey))
                .append(Component.translatable("item.industrialforegoing.tier"))
                .append(Component.literal(Integer.toString(tier)));
    }

    private static Optional<String> resolveAddonName(
            String addonTypeKey,
            int tier,
            Function<Component, Optional<String>> componentResolver
    ) {
        Optional<String> addon = componentResolver.apply(Component.translatable("item.industrialforegoing.addon"));
        Optional<String> type = componentResolver.apply(Component.translatable(addonTypeKey));
        Optional<String> tierLabel = componentResolver.apply(Component.translatable("item.industrialforegoing.tier"));
        if (addon.isEmpty() || type.isEmpty() || tierLabel.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(addon.get() + type.get() + tierLabel.get() + tier);
    }

    private static Optional<AddonDescriptor> resolveAddonDescriptor(Item item) {
        if (isClassOrSuperclass(item, RANGE_ADDON_ITEM)) {
            return Optional.of(new AddonDescriptor(RANGE_ADDON_KEY, 1));
        }
        if (isClassOrSuperclass(item, SPEED_ADDON_ITEM)) {
            return Optional.of(new AddonDescriptor(SPEED_ADDON_KEY, 0));
        }
        if (isClassOrSuperclass(item, EFFICIENCY_ADDON_ITEM)) {
            return Optional.of(new AddonDescriptor(EFFICIENCY_ADDON_KEY, 0));
        }
        if (isClassOrSuperclass(item, PROCESSING_ADDON_ITEM)) {
            return Optional.of(new AddonDescriptor(PROCESSING_ADDON_KEY, 0));
        }
        return Optional.empty();
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

    private static Optional<Integer> readIntField(Object target, String fieldName) {
        return readField(target, fieldName, Integer.class);
    }

    private static <T> Optional<T> readField(Object target, String fieldName, Class<T> fieldType) {
        String cacheKey = target.getClass().getName() + "#" + fieldName;
        Optional<Field> field = fieldCache.computeIfAbsent(cacheKey,
                k -> findField(target.getClass(), fieldName));
        return field.flatMap(f -> {
            try {
                Object value = f.get(target);
                return fieldType.isInstance(value) ? Optional.of(fieldType.cast(value)) : Optional.empty();
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

    private record AddonDescriptor(String translationKey, int tierOffset) {
    }
}
