package com.starskyxiii.polyglottooltip.minecraft;
//~ minecraft_116_packages

import com.starskyxiii.polyglottooltip.compat.figura.FiguraEmojiContext;
//? if >1.20.1 {
import net.minecraft.core.component.DataComponents;
//?} else {
/*//? if <=1.16.5 {
import net.minecraft.nbt.CompoundNBT;
//?} else {
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
//?}
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
//? if >1.20.1 {
import net.minecraft.world.item.enchantment.ItemEnchantments;
//?}

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.List;
//? if >1.20.1 {
import java.util.Objects;
//?}
import java.util.Optional;
import java.util.function.Function;

public final class ItemStackNameAdapter {
    public Optional<String> resolveDisplayName(ItemStack stack,
                                               Function<Component, Optional<String>> componentResolver,
                                               Iterable<? extends SpecialItemNameResolver> specialResolvers) {
        //? if <=1.20.1 {
        /*Optional<Component> customName = resolveCustomNameComponent(stack);
        if (customName.isPresent()) {
            return customName.flatMap(componentResolver);
        }
        *///?} else {
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
        //?}

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

        //? if >1.20.1 {
        Component defaultItemName = stack.get(DataComponents.ITEM_NAME);
        if (defaultItemName != null) {
            Optional<String> resolved = componentResolver.apply(defaultItemName);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        //?}

        return componentResolver.apply(stack.getHoverName());
    }

    public String resolveCurrentDisplayName(ItemStack stack) {
        return stack.getHoverName().getString();
    }

    public List<Component> getCraftingEnchantmentNames(ItemStack stack) {
        //? if <=1.20.1 {
        /*Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
        *///?} else {
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        //?}
        if (enchantments.isEmpty()) {
            return Collections.emptyList();
        }

        List<Component> names = new ArrayList<>();
        //? if <=1.20.1 {
        /*for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            names.add(entry.getKey().getFullname(entry.getValue()));
        }
        *///?} else {
        for (var entry : enchantments.entrySet()) {
            names.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue()));
        }
        //?}
        return Collections.unmodifiableList(names);
    }

    //? if >1.20.1 {
    private Optional<Component> resolvePatchedItemName(ItemStack stack) {
        Component itemName = stack.get(DataComponents.ITEM_NAME);
        if (itemName == null) {
            return Optional.empty();
        }
        Component defaultItemName = stack.getPrototype().get(DataComponents.ITEM_NAME);
        return Objects.equals(itemName, defaultItemName) ? Optional.empty() : Optional.of(itemName);
    }
    //?} else {
    /*private Optional<Component> resolveCustomNameComponent(ItemStack stack) {
        //? if <=1.16.5 {
        CompoundNBT displayTag = stack.getTagElement("display");
        if (displayTag == null || !displayTag.contains("Name", 8)) {
        //?} else {
        CompoundTag displayTag = stack.getTagElement(ItemStack.TAG_DISPLAY);
        if (displayTag == null || !displayTag.contains(ItemStack.TAG_DISPLAY_NAME, Tag.TAG_STRING)) {
        //?}
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(Component.Serializer.fromJson(
                    //? if <=1.16.5 {
                    displayTag.getString("Name")));
                    //?} else {
                    displayTag.getString(ItemStack.TAG_DISPLAY_NAME)));
                    //?}
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }
    *///?}

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
