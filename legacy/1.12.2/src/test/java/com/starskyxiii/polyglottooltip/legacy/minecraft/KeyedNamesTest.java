package com.starskyxiii.polyglottooltip.legacy.minecraft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class KeyedNamesTest {
    private static final Map<String, String> PRIMARY = new HashMap<>();

    static {
        PRIMARY.put("tile.appliedenergistics2.molecular_assembler.name", "分子装配室");
        PRIMARY.put("tc.aspect.aqua", "水");
    }

    private static Map<String, SecondaryDictionary> dictionaries() {
        Map<String, SecondaryDictionary> dictionaries = new LinkedHashMap<>();
        dictionaries.put("en_us", SecondaryDictionary.load("en_us", (code, sink) -> {
            sink.accept("tile.appliedenergistics2.molecular_assembler.name", "Molecular Assembler");
            sink.accept("tc.aspect.aqua", "Water");
        }));
        return dictionaries;
    }

    private static String translate(String key) {
        return PRIMARY.getOrDefault(key, key);
    }

    @Test
    public void machineLabelsUseTheFirstKeyThatRebuildsTheShownName() {
        String key = "tile.appliedenergistics2.molecular_assembler";
        assertEquals(Collections.singletonMap("en_us", "Molecular Assembler"),
                KeyedNames.resolve("分子装配室", Arrays.asList(key + ".name", key), KeyedNamesTest::translate, dictionaries()));
    }

    @Test
    public void renamedMachinesAndUnknownKeysGetNoNames() {
        String key = "tile.appliedenergistics2.molecular_assembler";
        assertTrue(KeyedNames.resolve("My Assembler", Arrays.asList(key + ".name", key), KeyedNamesTest::translate,
                dictionaries()).isEmpty());
        assertTrue(KeyedNames.resolve("Custom", Arrays.asList("Custom.name", "Custom"), KeyedNamesTest::translate,
                dictionaries()).isEmpty());
    }

    @Test
    public void aspectDescriptionsResolveThroughTheirThaumcraftKey() {
        assertEquals(Collections.singletonMap("en_us", "Water"), KeyedNames.resolve("水",
                Collections.singletonList("tc.aspect.aqua"), KeyedNamesTest::translate, dictionaries()));
        assertTrue(KeyedNames.resolve("Aqua", Collections.singletonList("tc.aspect.aqua"), KeyedNamesTest::translate,
                dictionaries()).isEmpty());
    }
}
