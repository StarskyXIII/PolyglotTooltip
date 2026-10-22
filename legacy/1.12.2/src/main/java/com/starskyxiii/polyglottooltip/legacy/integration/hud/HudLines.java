package com.starskyxiii.polyglottooltip.legacy.integration.hud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class HudLines {
    private static final Pattern FORMATTING = Pattern.compile("(?i)§[0-9a-fk-or]");

    private HudLines() {}

    public static List<String> compose(List<String> names, String prefix, Collection<String> shown) {
        if (names == null || names.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> seen = new HashSet<>();
        if (shown != null) {
            for (String line : shown) {
                if (line != null) {
                    seen.add(plain(line));
                }
            }
        }
        List<String> lines = new ArrayList<>();
        for (String name : names) {
            if (name == null) {
                continue;
            }
            String plain = plain(name);
            if (!plain.isEmpty() && seen.add(plain)) {
                lines.add((prefix == null ? "" : prefix) + name);
            }
        }
        return lines;
    }

    static String plain(String text) {
        return FORMATTING.matcher(text).replaceAll("").trim();
    }
}
