package com.starskyxiii.polyglottooltip;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.ItemStack;

public final class LanguageCache {

    private static final Map<String, net.minecraft.client.resources.Locale> LOCALES =
        new LinkedHashMap<String, net.minecraft.client.resources.Locale>();

    private LanguageCache() {}

    public static void preloadConfiguredLanguages() {
        for (String languageCode : Config.displayLanguages) {
            getLocale(languageCode);
        }
    }

    public static List<String> resolveDisplayNames(ItemStack stack) {
        LinkedHashSet<String> resolvedNames = new LinkedHashSet<String>();

        if (stack == null || stack.getItem() == null || stack.hasDisplayName()) {
            return new ArrayList<String>(resolvedNames);
        }

        String translationKey = getTranslationKey(stack);
        if (translationKey == null || translationKey.isEmpty()) {
            return new ArrayList<String>(resolvedNames);
        }

        for (String languageCode : Config.displayLanguages) {
            String localized = translate(translationKey, languageCode);
            if (localized != null && !localized.isEmpty()) {
                resolvedNames.add(localized);
            }
        }

        return new ArrayList<String>(resolvedNames);
    }

    public static synchronized void clear() {
        LOCALES.clear();
    }

    private static String getTranslationKey(ItemStack stack) {
        String unlocalizedName = stack.getItem().getUnlocalizedNameInefficiently(stack);
        if (unlocalizedName == null || unlocalizedName.isEmpty()) {
            return null;
        }
        return unlocalizedName + ".name";
    }

    private static String translate(String key, String languageCode) {
        if (languageCode == null || languageCode.trim().isEmpty()) {
            return null;
        }

        net.minecraft.client.resources.Locale locale = getLocale(languageCode.trim());
        if (locale == null) {
            return null;
        }

        String translated = locale.formatMessage(key, new Object[0]);
        if (key.equals(translated)) {
            return null;
        }

        return translated.trim();
    }

    private static synchronized net.minecraft.client.resources.Locale getLocale(String languageCode) {
        if (languageCode == null || languageCode.trim().isEmpty()) {
            return null;
        }

        languageCode = languageCode.trim();
        net.minecraft.client.resources.Locale locale = LOCALES.get(languageCode);
        if (locale != null) {
            return locale;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return null;
        }

        IResourceManager resourceManager = minecraft.getResourceManager();
        if (resourceManager == null) {
            return null;
        }

        net.minecraft.client.resources.Locale loadedLocale = new net.minecraft.client.resources.Locale();
        List<String> languagesToLoad = new ArrayList<String>();
        languagesToLoad.add("en_US");

        if (!"en_US".equalsIgnoreCase(languageCode)) {
            languagesToLoad.add(languageCode);
        }

        loadedLocale.loadLocaleDataFiles(resourceManager, languagesToLoad);

        LOCALES.put(languageCode, loadedLocale);
        return loadedLocale;
    }
}
