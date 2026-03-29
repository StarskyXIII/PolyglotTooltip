package com.starskyxiii.polyglottooltip;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;

public final class EnchantmentTooltipUtil {

    private static final String ENCHANTMENT_SEPARATOR = "｜";

    private static final char SECTION_SIGN = '\u00A7';

    private EnchantmentTooltipUtil() {}

    public static void insertSecondaryEnchantments(List<String> tooltip, ItemStack stack) {
        if (tooltip == null || stack == null || stack.getItem() == null || !SecondaryTooltipUtil.shouldShowSecondaryLanguage()) {
            return;
        }

        Map<String, List<String>> secondaryNameMap = resolveSecondaryEnchantmentNames(stack);
        if (secondaryNameMap.isEmpty()) {
            return;
        }

        Set<String> existingNames = SecondaryTooltipUtil.collectExistingNames(tooltip);
        for (int i = 0; i < tooltip.size(); i++) {
            String line = tooltip.get(i);
            String normalizedLine = normalizeTooltipEnchantmentLine(line);
            List<String> secondaryNames = secondaryNameMap.get(normalizedLine);
            if (secondaryNames == null || secondaryNames.isEmpty()) {
                continue;
            }

            List<String> filteredSecondaryNames = filterSecondaryNames(secondaryNames, existingNames, normalizedLine);
            if (filteredSecondaryNames.isEmpty()) {
                continue;
            }

            tooltip.set(i, appendSecondaryNames(line, filteredSecondaryNames));
        }
    }

    private static Map<String, List<String>> resolveSecondaryEnchantmentNames(ItemStack stack) {
        Map<String, List<String>> secondaryNameMap = new LinkedHashMap<String, List<String>>();
        for (EnchantmentEntry entry : getEnchantmentEntries(stack)) {
            ResolvedEnchantmentLine resolvedLine = resolveEnchantmentLine(entry);
            if (resolvedLine != null) {
                String normalizedPrimaryText = normalizeTooltipEnchantmentLine(resolvedLine.primaryText);
                if (!normalizedPrimaryText.isEmpty() && !secondaryNameMap.containsKey(normalizedPrimaryText)) {
                    secondaryNameMap.put(normalizedPrimaryText, resolvedLine.secondaryTexts);
                }
            }
        }
        return secondaryNameMap;
    }

    private static List<String> filterSecondaryNames(Collection<String> secondaryNames, Set<String> existingNames,
            String primaryText) {
        List<String> filteredSecondaryNames = new ArrayList<String>();
        String normalizedPrimaryText = normalizeTooltipEnchantmentLine(primaryText);
        if (!normalizedPrimaryText.isEmpty()) {
            existingNames.add(normalizedPrimaryText);
        }

        for (String secondaryName : secondaryNames) {
            String normalizedSecondaryName = normalizeTooltipEnchantmentLine(secondaryName);
            if (!normalizedSecondaryName.isEmpty() && !existingNames.contains(normalizedSecondaryName)) {
                filteredSecondaryNames.add(secondaryName);
                existingNames.add(normalizedSecondaryName);
            }
        }

        return filteredSecondaryNames;
    }

    private static String translateEnchantment(String languageCode, Enchantment enchantment, int level) {
        String enchantmentName = LanguageCache.translate(languageCode, enchantment.getName());
        if (enchantmentName == null || enchantmentName.isEmpty()) {
            return null;
        }

        if (level == 1 && enchantment.getMaxLevel() == 1) {
            return enchantmentName;
        }

        String levelText = LanguageCache.translate(languageCode, "enchantment.level." + level);
        if (levelText == null || levelText.isEmpty()) {
            return enchantmentName;
        }

        return enchantmentName + " " + levelText;
    }

