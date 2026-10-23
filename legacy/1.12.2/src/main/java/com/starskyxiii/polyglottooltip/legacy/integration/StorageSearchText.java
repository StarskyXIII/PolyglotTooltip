package com.starskyxiii.polyglottooltip.legacy.integration;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import com.starskyxiii.polyglottooltip.legacy.Config;
import com.starskyxiii.polyglottooltip.legacy.integration.jei.SearchTokens;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryNames;

public final class StorageSearchText {
    public static final String SEPARATOR = "\n";

    private final Map<SecondaryNames, String[]> texts = Collections.synchronizedMap(new WeakHashMap<>());

    public String text(String primary, SecondaryNames names) {
        if (primary == null || names.isEmpty()) {
            return primary;
        }
        String[] cached = texts.get(names);
        if (cached != null && cached[0].equals(primary)) {
            return cached[1];
        }
        String text = compose(primary, names.names(), Config.settings().chineseScriptMatching());
        texts.put(names, new String[] {primary, text});
        return text;
    }

    public void clear() {
        texts.clear();
    }

    public static String compose(String primary, List<String> names, boolean chineseScriptMatching) {
        StringBuilder text = new StringBuilder(primary);
        for (String token : SearchTokens.of(primary, names, chineseScriptMatching, value -> value.toLowerCase(Locale.ROOT))) {
            text.append(SEPARATOR).append(token);
        }
        return text.toString();
    }
}
