package com.starskyxiii.polyglottooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
//? if <=1.20.1 {
/*import net.minecraft.world.item.enchantment.EnchantmentHelper;
*///?} else {
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
//?}

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ItemTooltipAppender {
    private ItemTooltipAppender() {
    }

    public static void append(ItemStack stack, List<Component> tooltip) {
        if (stack.isEmpty() || !SecondaryTooltipUtil.shouldShowSecondaryLanguage()) {
            return;
        }
        LanguageCache cache = LanguageCache.getInstance();
        SecondaryTooltipUtil.insertSecondaryName(tooltip, stack);
        processEnchantments(stack, tooltip, cache);
    }

    private static void processEnchantments(ItemStack stack, List<Component> tooltip, LanguageCache cache) {
        //? if <=1.20.1 {
        /*Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
        if (enchants.isEmpty()) return;
        *///?} else {
        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        //?}

        Map<String, String> nameMap = new HashMap<>();
        fillEnchantmentNames(enchants, nameMap, cache);
        //? if >1.20.1 {
        fillEnchantmentNames(stored, nameMap, cache);
        //?}
        if (nameMap.isEmpty()) return;

        for (int i = 0; i < tooltip.size(); i++) {
            Component originalLine = tooltip.get(i);
            String secondary = nameMap.get(originalLine.getString());
            if (secondary != null) {
                Style originalStyle = originalLine.getStyle();
                Component appendedPart = Component.empty()
                        .append(Component.literal("｜").withStyle(net.minecraft.ChatFormatting.GRAY))
                        .append(Component.literal(secondary).withStyle(originalStyle));
                tooltip.set(i, originalLine.copy().append(appendedPart));
            }
        }
    }

    //? if <=1.20.1 {
    /*private static void fillEnchantmentNames(Map<Enchantment, Integer> enchants,
                                             Map<String, String> nameMap,
                                             LanguageCache cache) {
        for (var entry : enchants.entrySet()) {
            addEnchantmentName(entry.getKey().getFullname(entry.getValue()), nameMap, cache);
        }
    }
    *///?} else {
    private static void fillEnchantmentNames(ItemEnchantments enchants,
                                             Map<String, String> nameMap,
                                             LanguageCache cache) {
        for (var entry : enchants.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            addEnchantmentName(Enchantment.getFullname(holder, entry.getIntValue()), nameMap, cache);
        }
    }
    //?}

    private static void addEnchantmentName(Component fullName, Map<String, String> nameMap, LanguageCache cache) {
        String current = fullName.getString();
        String joined = cache.resolveComponentsForAll(fullName).stream()
                .filter(value -> !value.equals(current))
                .collect(Collectors.joining("｜"));
        if (!joined.isEmpty()) {
            nameMap.put(current, joined);
        }
    }
}
