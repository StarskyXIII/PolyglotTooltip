package com.starskyxiii.polyglottooltip.legacy;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

public final class Config {
    public static final String GENERAL = Configuration.CATEGORY_GENERAL;
    public static final String INTEGRATIONS = "integrations";
    private static final String LANG = "polyglottooltip.config.";

    private static Configuration configuration;
    private static volatile Settings settings = Settings.DEFAULT;
    private static final Map<String, Boolean> STARTED_WITH = new ConcurrentHashMap<>();

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

    public static synchronized void saveChanges() {
        if (configuration != null) {
            configuration.save();
            update();
        }
    }

    public static Configuration configuration() {
        return configuration;
    }

    public static Settings settings() {
        return settings;
    }

    public static synchronized boolean integrationEnabled(String id) {
        if (configuration == null) {
            return true;
        }
        Property property = configuration.get(INTEGRATIONS, id, true);
        describeIntegration(property);
        if (configuration.hasChanged()) {
            configuration.save();
        }
        STARTED_WITH.put(id, property.getBoolean());
        return property.getBoolean();
    }

    public static synchronized List<String> integrationsNeedingRestart() {
        List<String> changed = new ArrayList<>();
        if (configuration == null) {
            return changed;
        }
        for (Property property : configuration.getCategory(INTEGRATIONS).getOrderedValues()) {
            Boolean started = STARTED_WITH.get(property.getName());
            if (started != null && started != property.getBoolean()) {
                changed.add(property.getName());
            }
        }
        return changed;
    }

    private static void describeIntegration(Property property) {
        property.setLanguageKey(LANG + "integrations." + property.getName());
        property.setRequiresMcRestart(true);
    }

    private static void update() {
        Property languages = configuration.get(GENERAL, "displayLanguage", new String[] {"en_us"});
        languages.setLanguageKey(LANG + "displayLanguage");
        Property always = configuration.get(GENERAL, "alwaysShow", false);
        always.setLanguageKey(LANG + "alwaysShow");
        Property scripts = configuration.get(GENERAL, "enableChineseScriptMatching", true);
        scripts.setLanguageKey(LANG + "enableChineseScriptMatching");
        Property tooltip = configuration.get(GENERAL, "tooltipSecondaryNameFormat", "&7");
        tooltip.setLanguageKey(LANG + "tooltipSecondaryNameFormat");
        Property hud = configuration.get(GENERAL, "hudSecondaryNameFormat", "&7");
        hud.setLanguageKey(LANG + "hudSecondaryNameFormat");
        configuration.get(GENERAL, "nameProgressX", 8).setMinValue(0).setMaxValue(4096).setShowInGui(false);
        configuration.get(GENERAL, "nameProgressY", 8).setMinValue(0).setMaxValue(4096).setShowInGui(false);
        ConfigCategory integrations = configuration.getCategory(INTEGRATIONS);
        integrations.setLanguageKey(LANG + INTEGRATIONS);
        integrations.setRequiresMcRestart(true);
        for (Property property : integrations.getOrderedValues()) {
            describeIntegration(property);
        }
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
