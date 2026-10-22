package com.starskyxiii.polyglottooltip.legacy.minecraft;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class SecondaryLanguagesTest {
    private static final SecondaryDictionary.LangSource SOURCE = (code, sink) -> {
        if ("en_us".equals(code)) {
            sink.accept("item.stone.name", "Stone");
        }
    };

    @Test
    public void rebuildIncrementsGeneration() {
        int before = SecondaryLanguages.generation();
        SecondaryLanguages.rebuild(SOURCE);
        assertEquals(before + 1, SecondaryLanguages.generation());
        SecondaryLanguages.rebuild(SOURCE);
        assertEquals(before + 2, SecondaryLanguages.generation());
    }

    @Test
    public void callbacksRunInOrderAfterDictionariesAndGenerationAreUpdated() {
        List<String> calls = new ArrayList<>();
        int[] seen = new int[1];
        String[] stone = new String[1];
        Runnable first = () -> {
            calls.add("first");
            seen[0] = SecondaryLanguages.generation();
            stone[0] = SecondaryLanguages.dictionaries().get("en_us").get("item.stone.name");
        };
        Runnable failing = () -> {
            calls.add("failing");
            throw new IllegalStateException("expected");
        };
        Runnable last = () -> calls.add("last");
        SecondaryLanguages.addInvalidationListener(first);
        SecondaryLanguages.addInvalidationListener(failing);
        SecondaryLanguages.addInvalidationListener(last);
        try {
            int before = SecondaryLanguages.generation();
            SecondaryLanguages.rebuild(SOURCE);
            assertEquals(Arrays.asList("first", "failing", "last"), calls);
            assertEquals(before + 1, seen[0]);
            assertEquals("Stone", stone[0]);
        } finally {
            SecondaryLanguages.removeInvalidationListener(first);
            SecondaryLanguages.removeInvalidationListener(failing);
            SecondaryLanguages.removeInvalidationListener(last);
        }
    }
}