    private static String appendSecondaryNames(String primaryLine, Collection<String> secondaryNames) {
        StringBuilder builder = new StringBuilder(primaryLine);
        String primaryFormatting = extractLeadingFormattingCodes(stripLeadingTooltipDecorations(primaryLine));

        boolean first = true;
        for (String secondaryName : secondaryNames) {
            if (secondaryName == null || secondaryName.trim().isEmpty()) {
                continue;
            }

            if (!first) {
                builder.append(EnumChatFormatting.GRAY).append(ENCHANTMENT_SEPARATOR);
            } else {
                builder.append(EnumChatFormatting.GRAY).append(' ').append(ENCHANTMENT_SEPARATOR);
                first = false;
            }

            builder.append(' ');
            if (!primaryFormatting.isEmpty()) {
                builder.append(primaryFormatting);
            }
            builder.append(secondaryName);
        }

        return builder.toString();
    }

    private static ResolvedEnchantmentLine resolveEnchantmentLine(EnchantmentEntry entry) {
        if (entry == null || entry.enchantment == null) {
            return null;
        }

        String primaryText = entry.enchantment.getTranslatedName(entry.level);
        if (primaryText == null || primaryText.trim().isEmpty()) {
            return null;
        }

        LinkedHashSet<String> secondaryTexts = new LinkedHashSet<String>();
        for (String languageCode : Config.displayLanguages) {
            String secondaryText = translateEnchantment(languageCode, entry.enchantment, entry.level);
            if (secondaryText != null && !secondaryText.isEmpty()) {
                secondaryTexts.add(secondaryText);
            }
        }

        if (secondaryTexts.isEmpty()) {
            return null;
        }

        return new ResolvedEnchantmentLine(primaryText, new ArrayList<String>(secondaryTexts));
    }

    private static String extractLeadingFormattingCodes(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i + 1 < text.length();) {
            if (text.charAt(i) != SECTION_SIGN) {
                break;
            }

            builder.append(text.charAt(i)).append(text.charAt(i + 1));
            i += 2;
        }
        return builder.toString();
    }

    private static String normalizeTooltipEnchantmentLine(String text) {
        String normalized = SecondaryTooltipUtil.normalizeName(text);
        if (normalized.isEmpty()) {
            return normalized;
        }

        return stripLeadingTooltipDecorations(normalized).trim();
    }

    private static String stripLeadingTooltipDecorations(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        int index = 0;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (character == SECTION_SIGN) {
                break;
            }

            if (Character.isWhitespace(character) || isTooltipDecoration(character)) {
                index++;
                continue;
            }

            break;
        }

        return text.substring(index);
    }

    private static boolean isTooltipDecoration(char character) {
        return character >= '\uE000' && character <= '\uF8FF'
            || character == '\u2022'
            || character == '\u00B7';
    }

    private static List<EnchantmentEntry> getEnchantmentEntries(ItemStack stack) {
        List<EnchantmentEntry> entries = new ArrayList<EnchantmentEntry>();
        appendEntries(entries, stack.getEnchantmentTagList());

        if (stack.getItem() == Items.enchanted_book) {
            appendEntries(entries, Items.enchanted_book.func_92110_g(stack));
        }

        return entries;
    }

    private static void appendEntries(List<EnchantmentEntry> entries, NBTTagList tagList) {
        if (tagList == null) {
            return;
        }

        for (int i = 0; i < tagList.tagCount(); i++) {
            NBTTagCompound tag = tagList.getCompoundTagAt(i);
            int enchantmentId = tag.getShort("id");
            int level = tag.getShort("lvl");
            if (enchantmentId < 0 || enchantmentId >= Enchantment.enchantmentsList.length) {
                continue;
            }

            Enchantment enchantment = Enchantment.enchantmentsList[enchantmentId];
            if (enchantment != null) {
                entries.add(new EnchantmentEntry(enchantment, level));
            }
        }
    }

    private static final class EnchantmentEntry {

        private final Enchantment enchantment;
        private final int level;

        private EnchantmentEntry(Enchantment enchantment, int level) {
            this.enchantment = enchantment;
            this.level = level;
        }
    }

    private static final class ResolvedEnchantmentLine {

        private final String primaryText;
        private final List<String> secondaryTexts;

        private ResolvedEnchantmentLine(String primaryText, List<String> secondaryTexts) {
            this.primaryText = primaryText;
            this.secondaryTexts = secondaryTexts;
        }
    }
}
