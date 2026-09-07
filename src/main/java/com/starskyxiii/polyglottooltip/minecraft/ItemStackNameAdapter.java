package com.starskyxiii.polyglottooltip.minecraft;

import com.starskyxiii.polyglottooltip.compat.figura.FiguraEmojiContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public final class ItemStackNameAdapter {
    public Optional<String> resolveDisplayName(ItemStack stack,
                                               Function<Component, Optional<String>> componentResolver,
                                               Iterable<? extends SpecialItemNameResolver> specialResolvers) {
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            return componentResolver.apply(customName);
        }

        Optional<Component> patchedItemName = resolvePatchedItemName(stack);
        if (patchedItemName.isPresent()) {
            Optional<String> resolved = patchedItemName.flatMap(componentResolver);
            if (resolved.isPresent()) {
                return resolved;
            }
        }

        Optional<String> specialName = FiguraEmojiContext.supplyInHoverName(
                () -> resolveSpecialName(stack, componentResolver, specialResolvers));
        if (specialName.isPresent()) {
            return specialName;
        }

        Optional<String> generatedName = FiguraEmojiContext.supplyInHoverName(
                () -> componentResolver.apply(stack.getItem().getName(stack)));
        if (generatedName.isPresent()) {
            return generatedName;
        }

        Component defaultItemName = stack.get(DataComponents.ITEM_NAME);
        if (defaultItemName != null) {
            Optional<String> resolved = componentResolver.apply(defaultItemName);
            if (resolved.isPresent()) {
                return resolved;
            }
        }

        return componentResolver.apply(stack.getHoverName());
    }

    public String resolveCurrentDisplayName(ItemStack stack) {
        return stack.getHoverName().getString();
    }

    public List<Component> getCraftingEnchantmentNames(ItemStack stack) {
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        if (enchantments.isEmpty()) {
            return List.of();
        }

        List<Component> names = new ArrayList<>();
        for (var entry : enchantments.entrySet()) {
            names.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue()));
        }
        return List.copyOf(names);
    }

    private Optional<Component> resolvePatchedItemName(ItemStack stack) {
        Component itemName = stack.get(DataComponents.ITEM_NAME);
        if (itemName == null) {
            return Optional.empty();
        }
        Component defaultItemName = stack.getPrototype().get(DataComponents.ITEM_NAME);
        return Objects.equals(itemName, defaultItemName) ? Optional.empty() : Optional.of(itemName);
    }

    private Optional<String> resolveSpecialName(ItemStack stack,
                                                Function<Component, Optional<String>> componentResolver,
                                                Iterable<? extends SpecialItemNameResolver> specialResolvers) {
        for (SpecialItemNameResolver resolver : specialResolvers) {
            Optional<String> name = resolver.resolve(stack, componentResolver);
            if (name.isPresent()) {
                return name;
            }
        }
        return Optional.empty();
    }
}
