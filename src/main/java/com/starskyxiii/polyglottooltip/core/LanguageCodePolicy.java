package com.starskyxiii.polyglottooltip.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class LanguageCodePolicy {
    private LanguageCodePolicy() {
    }

    public static List<String> fallbackChain(String languageCode) {
        if ("en_us".equalsIgnoreCase(languageCode)) {
            return Collections.singletonList("en_us");
        }
        return Collections.unmodifiableList(Arrays.asList("en_us", languageCode));
    }
}
