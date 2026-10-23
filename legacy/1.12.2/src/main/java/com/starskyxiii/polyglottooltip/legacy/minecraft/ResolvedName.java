package com.starskyxiii.polyglottooltip.legacy.minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ResolvedName {
    private final String name;
    private final List<String> untranslated;

    private ResolvedName(String name, List<String> untranslated) {
        this.name = name;
        this.untranslated = untranslated;
    }

    public static ResolvedName of(String name) {
        return name == null ? null : new ResolvedName(name, Collections.<String>emptyList());
    }

    public static ResolvedName of(String name, List<String> untranslated) {
        if (name == null) {
            return null;
        }
        return untranslated == null || untranslated.isEmpty() ? of(name)
                : new ResolvedName(name, Collections.unmodifiableList(new ArrayList<>(untranslated)));
    }

    public String name() {
        return name;
    }

    public List<String> untranslated() {
        return untranslated;
    }

    public boolean partial() {
        return !untranslated.isEmpty();
    }
}
