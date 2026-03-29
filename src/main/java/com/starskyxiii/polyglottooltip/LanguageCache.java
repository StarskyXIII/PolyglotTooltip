package com.starskyxiii.polyglottooltip;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.ItemStack;

public final class LanguageCache {

    private static final Pattern FORMAT_PLACEHOLDER_PATTERN =
        Pattern.compile("%(?:(\\d+)\\$)?[-#+ 0,(<]*\\d*(?:\\.\\d+)?[tT]?[a-zA-Z]");
    private static final String ESCAPED_PERCENT_TOKEN = "\u0000";

    private static final Map<String, net.minecraft.client.resources.Locale> LOCALES =
        new LinkedHashMap<String, net.minecraft.client.resources.Locale>();
    private static Field localePropertiesField;

    private LanguageCache() {}

    public static void preloadConfiguredLanguages() {
        for (String languageCode : Config.displayLanguages) {
            getLocale(languageCode);
        }
    }

    public static List<String> resolveDisplayNames(ItemStack stack) {
        LinkedHashSet<String> resolvedNames = new LinkedHashSet<String>();

        if (stack == null || stack.getItem() == null) {
            return new ArrayList<String>(resolvedNames);
        }

        String translationKey = getTranslationKey(stack);
        if (translationKey == null || translationKey.isEmpty()) {
            return new ArrayList<String>(resolvedNames);
        }

        for (String languageCode : Config.displayLanguages) {
            String localized = translate(languageCode, translationKey);
            if (localized != null && !localized.isEmpty()) {
                resolvedNames.add(localized);
            }
        }

        return new ArrayList<String>(resolvedNames);
    }

    public static synchronized void clear() {
        LOCALES.clear();
    }

    public static String translate(String languageCode, String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }

        String rawTranslation = getRawTranslation(languageCode, key);
        if (rawTranslation == null) {
            return null;
        }

        String translated = normalizeTranslation(rawTranslation);
        if (translated == null || translated.isEmpty()) {
            return null;
        }

        return translated;
    }

    public static String format(String languageCode, String key, Object... args) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }

        net.minecraft.client.resources.Locale locale = getLocale(languageCode);
        if (locale == null) {
            return null;
        }

        if (args == null || args.length == 0) {
            return translate(languageCode, key);
        }

        String translated = locale.formatMessage(key, args);
        if (key.equals(translated)) {
            return null;
        }

        if (translated.startsWith("Format error:")) {
            return translate(languageCode, key);
        }

        return translated.trim();
    }

    private static String getTranslationKey(ItemStack stack) {
        String unlocalizedName = stack.getItem().getUnlocalizedNameInefficiently(stack);
        if (unlocalizedName == null || unlocalizedName.isEmpty()) {
            return null;
        }
        return unlocalizedName + ".name";
    }

    private static String getRawTranslation(String languageCode, String key) {
        net.minecraft.client.resources.Locale locale = getLocale(languageCode);
        if (locale == null) {
            return null;
        }

        Map<String, String> properties = getLocaleProperties(locale);
        if (properties != null) {
            String rawTranslation = properties.get(key);
            if (rawTranslation != null && !rawTranslation.trim().isEmpty()) {
                return rawTranslation.trim();
            }
            return null;
        }

        String translated = locale.formatMessage(key, new Object[0]);
        if (key.equals(translated) || translated.startsWith("Format error:")) {
            return null;
        }

        return translated.trim();
    }

    private static String normalizeTranslation(String rawTranslation) {
        if (rawTranslation == null) {
            return null;
        }

        String normalized = rawTranslation.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        if (!FORMAT_PLACEHOLDER_PATTERN.matcher(normalized).find()) {
            return normalized;
        }

        normalized = normalized.replace("%%", ESCAPED_PERCENT_TOKEN);
        normalized = FORMAT_PLACEHOLDER_PATTERN.matcher(normalized).replaceAll("");
        normalized = normalized.replace(ESCAPED_PERCENT_TOKEN, "%");
        normalized = normalized.replaceAll("\\s{2,}", " ").trim();
        normalized = normalized.replaceAll("\\s+([,.;:!?])", "$1");
        normalized = normalized.replaceAll("\\s*[:-]\\s*$", "").trim();

        return normalized.isEmpty() ? null : normalized;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> getLocaleProperties(net.minecraft.client.resources.Locale locale) {
        Field propertiesField = getLocalePropertiesField(locale);
        if (propertiesField == null) {
            return null;
        }

        try {
            Object properties = propertiesField.get(locale);
            if (properties instanceof Map) {
                return (Map<String, String>) properties;
            }
        } catch (IllegalAccessException ignored) {
            // Ignore and fall back to Locale#formatMessage when reflection fails.
        }

        return null;
    }

    private static synchronized Field getLocalePropertiesField(net.minecraft.client.resources.Locale locale) {
        if (localePropertiesField != null) {
            return localePropertiesField;
        }

        for (Field field : locale.getClass().getDeclaredFields()) {
            if (Map.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                localePropertiesField = field;
                return localePropertiesField;
            }
        }

        return null;
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
