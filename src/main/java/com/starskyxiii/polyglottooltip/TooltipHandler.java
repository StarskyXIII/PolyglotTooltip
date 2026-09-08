package com.starskyxiii.polyglottooltip;
//~ forge_neoforge_package

import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
//? if >1.20.1 {
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringDecomposer;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
//? if <=1.20.1 {
/*import net.minecraft.world.item.enchantment.EnchantmentHelper;
*///?} else {
import net.minecraft.world.item.enchantment.ItemEnchantments;
//?}
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
//? if <=1.20.1 {
/*import net.neoforged.fml.common.Mod;
*///?} else {
import net.neoforged.fml.common.EventBusSubscriber;
//?}
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
//? if >=26 {
/*import net.neoforged.neoforge.event.entity.player.FluidTooltipEvent;
*///?}
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles tooltip post-processing for item stacks by:
 * <ol>
 *   <li>Inserting one formatted secondary-language item name line per configured
 *       language directly below the primary item name.</li>
 *   <li>Appending secondary-language enchantment names to existing enchantment
 *       lines.</li>
 *   <li>Reordering generated tooltip components so secondary-name lines stay
 *       directly below the primary item name.</li>
 * </ol>
 */
//? if <=1.20.1 {
/*@Mod.EventBusSubscriber(modid = PolyglotTooltip.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
*///?} else {
@EventBusSubscriber(modid = PolyglotTooltip.MODID, value = Dist.CLIENT)
//?}
public class TooltipHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().isEmpty()) return;
        if (!SecondaryTooltipUtil.shouldShowSecondaryLanguage()) return;

        LanguageCache cache = LanguageCache.getInstance();
        List<Component> tooltip = event.getToolTip();

        // Insert secondary item names near the top of the tooltip.
        SecondaryTooltipUtil.insertSecondaryName(tooltip, event.getItemStack());

        // Append secondary-language names to matching enchantment lines.
        processEnchantments(event.getItemStack(), tooltip, cache);
    }

    //? if >=26 {
    /*@SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFluidTooltip(FluidTooltipEvent event) {
        if (!SecondaryTooltipUtil.shouldShowSecondaryLanguage()) return;
        SecondaryTooltipUtil.insertSecondaryName(event.getToolTip(), event.getFluidStack().getHoverName());
    }
    *///?}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGatherTooltipComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !SecondaryTooltipUtil.shouldShowSecondaryLanguage()) return;

        List<String> names = SecondaryTooltipUtil.getSecondaryNames(stack);
        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();

        // Keep generated secondary-name lines grouped directly under the title.
        List<Either<FormattedText, TooltipComponent>> secondaryLines = new ArrayList<>();
        for (String secondary : names) {
            int idx = findTooltipTextIndex(elements, secondary);
            if (idx >= 0) {
                secondaryLines.add(elements.remove(idx));
            }
        }

        int targetIndex = findInsertionIndexAfterTitleAttachments(elements);
        for (int i = secondaryLines.size() - 1; i >= 0; i--) {
            elements.add(targetIndex, secondaryLines.get(i));
        }
    }

    /**
     * For each enchantment on {@code stack} (including stored enchantments on books),
     * finds the matching tooltip line and appends the secondary-language names
     * joined by the tooltip separator.
     * Lines with no secondary-language translation are left unchanged.
     */
    private static void processEnchantments(ItemStack stack, List<Component> tooltip, LanguageCache cache) {
        //? if <=1.20.1 {
        /*Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
        if (enchants.isEmpty()) return;
        *///?} else {
        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments stored  = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        //?}

        // Map current-language enchantment lines to their joined secondary-language text.
        Map<String, String> nameMap = new HashMap<>();
        fillEnchantmentNames(enchants, nameMap, cache);
        //? if >1.20.1 {
        fillEnchantmentNames(stored, nameMap, cache);
        //?}
        if (nameMap.isEmpty()) return;

        for (int i = 0; i < tooltip.size(); i++) {
            Component originalLine = tooltip.get(i);
            String lineText = originalLine.getString();
            String secondary = nameMap.get(lineText);
            if (secondary != null) {
                Style originalStyle = originalLine.getStyle();
                Component appendedPart = Component.empty()
                        .append(Component.literal("｜").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(secondary).withStyle(originalStyle));
                tooltip.set(i, originalLine.copy().append(appendedPart));
            }
        }
    }

    /**
     * Populates {@code nameMap} with entries for each enchantment in {@code enchants}.
     * An entry maps the current-language full name to all secondary-language names
     * joined by the same separator used in the tooltip.
     */
    //? if <=1.20.1 {
    /*private static void fillEnchantmentNames(Map<Enchantment, Integer> enchants,
                                             Map<String, String> nameMap,
                                             LanguageCache cache) {
        for (var entry : enchants.entrySet()) {
            Enchantment enchantment = entry.getKey();
            int level = entry.getValue();
            Component fullName = enchantment.getFullname(level);
            addEnchantmentName(fullName, nameMap, cache);
        }
    }
    *///?} else {
    private static void fillEnchantmentNames(ItemEnchantments enchants,
                                             Map<String, String> nameMap,
                                             LanguageCache cache) {
        for (var entry : enchants.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            int level = entry.getIntValue();
            Component fullName = Enchantment.getFullname(holder, level);
            addEnchantmentName(fullName, nameMap, cache);
        }
    }
    //?}

    private static void addEnchantmentName(Component fullName,
                                           Map<String, String> nameMap,
                                           LanguageCache cache) {
        String current = fullName.getString();
        String joined = cache.resolveComponentsForAll(fullName).stream()
                .filter(s -> !s.equals(current))
                .collect(Collectors.joining("｜"));
        if (!joined.isEmpty()) {
            nameMap.put(current, joined);
        }
    }

    private static int findTooltipTextIndex(List<Either<FormattedText, TooltipComponent>> elements, String targetText) {
        for (int i = 0; i < elements.size(); i++) {
            Either<FormattedText, TooltipComponent> element = elements.get(i);
            if (element.left().isPresent()) {
                String text = StringDecomposer.getPlainText(element.left().get());
                if (targetText.equals(text)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int findInsertionIndexAfterTitleAttachments(List<Either<FormattedText, TooltipComponent>> elements) {
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i).left().isPresent()) {
                int insertAt = Math.min(i + 1, elements.size());
                while (insertAt < elements.size() && isTitleAttachedTooltip(elements.get(insertAt))) {
                    insertAt++;
                }
                return insertAt;
            }
        }
        return 0;
    }

    private static boolean isTitleAttachedTooltip(Either<FormattedText, TooltipComponent> element) {
        return element.right()
                .map(component -> "com.hollingsworth.arsnouveau.client.gui.SchoolTooltip".equals(component.getClass().getName()))
                .orElse(false);
    }
}
