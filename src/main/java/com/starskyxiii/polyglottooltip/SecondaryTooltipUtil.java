package com.starskyxiii.polyglottooltip;

import java.util.ArrayList;
import java.util.List;

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
        List<String> secondaryNames = new ArrayList<String>();

        for (String translatedName : SearchTextCollector.collectSearchableNames(stack)) {
            if (!translatedName.equals(primaryName)) {
                secondaryNames.add(translatedName);
            }
        }

        if (secondaryNames.isEmpty()) {
            return;
        }

        int insertIndex = tooltip.isEmpty() ? 0 : 1;
        for (int i = secondaryNames.size() - 1; i >= 0; i--) {
            tooltip.add(insertIndex, formatSecondaryLine(secondaryNames.get(i)));
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

    private static String formatSecondaryLine(String translatedName) {
        return EnumChatFormatting.GRAY + "\u00A0" + translatedName;
    }
}
