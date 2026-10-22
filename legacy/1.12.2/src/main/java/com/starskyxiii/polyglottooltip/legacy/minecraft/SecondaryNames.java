package com.starskyxiii.polyglottooltip.legacy.minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class SecondaryNames {
    private static final Pattern FORMATTING = Pattern.compile("(?i)§[0-9a-fk-or]");
    public static final SecondaryNames EMPTY = new SecondaryNames(Collections.<String>emptyList());

    private final List<String> names;

    private SecondaryNames(List<String> names) {
        this.names = names;
    }

    public static SecondaryNames of(String primary,
                                    List<String> languages,
                                    Map<String, String> namesByLanguage,
                                    boolean alwaysShow,
                                    String currentLanguage) {
        if (namesByLanguage == null || namesByLanguage.isEmpty()) {
            return EMPTY;
        }
        String shown = primary == null ? null : plain(primary);
        Set<String> seen = new LinkedHashSet<>();
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
        }
        return seen.isEmpty() ? EMPTY : new SecondaryNames(Collections.unmodifiableList(new ArrayList<>(seen)));
    }

    static String plain(String text) {
        return FORMATTING.matcher(text).replaceAll("").trim();
    }

    public List<String> names() {
        return names;
    }

    public boolean isEmpty() {
        return names.isEmpty();
    }
}
