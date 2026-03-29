package com.starskyxiii.polyglottooltip;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

public final class SecondaryTooltipUtil {

    private SecondaryTooltipUtil() {}

    public static void insertSecondaryNames(List<String> tooltip, ItemStack stack) {
        if (tooltip == null || stack == null || stack.getItem() == null || !shouldShowSecondaryLanguage()) {
            return;
        }

        String primaryName = EnumChatFormatting.getTextWithoutFormattingCodes(stack.getDisplayName());
        insertSecondaryNames(tooltip, 1, DisplayNameResolver.resolveSecondaryDisplayNames(stack), primaryName);
    }

    public static void insertSecondaryNames(List<String> tooltip, Collection<String> secondaryNames, String primaryName) {
        insertSecondaryNames(tooltip, tooltip == null || tooltip.isEmpty() ? 0 : 1, secondaryNames, primaryName);
    }

    public static void insertSecondaryNames(List<String> tooltip, int insertIndex, Collection<String> secondaryNames,
            String primaryName) {
        if (tooltip == null || secondaryNames == null || secondaryNames.isEmpty() || !shouldShowSecondaryLanguage()) {
            return;
        }

        Set<String> existingNames = collectExistingNames(tooltip);
        String normalizedPrimaryName = normalizeName(primaryName);
        if (!normalizedPrimaryName.isEmpty()) {
            existingNames.add(normalizedPrimaryName);
        }

        List<String> filteredSecondaryNames = new ArrayList<String>();
        for (String translatedName : secondaryNames) {
            String normalizedTranslatedName = normalizeName(translatedName);
            if (!normalizedTranslatedName.isEmpty() && !existingNames.contains(normalizedTranslatedName)) {
                filteredSecondaryNames.add(translatedName);
                existingNames.add(normalizedTranslatedName);
            }
        }

        if (filteredSecondaryNames.isEmpty()) {
            return;
        }

        insertIndex = Math.max(0, Math.min(insertIndex, tooltip.size()));
        for (int i = filteredSecondaryNames.size() - 1; i >= 0; i--) {
            tooltip.add(insertIndex, formatSecondaryLine(filteredSecondaryNames.get(i)));
        }
    }

    public static boolean shouldShowSecondaryLanguage() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.gameSettings == null) {
            return false;
        }

        if (Config.alwaysShow) {
            return true;
        }

        String currentLanguage = minecraft.gameSettings.language;
        for (String languageCode : Config.displayLanguages) {
            if (languageCode != null && !languageCode.equalsIgnoreCase(currentLanguage)) {
                return true;
            }
        }

        return false;
    }

    public static String formatSecondaryLine(String translatedName) {
        return EnumChatFormatting.GRAY + translatedName;
    }

    public static Set<String> collectExistingNames(List<String> tooltip) {
        LinkedHashSet<String> existingNames = new LinkedHashSet<String>();
        for (String line : tooltip) {
            String normalized = normalizeName(line);
            if (!normalized.isEmpty()) {
                existingNames.add(normalized);
            }
        }
        return existingNames;
    }

    public static String normalizeName(String value) {
        if (value == null) {
            return "";
        }

        String normalized = EnumChatFormatting.getTextWithoutFormattingCodes(value);
        if (normalized == null) {
            normalized = value;
        }

        return normalized.trim();
    }
}
