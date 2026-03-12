package com.starskyxiii.polyglottooltip.integration.arsnouveau;

import com.starskyxiii.polyglottooltip.integration.ReflectionHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.Function;

/**
 * Resolves special display names for Ars Nouveau items whose visible name is
 * determined by a private field (perk, ritual, familiar, or glyph object) rather
 * than a simple translation key on the {@link net.minecraft.world.item.Item} itself.
 *
 * <p>All class members are accessed via {@link com.starskyxiii.polyglottooltip.integration.ReflectionHelper}
 * because Ars Nouveau is not on the compile classpath.
 */
public final class ArsNouveauNameHelper {

    private static final String PERK_ITEM = "com.hollingsworth.arsnouveau.common.items.PerkItem";
    private static final String RITUAL_TABLET = "com.hollingsworth.arsnouveau.common.items.RitualTablet";
    private static final String FAMILIAR_SCRIPT = "com.hollingsworth.arsnouveau.common.items.FamiliarScript";
    private static final String GLYPH = "com.hollingsworth.arsnouveau.common.items.Glyph";

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
            case PERK_ITEM -> ReflectionHelper.readField(item, "perk")
                    .flatMap(ArsNouveauNameHelper::createThreadName);
            case RITUAL_TABLET -> ReflectionHelper.readField(item, "ritual")
                    .flatMap(ArsNouveauNameHelper::createRitualTabletName);
            case FAMILIAR_SCRIPT -> ReflectionHelper.readField(item, "familiar")
                    .flatMap(ArsNouveauNameHelper::createBoundScriptName);
            case GLYPH -> ReflectionHelper.readField(item, "spellPart")
                    .flatMap(ArsNouveauNameHelper::createGlyphName);
            default -> Optional.empty();
        };
    }

    private static Optional<Component> createThreadName(Object perk) {
        return invokeResourceLocation(perk, "getRegistryName")
                .map(id -> (Component) Component.translatable(id.getNamespace() + ".thread_of", itemNameComponent(id)))
                .or(() -> invokeComponent(perk, "getPerkName")
                        .map(perkName -> (Component) Component.translatable("ars_nouveau.thread_of", perkName)));
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

    private static Optional<Component> invokeComponent(Object target, String methodName) {
        return ReflectionHelper.invokeMethod(target, methodName)
                .filter(Component.class::isInstance)
                .map(Component.class::cast);
    }

    private static Optional<String> invokeString(Object target, String methodName) {
        return ReflectionHelper.invokeMethod(target, methodName)
                .filter(String.class::isInstance)
                .map(String.class::cast);
    }

    private static Optional<ResourceLocation> invokeResourceLocation(Object target, String methodName) {
        return ReflectionHelper.invokeMethod(target, methodName)
                .filter(ResourceLocation.class::isInstance)
                .map(ResourceLocation.class::cast);
    }
}
