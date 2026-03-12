package com.starskyxiii.polyglottooltip;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles tooltip post-processing for item stacks by:
 * <ol>
 *   <li>Inserting one gray secondary-language item name line per configured
 *       language directly below the primary item name.</li>
 *   <li>Appending secondary-language enchantment names to existing enchantment
 *       lines.</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = PolyglotTooltip.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
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

    /**
     * For each enchantment on {@code stack} (including stored enchantments on books),
     * finds the matching tooltip line and appends the secondary-language names
     * joined by the tooltip separator.
     * Lines with no secondary-language translation are left unchanged.
     */
    private static void processEnchantments(ItemStack stack, List<Component> tooltip, LanguageCache cache) {
        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
        if (enchants.isEmpty()) return;

        Map<String, String> nameMap = new HashMap<>();
        fillEnchantmentNames(enchants, nameMap, cache);
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
    private static void fillEnchantmentNames(Map<Enchantment, Integer> enchants,
                                             Map<String, String> nameMap,
                                             LanguageCache cache) {
        for (var entry : enchants.entrySet()) {
            Enchantment enchant = entry.getKey();
            int level = entry.getValue();

            Component fullName = enchant.getFullname(level);
            String current = fullName.getString();

            String joined = cache.resolveComponentsForAll(fullName).stream()
                    .filter(s -> !s.equals(current))
                    .collect(Collectors.joining("｜"));

            if (!joined.isEmpty()) {
                nameMap.put(current, joined);
            }
        }
    }
}
