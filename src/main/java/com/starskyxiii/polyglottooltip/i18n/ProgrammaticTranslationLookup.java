package com.starskyxiii.polyglottooltip.i18n;

import java.lang.reflect.Field;
import java.util.Map;

import net.minecraft.client.Minecraft;

final class ProgrammaticTranslationLookup {

    private static Map<String, String> currentLanguageTranslations;
    private static Map<String, String> fallbackTranslations;
    private static boolean initialized;

    private ProgrammaticTranslationLookup() {}

    static synchronized String getRawTranslation(String languageCode, String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }

        initialize();

        Map<String, String> translations = selectTranslations(languageCode);
        if (translations == null || translations.isEmpty()) {
            return null;
        }

        String translated = translations.get(key);
        if (translated == null || translated.trim().isEmpty()) {
            return null;
        }

        return translated.trim();
    }

    static synchronized void clear() {
        currentLanguageTranslations = null;
        fallbackTranslations = null;
        initialized = false;
    }

    private static Map<String, String> selectTranslations(String languageCode) {
        String normalizedLanguage = languageCode == null ? "" : languageCode.trim();
        if ("en_US".equalsIgnoreCase(normalizedLanguage)) {
            return fallbackTranslations != null ? fallbackTranslations : currentLanguageTranslations;
        }

        String currentLanguage = getCurrentLanguageCode();
        if (normalizedLanguage.equalsIgnoreCase(currentLanguage)) {
            return currentLanguageTranslations;
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static synchronized void initialize() {
        if (initialized) {
            return;
        }

        initialized = true;

        try {
            Field languageListField = findField(
                net.minecraft.util.StringTranslate.class,
                "languageList",
                "field_74816_c");
            Field instanceField = findField(
                net.minecraft.util.StringTranslate.class,
                "instance",
                "field_74817_a");
            Field fallbackTranslatorField = findField(
                net.minecraft.util.StatCollector.class,
                "fallbackTranslator",
                "field_150828_b");

            Object stringTranslateInstance = instanceField == null ? null : instanceField.get(null);
            Object fallbackTranslator = fallbackTranslatorField == null ? null : fallbackTranslatorField.get(null);

            if (languageListField != null && stringTranslateInstance != null) {
                Object currentTranslations = languageListField.get(stringTranslateInstance);
                if (currentTranslations instanceof Map) {
                    currentLanguageTranslations = (Map<String, String>) currentTranslations;
                }
            }

            if (languageListField != null && fallbackTranslator != null) {
                Object fallbackMap = languageListField.get(fallbackTranslator);
                if (fallbackMap instanceof Map) {
                    fallbackTranslations = (Map<String, String>) fallbackMap;
                }
            }
        } catch (Exception ignored) {
            currentLanguageTranslations = null;
            fallbackTranslations = null;
        }
    }

    private static Field findField(Class<?> owner, String... candidateNames) {
        if (owner == null || candidateNames == null) {
            return null;
        }

        for (String candidateName : candidateNames) {
            if (candidateName == null || candidateName.isEmpty()) {
                continue;
            }

            try {
                Field field = owner.getDeclaredField(candidateName);
                field.setAccessible(true);
                return field;
            } catch (Exception ignored) {
                // Try next candidate.
            }
        }

        return null;
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
