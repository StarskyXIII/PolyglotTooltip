package com.starskyxiii.polyglottooltip.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cpw.mods.fml.client.config.IConfigElement;
import com.starskyxiii.polyglottooltip.integration.controlling.ControllingSearchUtil;
import com.starskyxiii.polyglottooltip.i18n.LanguageCache;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import com.starskyxiii.polyglottooltip.search.SearchTextCollector;
import com.starskyxiii.polyglottooltip.tooltip.SecondaryTooltipUtil;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

public final class Config {

    private static final String CATEGORY_TOOLTIP = "tooltip";
    private static final String CATEGORY_SEARCH = "search";
    private static final String CATEGORY_PREBUILD = "prebuild";
    private static final String LANG_CATEGORY_TOOLTIP = CATEGORY_TOOLTIP;
    private static final String LANG_CATEGORY_SEARCH = CATEGORY_SEARCH;
    private static final String LANG_DISPLAY_LANGUAGES = "displayLanguages";
    private static final String LANG_ALWAYS_SHOW = "alwaysShow";
    private static final String LANG_SECONDARY_NAME_COLOR = "secondaryNameColor";
    private static final String LANG_WAILA_SECONDARY_NAME_COLOR = "wailaSecondaryNameColor";
    private static final String LANG_ENABLE_CHINESE_SCRIPT_MATCHING = "enableChineseScriptMatching";

    private static File configFile;
    private static Configuration configuration;

    public static List<String> displayLanguages = Arrays.asList("en_US");
    public static boolean alwaysShow = false;
    public static String secondaryNameColor = "";
    public static String wailaSecondaryNameColor = "&7";
    public static boolean enableChineseScriptMatching = true;
    public static boolean useFastLanguageSwitch = true;
    public static boolean autoRebuildFullNameCache = true;

    private Config() {}

    public static synchronized void synchronizeConfiguration(File suggestedConfigFile) {
        if (suggestedConfigFile != null) {
            configFile = suggestedConfigFile;
        }

        synchronizeConfiguration();
    }

