package com.starskyxiii.polyglottooltip;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cpw.mods.fml.client.config.IConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Property;

import com.starskyxiii.polyglottooltip.integration.controlling.ControllingSearchUtil;

public final class Config {

    private static final String CATEGORY_TOOLTIP = "tooltip";
    private static final String CATEGORY_SEARCH = "search";
    private static final String LANG_CATEGORY_TOOLTIP = CATEGORY_TOOLTIP;
    private static final String LANG_CATEGORY_SEARCH = CATEGORY_SEARCH;
    private static final String LANG_DISPLAY_LANGUAGES = "displayLanguages";
    private static final String LANG_ALWAYS_SHOW = "alwaysShow";
    private static final String LANG_ENABLE_CHINESE_SCRIPT_MATCHING = "enableChineseScriptMatching";

    private static File configFile;
    private static Configuration configuration;

    public static List<String> displayLanguages = Arrays.asList("en_US");
    public static boolean alwaysShow = false;
    public static boolean enableChineseScriptMatching = true;

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

        Property chineseScriptMatchingProperty = activeConfiguration.get(
            CATEGORY_SEARCH,
            "enableChineseScriptMatching",
            enableChineseScriptMatching,
            "Treat Simplified and Traditional Chinese as interchangeable during search.");
        chineseScriptMatchingProperty.setLanguageKey(LANG_ENABLE_CHINESE_SCRIPT_MATCHING);
        enableChineseScriptMatching = chineseScriptMatchingProperty.getBoolean(enableChineseScriptMatching);
        searchPropertyOrder.add(chineseScriptMatchingProperty.getName());

        activeConfiguration.setCategoryPropertyOrder(CATEGORY_TOOLTIP, tooltipPropertyOrder);
        activeConfiguration.setCategoryPropertyOrder(CATEGORY_SEARCH, searchPropertyOrder);

        LanguageCache.clear();
        ChineseScriptSearchMatcher.clearCaches();
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
        return configElements;
    }
}
