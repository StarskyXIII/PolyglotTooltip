package com.starskyxiii.polyglottooltip.legacy.integration.jei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import com.starskyxiii.polyglottooltip.core.ChineseScriptPolicy;

public final class SearchTokens {
    private static final Pattern FORMATTING = Pattern.compile("(?i)§[0-9a-fk-or]");

    private SearchTokens() {}

    public static List<String> of(String primary,
                                  List<String> secondaryNames,
                                  boolean chineseScriptMatching,
                                  UnaryOperator<String> lowercase) {
        Set<String> tokens = new LinkedHashSet<>();
        String plainPrimary = plain(primary);
        add(tokens, plainPrimary, chineseScriptMatching, lowercase);
        for (String name : secondaryNames) {
            add(tokens, plain(name), chineseScriptMatching, lowercase);
        }
        if (primary != null) {
            tokens.remove(lowercase.apply(primary));
        }
        tokens.remove(lowercase.apply(plainPrimary));
        return tokens.isEmpty() ? Collections.<String>emptyList() : new ArrayList<>(tokens);
    }

    private static void add(Set<String> tokens, String name, boolean scripts, UnaryOperator<String> lowercase) {
        for (String variant : ChineseScriptPolicy.scriptVariants(name, scripts)) {
            String token = lowercase.apply(variant).trim();
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
    }

    private static String plain(String text) {
        return text == null ? "" : FORMATTING.matcher(text).replaceAll("").trim();
    }
}
