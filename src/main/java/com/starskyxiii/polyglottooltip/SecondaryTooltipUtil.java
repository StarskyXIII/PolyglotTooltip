package com.starskyxiii.polyglottooltip;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

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
     * Inserts one gray secondary-name line per configured language that produces
     * a name different from the primary, directly below the item name.
     */
    public static void insertSecondaryName(List<Component> tooltip, ItemStack stack) {
        if (!shouldShowSecondaryLanguage()) return;
        insertNames(tooltip,
                LanguageCache.getInstance().resolveDisplayNamesForAll(stack),
                stack.getHoverName().getString());
    }

    /**
     * Inserts one gray secondary-name line per configured language for the given
     * component (used for non-ItemStack sources such as fluid tooltips).
     */
    public static void insertSecondaryName(List<Component> tooltip, Component sourceName) {
        if (!shouldShowSecondaryLanguage()) return;
        insertNames(tooltip,
                LanguageCache.getInstance().resolveComponentsForAll(sourceName),
                sourceName.getString());
    }

    private static void insertNames(List<Component> tooltip, List<String> names, String primaryText) {
        int insertAt = tooltip.isEmpty() ? 0 : 1;
        // Insert in reverse order so config order ends up top-to-bottom.
        for (int i = names.size() - 1; i >= 0; i--) {
            String secondary = names.get(i);
            if (secondary.equals(primaryText)) continue;
            removeLine(tooltip, secondary);
            tooltip.add(insertAt, Component.literal(getMarkedSecondaryText(secondary))
                    .withStyle(s -> s.withColor(ChatFormatting.GRAY)));
        }
    }

    public static String getMarkedSecondaryText(String secondary) {
        return "\u00A0" + secondary;
    }

    private static String normalizeSecondaryLineText(String text) {
        return text.replace("\u00A0", "").strip();
    }

    private static void removeLine(List<Component> tooltip, String text) {
        for (int i = 0; i < tooltip.size(); i++) {
            String lineText = normalizeSecondaryLineText(tooltip.get(i).getString());
            if (text.equals(lineText)) {
                tooltip.remove(i);
                return;
            }
        }
    }
}
