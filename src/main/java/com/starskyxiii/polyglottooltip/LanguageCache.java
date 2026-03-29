package com.starskyxiii.polyglottooltip;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

public final class LanguageCache {

    private static final Pattern FORMAT_PLACEHOLDER_PATTERN =
        Pattern.compile("%(?:(\\d+)\\$)?[-#+ 0,(<]*\\d*(?:\\.\\d+)?[tT]?[a-zA-Z]");
    private static final String ESCAPED_PERCENT_TOKEN = "\u0000";

    private static final Map<String, net.minecraft.client.resources.Locale> LOCALES =
        new LinkedHashMap<String, net.minecraft.client.resources.Locale>();
    private static Field localePropertiesField;

    private LanguageCache() {}

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

    public static synchronized String resolveItemDisplayName(String languageCode, ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        net.minecraft.client.resources.Locale locale = getLocale(languageCode);
        if (locale == null) {
            return null;
        }

        Map<String, String> localeProperties = getLocaleProperties(locale);
        if (localeProperties == null || localeProperties.isEmpty()) {
            return null;
        }

        try {
            TranslationOverrideContext.push(localeProperties);
            String displayName = invokeDisplayNameMethod(stack);
            if (displayName == null) {
                return null;
            }

            displayName = EnumChatFormatting.getTextWithoutFormattingCodes(displayName);
            if (displayName == null) {
                return null;
            }

            displayName = displayName.trim();
            return displayName.isEmpty() ? null : displayName;
        } catch (Exception ignored) {
            return null;
        } finally {
            TranslationOverrideContext.pop();
        }
    }

    public static synchronized String resolveEnchantmentTranslatedName(String languageCode, Enchantment enchantment, int level) {
        if (enchantment == null) {
            return null;
        }

        net.minecraft.client.resources.Locale locale = getLocale(languageCode);
        if (locale == null) {
            return null;
        }

        Map<String, String> localeProperties = getLocaleProperties(locale);
        if (localeProperties == null || localeProperties.isEmpty()) {
            return null;
        }

        try {
            TranslationOverrideContext.push(localeProperties);
            String translatedName = enchantment.getTranslatedName(level);
            if (translatedName == null) {
                return null;
            }

            translatedName = EnumChatFormatting.getTextWithoutFormattingCodes(translatedName);
            if (translatedName == null) {
                return null;
            }

            translatedName = translatedName.trim();
            return translatedName.isEmpty() ? null : translatedName;
        } catch (Exception ignored) {
            return null;
        } finally {
            TranslationOverrideContext.pop();
        }
    }

    private static String invokeDisplayNameMethod(ItemStack stack) {
        String displayName = invokeDisplayNameMethod(stack, "func_77653_i");
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName;
        }

        return invokeDisplayNameMethod(stack, "getItemStackDisplayName");
    }

    private static String invokeDisplayNameMethod(ItemStack stack, String methodName) {
        if (stack == null || stack.getItem() == null || methodName == null || methodName.isEmpty()) {
            return null;
        }

        try {
            Method method = stack.getItem().getClass().getMethod(methodName, ItemStack.class);
            Object value = method.invoke(stack.getItem(), stack);
            return value instanceof String ? (String) value : null;
        } catch (Exception ignored) {
            return null;
        }
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
