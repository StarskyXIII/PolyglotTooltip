package com.starskyxiii.polyglottooltip.core;

public final class SecondaryLanguagePolicy {
    private SecondaryLanguagePolicy() {
    }

    public static boolean shouldShow(boolean alwaysShow,
                                     String currentLanguage,
                                     Iterable<? extends String> displayLanguages) {
        if (alwaysShow) {
            return true;
        }
        for (String language : displayLanguages) {
            if (!language.equalsIgnoreCase(currentLanguage)) {
                return true;
            }
        }
        return false;
    }
}