    public static synchronized void synchronizeConfiguration() {
        Configuration activeConfiguration = getConfiguration();
        if (activeConfiguration == null) {
            return;
        }

        List<String> tooltipPropertyOrder = new ArrayList<String>();
        List<String> searchPropertyOrder = new ArrayList<String>();

        activeConfiguration.setCategoryComment(CATEGORY_TOOLTIP, "Tooltip display settings.");
        activeConfiguration.setCategoryLanguageKey(CATEGORY_TOOLTIP, LANG_CATEGORY_TOOLTIP);
        activeConfiguration.setCategoryComment(CATEGORY_SEARCH, "Search integration settings.");
        activeConfiguration.setCategoryLanguageKey(CATEGORY_SEARCH, LANG_CATEGORY_SEARCH);
        activeConfiguration.setCategoryComment(CATEGORY_PREBUILD, "Settings for the /polyglotbuild command.");

        Property displayLanguagesProperty = activeConfiguration.get(
            CATEGORY_TOOLTIP,
            "displayLanguages",
            displayLanguages.toArray(new String[displayLanguages.size()]),
            "Extra language codes shown in tooltips, in display order.");
        displayLanguagesProperty.setLanguageKey(LANG_DISPLAY_LANGUAGES);
        displayLanguages = Arrays.asList(displayLanguagesProperty.getStringList());
        tooltipPropertyOrder.add(displayLanguagesProperty.getName());

        Property alwaysShowProperty = activeConfiguration.get(
            CATEGORY_TOOLTIP,
            "alwaysShow",
            alwaysShow,
            "Show secondary tooltip lines even when the current language already matches.");
        alwaysShowProperty.setLanguageKey(LANG_ALWAYS_SHOW);
        alwaysShow = alwaysShowProperty.getBoolean(alwaysShow);
        tooltipPropertyOrder.add(alwaysShowProperty.getName());

        Property secondaryNameColorProperty = activeConfiguration.get(
            CATEGORY_TOOLTIP,
            "secondaryNameColor",
            secondaryNameColor,
            "Formatting used for inserted secondary tooltip name lines. Leave empty to inherit the primary name style. Supports color/style names like gray, gold, bold, italic, and codes like 7, l, &7, &l, \u00A77, or \u00A7l. Combine values with spaces or commas, like 'gold italic'.");
        secondaryNameColorProperty.setLanguageKey(LANG_SECONDARY_NAME_COLOR);
        secondaryNameColor = secondaryNameColorProperty.getString();
        tooltipPropertyOrder.add(secondaryNameColorProperty.getName());

        Property wailaSecondaryNameColorProperty = activeConfiguration.get(
            CATEGORY_TOOLTIP,
            "wailaSecondaryNameColor",
            wailaSecondaryNameColor,
            "Formatting used for inserted secondary name lines in Waila. Leave empty to reuse secondaryNameColor. Supports color/style names like gray, gold, bold, italic, and codes like 7, l, &7, &l, \u00A77, or \u00A7l. Combine values with spaces or commas, like 'gold italic'.");
        wailaSecondaryNameColorProperty.setLanguageKey(LANG_WAILA_SECONDARY_NAME_COLOR);
        wailaSecondaryNameColor = wailaSecondaryNameColorProperty.getString();
        tooltipPropertyOrder.add(wailaSecondaryNameColorProperty.getName());

        Property chineseScriptMatchingProperty = activeConfiguration.get(
            CATEGORY_SEARCH,
            "enableChineseScriptMatching",
            enableChineseScriptMatching,
            "Treat Simplified and Traditional Chinese as interchangeable during search.");
        chineseScriptMatchingProperty.setLanguageKey(LANG_ENABLE_CHINESE_SCRIPT_MATCHING);
        enableChineseScriptMatching = chineseScriptMatchingProperty.getBoolean(enableChineseScriptMatching);
        searchPropertyOrder.add(chineseScriptMatchingProperty.getName());

        List<String> prebuildPropertyOrder = new ArrayList<String>();

        Property useFastLanguageSwitchProperty = activeConfiguration.get(
            CATEGORY_PREBUILD,
            "useFastLanguageSwitch",
            useFastLanguageSwitch,
            "Fast path: only reloads language data during /polyglotbuild, skips textures/sounds/models. "
                + "Set false to force full mc.refreshResources() for comparison.");
        useFastLanguageSwitch = useFastLanguageSwitchProperty.getBoolean(useFastLanguageSwitch);
        prebuildPropertyOrder.add(useFastLanguageSwitchProperty.getName());

        Property autoRebuildFullNameCacheProperty = activeConfiguration.get(
            CATEGORY_PREBUILD,
            "autoRebuildFullNameCache",
            autoRebuildFullNameCache,
            "Automatically build the full name cache on first launch (when no cache file exists). "
                + "Also supplements the cache when displayLanguages adds a language that is not present yet. "
                + "Work is budgeted across client ticks to reduce visible stutter. "
                + "Set false to disable auto-build and rely solely on /polyglotbuild.");
        autoRebuildFullNameCache = autoRebuildFullNameCacheProperty.getBoolean(autoRebuildFullNameCache);
        prebuildPropertyOrder.add(autoRebuildFullNameCacheProperty.getName());

        activeConfiguration.setCategoryPropertyOrder(CATEGORY_TOOLTIP, tooltipPropertyOrder);
        activeConfiguration.setCategoryPropertyOrder(CATEGORY_SEARCH, searchPropertyOrder);
        activeConfiguration.setCategoryPropertyOrder(CATEGORY_PREBUILD, prebuildPropertyOrder);

        LanguageCache.clear();
        ChineseScriptSearchMatcher.clearCaches();
        SearchTextCollector.clearCache();
        SecondaryTooltipUtil.clearInsertedLineCache();
        ControllingSearchUtil.clearCaches();

        if (activeConfiguration.hasChanged()) {
            activeConfiguration.save();
        }
    }

    public static synchronized Configuration getConfiguration() {
        if (configuration == null && configFile != null) {
            configuration = new Configuration(configFile);
        }

        return configuration;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static synchronized List<IConfigElement> getConfigElements() {
        synchronizeConfiguration();

        List<IConfigElement> configElements = new ArrayList<IConfigElement>();
        Configuration activeConfiguration = getConfiguration();
        if (activeConfiguration == null) {
            return configElements;
        }

        configElements.add(new ConfigElement(activeConfiguration.getCategory(CATEGORY_TOOLTIP)));
        configElements.add(new ConfigElement(activeConfiguration.getCategory(CATEGORY_SEARCH)));
        configElements.add(new ConfigElement(activeConfiguration.getCategory(CATEGORY_PREBUILD)));
        return configElements;
    }
}
