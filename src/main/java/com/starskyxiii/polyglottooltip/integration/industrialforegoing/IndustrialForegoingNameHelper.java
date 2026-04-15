package com.starskyxiii.polyglottooltip.integration.industrialforegoing;

import com.starskyxiii.polyglottooltip.integration.ReflectionHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.Function;

/**
 * Resolves special display names for Industrial Foregoing items whose name
 * encodes a tier or color stored in a private field (laser lens color, addon tier).
 *
 * <p>All class members are accessed via {@link com.starskyxiii.polyglottooltip.integration.ReflectionHelper}
 * because Industrial Foregoing is not on the compile classpath.
 *
 * <p>Note: {@code RangeAddonItem.tier} is stored zero-based but displayed one-based,
 * so its tier is incremented by one when building the translation key. All other
 * addon types store their tier directly as displayed.
 */
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

    private IndustrialForegoingNameHelper() {
    }

    public static Optional<Component> tryCreateSpecialName(ItemStack stack) {
        return tryCreateSpecialName(stack.getItem());
    }

    public static Optional<String> tryResolveSpecialName(ItemStack stack, Function<Component, Optional<String>> componentResolver) {
        Item item = stack.getItem();
        if (isClassOrSuperclass(item, LASER_LENS_ITEM)) {
            return ReflectionHelper.readField(item, "color", Integer.class)
                    .map(DyeColor::byId)
                    .flatMap(color -> componentResolver.apply(createLaserLensName(color)));
        }

        return resolveAddonDescriptor(item)
                .flatMap(descriptor -> ReflectionHelper.readField(item, "tier", Integer.class)
                        .flatMap(tier -> componentResolver.apply(createAddonName(
                                descriptor.translationKey(),
                                tier + descriptor.tierOffset()
                        ))));
    }

    public static Optional<Component> tryCreateSpecialName(Item item) {
        if (isClassOrSuperclass(item, LASER_LENS_ITEM)) {
            return ReflectionHelper.readField(item, "color", Integer.class)
                    .map(DyeColor::byId)
                    .map(IndustrialForegoingNameHelper::createLaserLensName);
        }

        return resolveAddonDescriptor(item)
                .flatMap(descriptor -> ReflectionHelper.readField(item, "tier", Integer.class)
                        .map(tier -> createAddonName(
                                descriptor.translationKey(),
                                tier + descriptor.tierOffset()
                        )));
    }

    public static Component createLaserLensName(DyeColor color) {
        return Component.translatable("color.minecraft." + color.getName())
                .append(Component.literal(" "))
                .append(Component.translatable("item.industrialforegoing.laser_lens"));
    }

    public static Component createAddonName(String addonTypeKey, int tier) {
        return Component.translatable("item.industrialforegoing.addon")
                .append(Component.translatable(addonTypeKey))
                .append(Component.literal("Tier " + tier + " "));
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

    private record AddonDescriptor(String translationKey, int tierOffset) {
    }
}
