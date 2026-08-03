package com.starskyxiii.polyglottooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

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
        insertNames(tooltip, getSecondaryNames(sourceName));
    }

    /**
     * Inserts secondary names below the first detail line when it matches the
     * supplied predicate, or directly below the primary name otherwise.
     *
     * <p>Known secondary-name lines are removed before the predicate is tested.
     * This lets an integration hook repair the final ordering after a broader
     * tooltip hook has already inserted the same names.
     */
    public static void insertSecondaryNameAfterFirstDetailIf(List<Component> tooltip,
                                                              Component sourceName,
                                                              Predicate<Component> firstDetailPredicate) {
        if (!shouldShowSecondaryLanguage()) return;

        List<String> names = getSecondaryNames(sourceName);
        removeLines(tooltip, names);

        int insertAt = tooltip.size() > 1 && firstDetailPredicate.test(tooltip.get(1)) ? 2 : 1;
        insertNamesAt(tooltip, names, Math.min(insertAt, tooltip.size()));
    }

    public static List<Component> getSecondaryNameLines(Component sourceName) {
        if (!shouldShowSecondaryLanguage()) return List.of();
        List<String> names = getSecondaryNames(sourceName);
        List<Component> lines = new ArrayList<>(names.size());
        for (String secondary : names) {
            lines.add(createSecondaryLine(secondary));
        }
        return lines;
    }

    private static void insertNames(List<Component> tooltip, List<String> names) {
        int insertAt = tooltip.isEmpty() ? 0 : 1;
        removeLines(tooltip, names);
        insertNamesAt(tooltip, names, Math.min(insertAt, tooltip.size()));
    }

    private static void insertNamesAt(List<Component> tooltip, List<String> names, int insertAt) {
        Set<String> inserted = new HashSet<>();
        for (String secondary : names) {
            if (inserted.add(secondary)) {
                tooltip.add(insertAt++, createSecondaryLine(secondary));
            }
        }
    }

    private static List<String> getSecondaryNames(Component sourceName) {
        return getSecondaryNames(
                LanguageCache.getInstance().resolveComponentsForAll(sourceName),
                sourceName.getString()
        );
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

    private static void removeLines(List<Component> tooltip, List<String> texts) {
        if (texts.isEmpty()) return;
        Style secondaryStyle = LegacyFormatStyleUtil.tooltipSecondaryNameStyle();
        for (int i = tooltip.size() - 1; i >= 0; i--) {
            Component line = tooltip.get(i);
            if (texts.contains(line.getString().strip())
                    && secondaryStyle.equals(line.getStyle())) {
                tooltip.remove(i);
            }
        }
    }
}
