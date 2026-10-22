package com.starskyxiii.polyglottooltip.legacy;

import java.io.File;
import java.util.Arrays;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

public final class Config {
    public static final String GENERAL = Configuration.CATEGORY_GENERAL;
    public static final String INTEGRATIONS = "integrations";

    private static Configuration configuration;
    private static volatile Settings settings = Settings.DEFAULT;

    private Config() {}

    public static void initialize(File file) {
        configuration = new Configuration(file);
        configuration.load();
        update();
    }

    public static void reload() {
        if (configuration != null) {
            configuration.load();
            update();
        }
    }

    public static Settings settings() {
        return settings;
    }

    private static void update() {
        Property languages = configuration.get(GENERAL, "displayLanguage", new String[] {"en_us"});
        languages.setLanguageKey("polyglottooltip.config.displayLanguage");
        Property always = configuration.get(GENERAL, "alwaysShow", false);
        always.setLanguageKey("polyglottooltip.config.alwaysShow");
        Property scripts = configuration.get(GENERAL, "enableChineseScriptMatching", true);
        scripts.setLanguageKey("polyglottooltip.config.enableChineseScriptMatching");
        Property tooltip = configuration.get(GENERAL, "tooltipSecondaryNameFormat", "&7");
        tooltip.setLanguageKey("polyglottooltip.config.tooltipSecondaryNameFormat");
        Property hud = configuration.get(GENERAL, "hudSecondaryNameFormat", "&7");
        hud.setLanguageKey("polyglottooltip.config.hudSecondaryNameFormat");
        configuration.get(GENERAL, "nameProgressX", 8).setMinValue(0).setMaxValue(4096);
        configuration.get(GENERAL, "nameProgressY", 8).setMinValue(0).setMaxValue(4096);
        configuration.getCategory(INTEGRATIONS);
        settings = new Settings(
                Arrays.asList(languages.getStringList()),
                always.getBoolean(),
                scripts.getBoolean(),
                tooltip.getString(),
                hud.getString());
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
