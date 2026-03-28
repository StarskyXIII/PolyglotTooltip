package com.starskyxiii.polyglottooltip;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import net.minecraftforge.common.config.Configuration;

public final class Config {

    private static final String CATEGORY_TOOLTIP = "tooltip";
    private static final String CATEGORY_SEARCH = "search";

    public static List<String> displayLanguages = Arrays.asList("en_US");
    public static boolean alwaysShow = false;
    public static boolean enableChineseScriptMatching = false;

    private Config() {}

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        String[] configuredLanguages = configuration.getStringList(
            "displayLanguages",
            CATEGORY_TOOLTIP,
            displayLanguages.toArray(new String[displayLanguages.size()]),
            "Extra language codes shown in tooltips, in display order.");
        displayLanguages = Arrays.asList(configuredLanguages);

        alwaysShow = configuration.getBoolean(
            "alwaysShow",
            CATEGORY_TOOLTIP,
            alwaysShow,
            "Show secondary tooltip lines even when the current language already matches.");

        enableChineseScriptMatching = configuration.getBoolean(
            "enableChineseScriptMatching",
            CATEGORY_SEARCH,
            enableChineseScriptMatching,
            "Treat Simplified and Traditional Chinese as interchangeable during search.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
