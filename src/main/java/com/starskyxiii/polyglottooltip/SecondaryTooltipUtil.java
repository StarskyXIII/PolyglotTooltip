package com.starskyxiii.polyglottooltip;
//~ minecraft_116_packages minecraft_legacy_components minecraft_118_factories

import com.starskyxiii.polyglottooltip.minecraft.ComponentFactory;
import com.starskyxiii.polyglottooltip.minecraft.ItemStackNameAdapter;
import com.starskyxiii.polyglottooltip.core.SecondaryLanguagePolicy;
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
        return SecondaryLanguagePolicy.shouldShow(
                Config.ALWAYS_SHOW.get(),
                mc.options.languageCode,
                Config.DISPLAY_LANGUAGE.get());
    }

    /**
     * Inserts one secondary-name line per configured language that produces
     * a name different from the primary, directly below the item name.
     */
    public static void insertSecondaryName(List<Component> tooltip, ItemStack stack) {
        if (!shouldShowSecondaryLanguage()) return;
        insertNames(tooltip, getSecondaryNames(stack));
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

    public static List<String> getSecondaryNames(ItemStack stack) {
        return getSecondaryNames(
                LanguageCache.getInstance().resolveDisplayNamesForAll(stack),
                LanguageCache.getInstance().resolveCurrentDisplayName(stack),
                ItemStackNameAdapter.resolveCustomNameComponent(stack).orElse(null)
        );
    }

    public static List<String> getSecondaryNames(Component sourceName) {
        return getSecondaryNames(
                LanguageCache.getInstance().resolveComponentsForAll(sourceName),
                sourceName.getString()
        );
    }

    public static List<String> getSecondaryNames(List<String> names, String primaryText) {
        return filterPrimaryName(names, primaryText);
    }

    static List<String> getSecondaryNames(List<String> names, String primaryText, Component customName) {
        List<String> filtered = filterPrimaryName(names, primaryText);
        return ItemStackNameAdapter.isLiteralName(customName) ? filterPrimaryName(filtered, customName.getString()) : filtered;
    }

    public static List<Component> getSecondaryNameLines(Component sourceName) {
        if (!shouldShowSecondaryLanguage()) return java.util.Collections.emptyList();
        List<String> names = getSecondaryNames(sourceName);
        List<Component> lines = new ArrayList<>(names.size());
        for (String secondary : names) {
            lines.add(createTooltipSecondaryLine(secondary));
        }
        return lines;
    }

    public static Component createTooltipSecondaryLine(String secondary) {
        return createSecondaryLine(secondary, LegacyFormatStyleUtil.tooltipSecondaryNameStyle());
    }

    public static Component createJadeSecondaryLine(String secondary) {
        return createSecondaryLine(secondary, LegacyFormatStyleUtil.jadeSecondaryNameStyle());
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
                tooltip.add(insertAt++, createTooltipSecondaryLine(secondary));
            }
        }
    }

    private static List<String> filterPrimaryName(List<String> names, String primaryText) {
        List<String> filtered = new ArrayList<>(names.size());
        for (String secondary : names) {
            if (!secondary.equals(primaryText)) {
                filtered.add(secondary);
            }
        }
        return filtered;
    }

    private static Component createSecondaryLine(String secondary, Style style) {
        return ComponentFactory.literal(secondary).withStyle(style);
    }

    private static void removeLines(List<Component> tooltip, List<String> texts) {
        if (texts.isEmpty()) return;
        Style secondaryStyle = LegacyFormatStyleUtil.tooltipSecondaryNameStyle();
        for (int i = tooltip.size() - 1; i >= 0; i--) {
            Component line = tooltip.get(i);
            if (texts.contains(line.getString())
                    && secondaryStyle.equals(line.getStyle())) {
                tooltip.remove(i);
            }
        }
    }
}
