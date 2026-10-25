package com.starskyxiii.polyglottooltip.legacy.minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class SecondaryNames {
    private static final Pattern FORMATTING = Pattern.compile("(?i)§[0-9a-fk-or]");
    public static final SecondaryNames EMPTY = new SecondaryNames(Collections.<String>emptyList());

    public static final String PARTIAL_SUFFIX = "(partial)";

    private final List<String> names;
    private final Map<String, List<String>> untranslated;
    private final String source;

    private SecondaryNames(List<String> names) {
        this(names, Collections.<String, List<String>>emptyMap(), null);
    }

    private SecondaryNames(List<String> names, Map<String, List<String>> untranslated, String source) {
        this.names = names;
        this.untranslated = untranslated;
        this.source = source;
    }

    public static SecondaryNames of(String primary,
                                    List<String> languages,
                                    Map<String, String> namesByLanguage,
                                    boolean alwaysShow,
                                    String currentLanguage) {
        return of(primary, languages, namesByLanguage, null, alwaysShow, currentLanguage);
    }

    public static SecondaryNames of(String primary,
                                    List<String> languages,
                                    Map<String, String> namesByLanguage,
                                    Map<String, List<String>> untranslatedByLanguage,
                                    boolean alwaysShow,
                                    String currentLanguage) {
        if (namesByLanguage == null || namesByLanguage.isEmpty()) {
            return EMPTY;
        }
        String shown = primary == null ? null : plain(primary);
        Set<String> seen = new LinkedHashSet<>();
        Map<String, List<String>> untranslated = new LinkedHashMap<>();
        for (String language : languages) {
            if (!alwaysShow && language.equalsIgnoreCase(currentLanguage)) {
                continue;
            }
            String name = namesByLanguage.get(language);
            if (name == null) {
                continue;
            }
            String trimmed = name.trim();
            String plain = plain(trimmed);
            if (plain.isEmpty() || plain.equals(shown)) {
                continue;
            }
            seen.add(trimmed);
            List<String> parts = untranslatedByLanguage == null ? null : untranslatedByLanguage.get(language);
            if (parts != null && !parts.isEmpty()) {
                untranslated.put(language, parts);
            }
        }
        return seen.isEmpty() ? EMPTY : new SecondaryNames(Collections.unmodifiableList(new ArrayList<>(seen)),
                Collections.unmodifiableMap(untranslated), null);
    }

    public static String plain(String text) {
        return FORMATTING.matcher(text).replaceAll("").trim();
    }

    public List<String> names() {
        return names;
    }

    public boolean isEmpty() {
        return names.isEmpty();
    }

    public String source() {
        return source == null || !partial() ? source : source + PARTIAL_SUFFIX;
    }

    public boolean partial() {
        return !untranslated.isEmpty();
    }

    public Map<String, List<String>> untranslated() {
        return untranslated;
    }

    public SecondaryNames withSource(String source) {
        return isEmpty() ? EMPTY : new SecondaryNames(names, untranslated, source);
    }
}
