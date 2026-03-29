package com.starskyxiii.polyglottooltip.ae2;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import com.starskyxiii.polyglottooltip.config.Config;
import com.starskyxiii.polyglottooltip.i18n.LanguageCache;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

public final class Ae2InterfaceSearchHelper {

    private static final String INTERFACE_NAME_SUFFIX = ".name";

    private static volatile Field clientDcUnlocalizedNameField;
    private static volatile boolean failedToResolveClientDcUnlocalizedNameField;
    private static volatile Method clientDcGetNameMethod;
    private static volatile boolean failedToResolveClientDcGetNameMethod;

    private Ae2InterfaceSearchHelper() {}

    public static String getSearchableInterfaceName(Object entry) {
        if (entry == null) {
            return "";
        }

        LinkedHashSet<String> searchableNames = new LinkedHashSet<String>();
        addName(searchableNames, getCurrentDisplayName(entry));

        String unlocalizedName = getUnlocalizedName(entry);
        if (unlocalizedName != null && !unlocalizedName.isEmpty()) {
            addTranslatedKey(searchableNames, unlocalizedName + INTERFACE_NAME_SUFFIX);
            addTranslatedKey(searchableNames, unlocalizedName);
        }

        if (searchableNames.isEmpty()) {
            String fallback = getCurrentDisplayName(entry);
            return fallback == null ? "" : fallback;
        }

        return String.join("\n", searchableNames);
    }

    private static void addTranslatedKey(Set<String> target, String translationKey) {
        addTranslatedText(target, StatCollector.translateToLocal(translationKey), translationKey);
        addTranslatedText(target, LanguageCache.translate("en_US", translationKey), translationKey);

        for (String languageCode : Config.displayLanguages) {
            addTranslatedText(target, LanguageCache.translate(languageCode, translationKey), translationKey);
        }
    }

    private static void addTranslatedText(Set<String> target, String translatedText, String translationKey) {
        if (translatedText == null || translatedText.equals(translationKey)) {
            return;
        }

        addName(target, translatedText);
    }

    private static void addName(Set<String> target, String text) {
        if (text == null) {
            return;
        }

        String normalized = EnumChatFormatting.getTextWithoutFormattingCodes(text);
        if (normalized == null) {
            return;
        }

        normalized = normalized.trim();
        if (normalized.isEmpty()) {
            return;
        }

        for (String variant : ChineseScriptSearchMatcher.getSearchVariants(normalized)) {
            String lowered = variant == null ? "" : variant.trim().toLowerCase(Locale.ROOT);
            if (!lowered.isEmpty()) {
                target.add(lowered);
            }
        }
    }

    private static String getCurrentDisplayName(Object entry) {
        Method method = clientDcGetNameMethod;
        if (method == null && !failedToResolveClientDcGetNameMethod) {
            method = resolveGetNameMethod(entry);
        }

        if (method == null) {
            return null;
        }

        try {
            Object result = method.invoke(entry);
            return result instanceof String ? (String) result : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getUnlocalizedName(Object entry) {
        Field field = clientDcUnlocalizedNameField;
        if (field == null && !failedToResolveClientDcUnlocalizedNameField) {
            field = resolveUnlocalizedNameField(entry);
        }

        if (field == null) {
            return null;
        }

        try {
            Object result = field.get(entry);
            return result instanceof String ? (String) result : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Method resolveGetNameMethod(Object entry) {
        synchronized (Ae2InterfaceSearchHelper.class) {
            if (clientDcGetNameMethod != null || failedToResolveClientDcGetNameMethod) {
                return clientDcGetNameMethod;
            }

            try {
                Method method = entry.getClass().getMethod("getName");
                method.setAccessible(true);
                clientDcGetNameMethod = method;
                return method;
            } catch (Exception ignored) {
                failedToResolveClientDcGetNameMethod = true;
                return null;
            }
        }
    }

    private static Field resolveUnlocalizedNameField(Object entry) {
        synchronized (Ae2InterfaceSearchHelper.class) {
            if (clientDcUnlocalizedNameField != null || failedToResolveClientDcUnlocalizedNameField) {
                return clientDcUnlocalizedNameField;
            }

            try {
                Field field = entry.getClass().getDeclaredField("unlocalizedName");
                field.setAccessible(true);
                clientDcUnlocalizedNameField = field;
                return field;
            } catch (Exception ignored) {
                failedToResolveClientDcUnlocalizedNameField = true;
                return null;
            }
        }
    }
}
