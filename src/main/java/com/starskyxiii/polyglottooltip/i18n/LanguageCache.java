package com.starskyxiii.polyglottooltip.i18n;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;

public final class LanguageCache {

    private static final Pattern FORMAT_PLACEHOLDER_PATTERN =
        Pattern.compile("%(?:(\\d+)\\$)?[-#+ 0,(<]*\\d*(?:\\.\\d+)?[tT]?[a-zA-Z](?![a-zA-Z])");
    private static final String ESCAPED_PERCENT_TOKEN = "\u0000";

    private static final Map<String, net.minecraft.client.resources.Locale> LOCALES =
        new LinkedHashMap<String, net.minecraft.client.resources.Locale>();
    private static Field localePropertiesField;
    private static final Map<String, String> CURRENT_LANGUAGE_VALUE_KEY_CACHE =
        new LinkedHashMap<String, String>();
    private static final Map<String, Map<String, String>> CURRENT_LANGUAGE_VALUE_KEY_INDEX_CACHE =
        new LinkedHashMap<String, Map<String, String>>();

    private LanguageCache() {}

    public static synchronized void clear() {
        LOCALES.clear();
        GregTechSupplementalTranslations.clear();
        ProgrammaticTranslationLookup.clear();
        CURRENT_LANGUAGE_VALUE_KEY_CACHE.clear();
        CURRENT_LANGUAGE_VALUE_KEY_INDEX_CACHE.clear();
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
        if (!key.equals(translated) && !translated.startsWith("Format error:")) {
            return translated.trim();
        }

        String programmaticTranslation = ProgrammaticTranslationLookup.getRawTranslation(languageCode, key);
        if (programmaticTranslation == null || programmaticTranslation.trim().isEmpty()) {
            return translate(languageCode, key);
        }

        try {
            return String.format(programmaticTranslation, args).trim();
        } catch (Exception ignored) {
            return programmaticTranslation.trim();
        }
    }

    public static synchronized String findCurrentLanguageTranslationKey(String localizedValue, String requiredKeyPrefix) {
        return findCurrentLanguageTranslationKey(localizedValue, requiredKeyPrefix, true);
    }

    private static String getRawTranslation(String languageCode, String key) {
        net.minecraft.client.resources.Locale locale = getLocale(languageCode);
        if (locale != null) {
            Map<String, String> properties = getLocaleProperties(locale);
            if (properties != null) {
                String rawTranslation = properties.get(key);
                if (rawTranslation != null && !rawTranslation.trim().isEmpty()) {
                    return rawTranslation.trim();
                }
            } else {
                String translated = locale.formatMessage(key, new Object[0]);
                if (!key.equals(translated) && !translated.startsWith("Format error:")) {
                    return translated.trim();
                }
            }
        }

        return ProgrammaticTranslationLookup.getRawTranslation(languageCode, key);
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

    private static void supplementGregTechTranslations(
        net.minecraft.client.resources.Locale locale, String languageCode) {
        Map<String, String> source = GregTechSupplementalTranslations.getTranslations(languageCode);
        if (source == null || source.isEmpty()) {
            return;
        }

        Map<String, String> localeProperties = getLocaleProperties(locale);
        if (localeProperties == null) {
            return;
        }

        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (!localeProperties.containsKey(entry.getKey())) {
                localeProperties.put(entry.getKey(), entry.getValue());
            }
        }
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
        supplementGregTechTranslations(loadedLocale, languageCode);

        LOCALES.put(languageCode, loadedLocale);
        return loadedLocale;
    }

    static synchronized Map<String, String> snapshotTranslations(String languageCode) {
        net.minecraft.client.resources.Locale locale = getLocale(languageCode);
        Map<String, String> properties = locale == null ? null : getLocaleProperties(locale);
        if (properties == null || properties.isEmpty()) {
            return null;
        }

        return properties;
    }

    private static synchronized String findCurrentLanguageTranslationKey(String localizedValue, String requiredKeyPrefix,
        boolean unused) {
        if (localizedValue == null) {
            return null;
        }

        String normalizedValue = localizedValue.trim();
        if (normalizedValue.isEmpty()) {
            return null;
        }

        String currentLanguageCode = getCurrentLanguageCode();
        String normalizedPrefix = requiredKeyPrefix == null ? "" : requiredKeyPrefix;
        String cacheKey = currentLanguageCode + "\u0000" + normalizedPrefix + "\u0000" + normalizedValue;
        if (CURRENT_LANGUAGE_VALUE_KEY_CACHE.containsKey(cacheKey)) {
            return CURRENT_LANGUAGE_VALUE_KEY_CACHE.get(cacheKey);
        }

        net.minecraft.client.resources.Locale currentLocale = getLocale(currentLanguageCode);
        Map<String, String> currentProperties = currentLocale == null ? null : getLocaleProperties(currentLocale);
        if (currentProperties == null || currentProperties.isEmpty()) {
            CURRENT_LANGUAGE_VALUE_KEY_CACHE.put(cacheKey, null);
            return null;
        }

        Map<String, String> reverseLookup = getCurrentLanguageTranslationKeyIndex(
            currentLanguageCode,
            normalizedPrefix,
            currentProperties);
        String resolvedKey = reverseLookup.get(normalizedValue);
        CURRENT_LANGUAGE_VALUE_KEY_CACHE.put(cacheKey, resolvedKey);
        return resolvedKey;
    }

    private static synchronized Map<String, String> getCurrentLanguageTranslationKeyIndex(String currentLanguageCode,
        String requiredKeyPrefix, Map<String, String> currentProperties) {
        String normalizedPrefix = requiredKeyPrefix == null ? "" : requiredKeyPrefix;
        String indexCacheKey = currentLanguageCode + "\u0000" + normalizedPrefix;
        Map<String, String> cachedIndex = CURRENT_LANGUAGE_VALUE_KEY_INDEX_CACHE.get(indexCacheKey);
        if (cachedIndex != null) {
            return cachedIndex;
        }

        Map<String, String> reverseLookup = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : currentProperties.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || value == null) {
                continue;
            }
            if (!normalizedPrefix.isEmpty() && !key.startsWith(normalizedPrefix)) {
                continue;
            }

            String normalizedEntryValue = value.trim();
            if (normalizedEntryValue.isEmpty() || reverseLookup.containsKey(normalizedEntryValue)) {
                continue;
            }

            reverseLookup.put(normalizedEntryValue, key);
        }

        CURRENT_LANGUAGE_VALUE_KEY_INDEX_CACHE.put(indexCacheKey, reverseLookup);
        return reverseLookup;
    }

    private static String getCurrentLanguageCode() {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft != null && minecraft.gameSettings != null && minecraft.gameSettings.language != null) {
                String languageCode = minecraft.gameSettings.language.trim();
                if (!languageCode.isEmpty()) {
                    return languageCode;
                }
            }
        } catch (Exception ignored) {
        }

        return "en_US";
    }
}
