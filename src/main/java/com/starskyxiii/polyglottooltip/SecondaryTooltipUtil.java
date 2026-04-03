package com.starskyxiii.polyglottooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class SecondaryTooltipUtil {

    private SecondaryTooltipUtil() {
    }

    public static boolean shouldShowSecondaryLanguage() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return false;
        if (Config.ALWAYS_SHOW.get()) return true;
        String current = mc.options.languageCode;
        return Config.DISPLAY_LANGUAGE.get().stream()
                .anyMatch(lang -> !lang.equalsIgnoreCase(current));
    }

    /**
     * Inserts one secondary-name line per configured language that produces
     * a name different from the primary, directly below the item name.
     */
    public static void insertSecondaryName(List<Component> tooltip, ItemStack stack) {
        if (!shouldShowSecondaryLanguage()) return;
        insertNames(tooltip, getSecondaryNames(
                LanguageCache.getInstance().resolveDisplayNamesForAll(stack),
                stack.getHoverName().getString()
        ));
    }

    /**
     * Inserts one secondary-name line per configured language for the given
     * component (used for non-ItemStack sources such as fluid tooltips).
     */
    public static void insertSecondaryName(List<Component> tooltip, Component sourceName) {
        if (!shouldShowSecondaryLanguage()) return;
        insertNames(tooltip, getSecondaryNames(
                LanguageCache.getInstance().resolveComponentsForAll(sourceName),
                sourceName.getString()
        ));
    }

    public static List<Component> getSecondaryNameLines(Component sourceName) {
        if (!shouldShowSecondaryLanguage()) return List.of();
        List<String> names = getSecondaryNames(
                LanguageCache.getInstance().resolveComponentsForAll(sourceName),
                sourceName.getString()
        );
        List<Component> lines = new ArrayList<>(names.size());
        for (String secondary : names) {
            lines.add(createSecondaryLine(secondary));
        }
        return lines;
    }

    private static void insertNames(List<Component> tooltip, List<String> names) {
        int insertAt = tooltip.isEmpty() ? 0 : 1;
        // Insert at the same index in reverse order: inserting A then B at index 1
        // yields [name, B, A, ...], so iterating in reverse (B first, then A) gives
        // the correct top-to-bottom config order: [name, A, B, ...].
        for (int i = names.size() - 1; i >= 0; i--) {
            String secondary = names.get(i);
            removeLine(tooltip, secondary);
            tooltip.add(insertAt, createSecondaryLine(secondary));
        }
    }

    private static List<String> getSecondaryNames(List<String> names, String primaryText) {
        List<String> filtered = new ArrayList<>(names.size());
        for (String secondary : names) {
            if (!secondary.equals(primaryText)) {
                filtered.add(secondary);
            }
        }
        return filtered;
    }

    private static Component createSecondaryLine(String secondary) {
        return Component.literal(secondary).withStyle(LegacyFormatStyleUtil.tooltipSecondaryNameStyle());
    }

    private static void removeLine(List<Component> tooltip, String text) {
        for (int i = 0; i < tooltip.size(); i++) {
            String lineText = tooltip.get(i).getString().strip();
            if (text.equals(lineText)) {
                tooltip.remove(i);
                return;
            }
        }
    }
}
