package com.starskyxiii.polyglottooltip.report;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Language-aware quality classifier for collected item display names.
 *
 * Rules are language-sensitive:
 * - CJK languages (zh_CN, zh_TW, ja_JP, ko_KR): CJK characters are NORMAL.
 *   Only raw keys and format-only entries are flagged.
 * - Non-CJK languages: CJK characters are SUSPICIOUS.
 *   Distinguishes MIXED_LANGUAGE (has Latin too) from CONTAINS_CJK (CJK-only content).
 */
public final class NameQualityClassifier {

    /** Languages where CJK characters in display names are expected and normal. */
    static final Set<String> CJK_LANGUAGES =
        new HashSet<String>(Arrays.asList("zh_CN", "zh_TW", "ja_JP", "ko_KR"));

    private NameQualityClassifier() {}

    public static boolean isCjkLanguage(String lang) {
        return lang != null && CJK_LANGUAGES.contains(lang);
    }

    /**
     * Classifies a display name in the context of a given language code.
     *
     * @param name display name (formatting codes may still be present)
     * @param lang language code, e.g. "en_US", "zh_CN"
     * @return quality category
     */
    public static NameQuality classify(String name, String lang) {
        if (name == null || name.isEmpty()) return NameQuality.EMPTY;
        if (isRawKey(name)) return NameQuality.RAW_KEY;
        if (isFormatOnly(name)) return NameQuality.FORMAT_ONLY;
        if (!isCjkLanguage(lang) && containsCjk(name)) {
            return containsMeaningfulLatin(name) ? NameQuality.MIXED_LANGUAGE : NameQuality.CONTAINS_CJK;
        }
        return NameQuality.GOOD;
    }

    // -------------------------------------------------------------------------
    // Character analysis (package-visible for BuildReportWriter)
    // -------------------------------------------------------------------------

    static boolean isRawKey(String name) {
        return name.startsWith("item.") || name.startsWith("tile.")
            || name.startsWith("entity.")
            || name.startsWith("block.");
    }

    /**
     * Returns true if the string contains no meaningful visible content after
     * stripping Minecraft §-formatting codes and whitespace.
     *
     * Single-character CJK names (石, 水, 弓 …) are NOT FORMAT_ONLY.
     * Single Latin letters or digits ARE treated as FORMAT_ONLY.
     */
    static boolean isFormatOnly(String name) {
        String stripped = stripFormattingCodes(name).trim();
        if (stripped.isEmpty()) return true;

        int cjkCount = 0;
        int latinOrDigitCount = 0;
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (isCjkChar(c)) {
                cjkCount++;
            } else if (Character.isLetter(c) || Character.isDigit(c)) {
                latinOrDigitCount++;
            }
        }

        if (cjkCount >= 1) return false;
        return latinOrDigitCount < 2;
    }

    private static String stripFormattingCodes(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00A7' && i + 1 < s.length()) {
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isCjkChar(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)
            || (c >= 0x3040 && c <= 0x309F)
            || (c >= 0x30A0 && c <= 0x30FF)
            || (c >= 0xAC00 && c <= 0xD7AF)
            || (c >= 0x3400 && c <= 0x4DBF)
            || (c >= 0xF900 && c <= 0xFAFF);
    }

    static boolean containsCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (isCjkChar(s.charAt(i))) return true;
        }
        return false;
    }

    /**
     * Returns true if the string contains at least 2 Latin letters (a-z / A-Z).
     * A single letter does not count as "meaningful Latin".
     */
    static boolean containsMeaningfulLatin(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                if (++count >= 2) return true;
            }
        }
        return false;
    }
}
