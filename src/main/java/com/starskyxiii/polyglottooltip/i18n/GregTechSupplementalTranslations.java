package com.starskyxiii.polyglottooltip.i18n;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class GregTechSupplementalTranslations {

    private static final Logger LOG = LogManager.getLogger("polyglottooltip");

    private static final Map<String, Map<String, String>> CACHED_TRANSLATIONS =
        new LinkedHashMap<String, Map<String, String>>();
    private static final List<String> LOADED_LANGUAGES = new ArrayList<String>();

    private GregTechSupplementalTranslations() {}

    static synchronized void clear() {
        CACHED_TRANSLATIONS.clear();
        LOADED_LANGUAGES.clear();
    }

    static synchronized Map<String, String> getTranslations(String languageCode) {
        String normalizedLanguageCode = normalizeLanguageCode(languageCode);
        if (normalizedLanguageCode == null) {
            return null;
        }

        if (LOADED_LANGUAGES.contains(normalizedLanguageCode)) {
            return CACHED_TRANSLATIONS.get(normalizedLanguageCode);
        }

        LOADED_LANGUAGES.add(normalizedLanguageCode);
        Map<String, String> translations = readLangFile(normalizedLanguageCode);
        if (translations != null && !translations.isEmpty()) {
            CACHED_TRANSLATIONS.put(normalizedLanguageCode, translations);
        }
        return translations;
    }

    private static Map<String, String> readLangFile(String languageCode) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.mcDataDir == null) {
            return null;
        }

        File gtLangFile = resolveLangFile(minecraft.mcDataDir, languageCode);
        if (!gtLangFile.isFile()) {
            return null;
        }

        Map<String, String> result = new LinkedHashMap<String, String>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(gtLangFile), "UTF-8"));
            String line;
            boolean inLanguageFile = false;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                if (!inLanguageFile) {
                    if (isLanguageSectionStart(trimmed)) {
                        inLanguageFile = true;
                    }
                    continue;
                }

                if ("}".equals(trimmed)) {
                    break;
                }

                ParsedEntry entry = parseEntry(trimmed);
                if (entry != null) {
                    result.put(entry.key, entry.value);
                }
            }
        } catch (Exception e) {
            LOG.warn("[PolyglotTooltip] Failed to read supplemental GregTech lang for {}", languageCode, e);
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }

        if (!result.isEmpty()) {
            LOG.info(
                "[PolyglotTooltip] Read {} supplemental GregTech entries for {} from {}",
                result.size(),
                languageCode,
                gtLangFile.getAbsolutePath());
        }
        return result.isEmpty() ? null : result;
    }

    private static File resolveLangFile(File minecraftDir, String languageCode) {
        if (minecraftDir == null || languageCode == null || languageCode.isEmpty()) {
            return new File("");
        }

        if ("en_US".equalsIgnoreCase(languageCode)) {
            File rootDefaultFile = new File(minecraftDir, "GregTech.lang");
            if (rootDefaultFile.isFile()) {
                return rootDefaultFile;
            }

            return new File(new File(minecraftDir, "config"), "GregTech.lang");
        }

        File rootFile = new File(minecraftDir, "GregTech_" + languageCode + ".lang");
        if (rootFile.isFile()) {
            return rootFile;
        }

        return new File(new File(minecraftDir, "config"), "GregTech_" + languageCode + ".lang");
    }

    private static String normalizeLanguageCode(String languageCode) {
        if (languageCode == null) {
            return null;
        }

        String normalized = languageCode.trim().replace('-', '_');
        if (normalized.isEmpty()) {
            return null;
        }

        int separatorIndex = normalized.indexOf('_');
        if (separatorIndex <= 0 || separatorIndex >= normalized.length() - 1) {
            return normalized;
        }

        String language = normalized.substring(0, separatorIndex).toLowerCase(Locale.ROOT);
        String region = normalized.substring(separatorIndex + 1).toUpperCase(Locale.ROOT);
        return language + "_" + region;
    }

    private static boolean isLanguageSectionStart(String line) {
        if (line == null) {
            return false;
        }

        String normalized = line.trim().toLowerCase(Locale.ROOT);
        return "languagefile {".equals(normalized)
            || "\"languagefile\" {".equals(normalized)
            || "languagefile{".equals(normalized)
            || "\"languagefile\"{".equals(normalized);
    }

    private static ParsedEntry parseEntry(String line) {
        if (line == null || !line.startsWith("S:")) {
            return null;
        }

        String entry = line.substring(2).trim();
        if (entry.isEmpty()) {
            return null;
        }

        int separatorIndex;
        String key;
        if (entry.charAt(0) == '"') {
            int closingQuoteIndex = findClosingQuote(entry, 1);
            if (closingQuoteIndex <= 0) {
                return null;
            }

            key = unescapeQuotedConfigString(entry.substring(1, closingQuoteIndex));
            separatorIndex = skipWhitespace(entry, closingQuoteIndex + 1);
            if (separatorIndex >= entry.length() || entry.charAt(separatorIndex) != '=') {
                return null;
            }
        } else {
            separatorIndex = entry.indexOf('=');
            if (separatorIndex <= 0) {
                return null;
            }
            key = entry.substring(0, separatorIndex).trim();
        }

        String value = entry.substring(separatorIndex + 1).trim();
        if (key.isEmpty() || value.isEmpty()) {
            return null;
        }

        return new ParsedEntry(key, value);
    }

    private static int findClosingQuote(String text, int startIndex) {
        boolean escaped = false;
        for (int i = startIndex; i < text.length(); i++) {
            char current = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }

            if (current == '\\') {
                escaped = true;
                continue;
            }

            if (current == '"') {
                return i;
            }
        }

        return -1;
    }

    private static int skipWhitespace(String text, int index) {
        int result = index;
        while (result < text.length() && Character.isWhitespace(text.charAt(result))) {
            result++;
        }
        return result;
    }

    private static String unescapeQuotedConfigString(String value) {
        if (value.indexOf('\\') < 0) {
            return value;
        }

        StringBuilder builder = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaped) {
                builder.append(current);
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else {
                builder.append(current);
            }
        }

        if (escaped) {
            builder.append('\\');
        }
        return builder.toString();
    }

    private static final class ParsedEntry {

        private final String key;
        private final String value;

        private ParsedEntry(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
