package com.starskyxiii.polyglottooltip.legacy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class Settings {
    public static final int MAX_LANGUAGES = 32;
    private static final Pattern LANGUAGE_CODE = Pattern.compile("[A-Za-z0-9_]{2,32}");
    private static final String FORMATTING_CODES = "0123456789abcdefklmnor";
    public static final Settings DEFAULT =
            new Settings(Collections.singletonList("en_us"), false, true, "&7", "&7");

    private final List<String> displayLanguages;
    private final boolean alwaysShow;
    private final boolean chineseScriptMatching;
    private final String tooltipFormat;
    private final String hudFormat;

    public Settings(Iterable<String> displayLanguages,
                    boolean alwaysShow,
                    boolean chineseScriptMatching,
                    String tooltipFormat,
                    String hudFormat) {
        this.displayLanguages = normalizeLanguages(displayLanguages);
        this.alwaysShow = alwaysShow;
        this.chineseScriptMatching = chineseScriptMatching;
        this.tooltipFormat = tooltipFormat == null ? "" : tooltipFormat;
        this.hudFormat = hudFormat == null ? "" : hudFormat;
    }

    public static List<String> normalizeLanguages(Iterable<String> codes) {
        Set<String> normalized = new LinkedHashSet<>();
        if (codes != null) {
            for (String code : codes) {
                if (normalized.size() == MAX_LANGUAGES) {
                    break;
                }
                if (code == null) {
                    continue;
                }
                String trimmed = code.trim();
                if (LANGUAGE_CODE.matcher(trimmed).matches()) {
                    normalized.add(trimmed.toLowerCase(Locale.ROOT));
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(normalized));
    }

    public static String formattingCodes(String format) {
        if (format == null || format.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(format.length());
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            if (c == '&' && i + 1 < format.length()) {
                char code = Character.toLowerCase(format.charAt(i + 1));
                if (FORMATTING_CODES.indexOf(code) >= 0) {
                    result.append('§').append(code);
                    i++;
                    continue;
                }
            }
            result.append(c);
        }
        return result.toString();
    }

    public List<String> displayLanguages() {
        return displayLanguages;
    }

    public boolean alwaysShow() {
        return alwaysShow;
    }

    public boolean chineseScriptMatching() {
        return chineseScriptMatching;
    }

    public String tooltipFormat() {
        return tooltipFormat;
    }

    public String hudFormat() {
        return hudFormat;
    }

    public String tooltipPrefix() {
        return formattingCodes(tooltipFormat);
    }

    public String hudPrefix() {
        return formattingCodes(hudFormat);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Settings)) {
            return false;
        }
        Settings that = (Settings) other;
        return alwaysShow == that.alwaysShow
                && chineseScriptMatching == that.chineseScriptMatching
                && displayLanguages.equals(that.displayLanguages)
                && tooltipFormat.equals(that.tooltipFormat)
                && hudFormat.equals(that.hudFormat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayLanguages, alwaysShow, chineseScriptMatching, tooltipFormat, hudFormat);
    }
}
