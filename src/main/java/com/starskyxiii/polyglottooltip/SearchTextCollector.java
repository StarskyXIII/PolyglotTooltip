package com.starskyxiii.polyglottooltip;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

public final class SearchTextCollector {

    private SearchTextCollector() {}

    public static List<String> collectSearchableNames(ItemStack stack) {
        LinkedHashSet<String> searchableNames = new LinkedHashSet<String>();

        if (stack == null || stack.getItem() == null) {
            return new ArrayList<String>(searchableNames);
        }

        addName(searchableNames, stack.getDisplayName());

        for (String translatedName : DisplayNameResolver.resolveSecondaryDisplayNames(stack)) {
            addName(searchableNames, translatedName);
        }

        return new ArrayList<String>(searchableNames);
    }

    private static void addName(LinkedHashSet<String> searchableNames, String text) {
        if (text == null) {
            return;
        }

        String normalized = EnumChatFormatting.getTextWithoutFormattingCodes(text);
        if (normalized == null) {
            return;
        }

        normalized = normalized.trim();
        if (!normalized.isEmpty()) {
            searchableNames.addAll(ChineseScriptSearchMatcher.getSearchVariants(normalized));
        }
    }
}
