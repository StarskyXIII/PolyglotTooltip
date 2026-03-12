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
 * so its tier is incremented by one when building the translation key.  All other
 * addon types store their tier directly as displayed.
 */
public final class IndustrialForegoingNameHelper {

    private static final String LASER_LENS_ITEM = "com.buuz135.industrial.item.LaserLensItem";
    private static final String RANGE_ADDON_ITEM = "com.buuz135.industrial.item.addon.RangeAddonItem";
    private static final String SPEED_ADDON_ITEM = "com.buuz135.industrial.item.addon.SpeedAddonItem";
    private static final String EFFICIENCY_ADDON_ITEM = "com.buuz135.industrial.item.addon.EfficiencyAddonItem";
    private static final String PROCESSING_ADDON_ITEM = "com.buuz135.industrial.item.addon.ProcessingAddonItem";

    private IndustrialForegoingNameHelper() {
    }

    public static Optional<Component> tryCreateSpecialName(ItemStack stack) {
        return tryCreateSpecialName(stack.getItem());
    }

    public static Optional<String> tryResolveSpecialName(ItemStack stack, Function<Component, Optional<String>> componentResolver) {
        return switch (stack.getItem().getClass().getName()) {
            case LASER_LENS_ITEM -> ReflectionHelper.readField(stack.getItem(), "color", Integer.class)
                    .map(DyeColor::byId)
                    .flatMap(color -> componentResolver.apply(createLaserLensName(color)));
            case RANGE_ADDON_ITEM -> ReflectionHelper.readField(stack.getItem(), "tier", Integer.class)
                    .flatMap(tier -> componentResolver.apply(createAddonName("item.industrialforegoing.range_addon", tier + 1)));
            case SPEED_ADDON_ITEM -> ReflectionHelper.readField(stack.getItem(), "tier", Integer.class)
                    .flatMap(tier -> componentResolver.apply(createAddonName("item.industrialforegoing.speed", tier)));
            case EFFICIENCY_ADDON_ITEM -> ReflectionHelper.readField(stack.getItem(), "tier", Integer.class)
                    .flatMap(tier -> componentResolver.apply(createAddonName("item.industrialforegoing.efficiency", tier)));
            case PROCESSING_ADDON_ITEM -> ReflectionHelper.readField(stack.getItem(), "tier", Integer.class)
                    .flatMap(tier -> componentResolver.apply(createAddonName("item.industrialforegoing.processing", tier)));
            default -> Optional.empty();
        };
    }

    public static Optional<Component> tryCreateSpecialName(Item item) {
        return switch (item.getClass().getName()) {
            case LASER_LENS_ITEM -> ReflectionHelper.readField(item, "color", Integer.class)
                    .map(DyeColor::byId)
                    .map(IndustrialForegoingNameHelper::createLaserLensName);
            case RANGE_ADDON_ITEM -> ReflectionHelper.readField(item, "tier", Integer.class)
                    .map(tier -> createAddonName("item.industrialforegoing.range_addon", tier + 1));
            case SPEED_ADDON_ITEM -> ReflectionHelper.readField(item, "tier", Integer.class)
                    .map(tier -> createAddonName("item.industrialforegoing.speed", tier));
            case EFFICIENCY_ADDON_ITEM -> ReflectionHelper.readField(item, "tier", Integer.class)
                    .map(tier -> createAddonName("item.industrialforegoing.efficiency", tier));
            case PROCESSING_ADDON_ITEM -> ReflectionHelper.readField(item, "tier", Integer.class)
                    .map(tier -> createAddonName("item.industrialforegoing.processing", tier));
            default -> Optional.empty();
        };
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
}
