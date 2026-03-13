package com.starskyxiii.polyglottooltip.integration.productivebees;

import com.starskyxiii.polyglottooltip.integration.ReflectionHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.Function;

public final class ProductiveBeesNameHelper {

    private static final String MOD_ID = "productivebees";
    private static final String BEE_INGREDIENT = "cy.jdkdigital.productivebees.compat.jei.ingredients.BeeIngredient";
    private static final String CONFIGURABLE_HONEYCOMB = "configurable_honeycomb";
    private static final String CONFIGURABLE_COMB_BLOCK = "configurable_comb";
    private static final String CONFIGURABLE_SPAWN_EGG = "spawn_egg_configurable_bee";
    private static final String ENGLISH_BEE_SUFFIX = " Bee";

    private ProductiveBeesNameHelper() {
    }

    public static Optional<String> tryResolveSpecialName(
            ItemStack stack,
            Function<Component, Optional<String>> componentResolver
    ) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!MOD_ID.equals(itemId.getNamespace())) {
            return Optional.empty();
        }

        return switch (itemId.getPath()) {
            case CONFIGURABLE_HONEYCOMB -> resolveCombLikeName(
                    stack,
                    "item.productivebees.honeycomb_configurable",
                    componentResolver
            );
            case CONFIGURABLE_COMB_BLOCK -> resolveCombLikeName(
                    stack,
                    "block.productivebees.comb_configurable",
                    componentResolver
            );
            case CONFIGURABLE_SPAWN_EGG -> resolveSpawnEggName(stack, componentResolver);
            default -> Optional.empty();
        };
    }

    public static Optional<Component> tryCreateBeeIngredientName(Object ingredient) {
        if (ingredient == null || !BEE_INGREDIENT.equals(ingredient.getClass().getName())) {
            return Optional.empty();
        }

        Optional<ResourceLocation> beeType = ReflectionHelper.invokeMethod(ingredient, "getBeeType")
                .filter(ResourceLocation.class::isInstance)
                .map(ResourceLocation.class::cast);
        if (beeType.isPresent()) {
            return Optional.of(createBeeNameComponent(beeType.get()));
        }

        return ReflectionHelper.invokeMethod(ingredient, "getBeeEntity")
                .filter(EntityType.class::isInstance)
                .map(EntityType.class::cast)
                .map(EntityType::getDescription);
    }

    private static Optional<String> resolveCombLikeName(
            ItemStack stack,
            String templateKey,
            Function<Component, Optional<String>> componentResolver
    ) {
        return resolveBeeName(stack, componentResolver)
                .map(ProductiveBeesNameHelper::stripEnglishBeeSuffix)
                .flatMap(beeName -> componentResolver.apply(Component.translatable(templateKey, beeName)));
    }

    private static Optional<String> resolveSpawnEggName(
            ItemStack stack,
            Function<Component, Optional<String>> componentResolver
    ) {
        return resolveBeeName(stack, componentResolver)
                .flatMap(beeName -> componentResolver.apply(
                        Component.translatable("item.productivebees.spawn_egg_configurable", beeName)
                ));
    }

    private static Optional<String> resolveBeeName(
            ItemStack stack,
            Function<Component, Optional<String>> componentResolver
    ) {
        return findBeeType(stack)
                .map(ProductiveBeesNameHelper::createBeeNameComponent)
                .flatMap(componentResolver);
    }

    private static Optional<String> findBeeType(ItemStack stack) {
        CompoundTag entityTag = stack.getTagElement("EntityTag");
        if (entityTag == null || !entityTag.contains("type")) {
            return Optional.empty();
        }
        String type = entityTag.getString("type");
        return type.isEmpty() ? Optional.empty() : Optional.of(type);
    }

    private static Component createBeeNameComponent(String beeType) {
        return createBeeNameComponent(ResourceLocation.tryParse(beeType));
    }

    private static Component createBeeNameComponent(ResourceLocation beeId) {
        if (beeId == null) {
            return Component.empty();
        }

        String beePath = beeId.getPath();
        if (!MOD_ID.equals(beeId.getNamespace())) {
            return Component.translatable("entity." + beeId.getNamespace() + "." + beePath);
        }

        if (beePath.endsWith("_bee")) {
            beePath = beePath.substring(0, beePath.length() - 4);
        }

        return Component.translatable("entity.productivebees." + beePath + "_bee");
    }

    private static String stripEnglishBeeSuffix(String name) {
        return name.endsWith(ENGLISH_BEE_SUFFIX)
                ? name.substring(0, name.length() - ENGLISH_BEE_SUFFIX.length())
                : name;
    }
}
