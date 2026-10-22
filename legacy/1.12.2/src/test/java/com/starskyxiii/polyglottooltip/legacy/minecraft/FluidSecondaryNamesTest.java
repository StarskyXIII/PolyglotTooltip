package com.starskyxiii.polyglottooltip.legacy.minecraft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class FluidSecondaryNamesTest {
    private static SecondaryDictionary dictionary(String language, String key, String value) {
        return SecondaryDictionary.load(language, (code, sink) -> {
            if (language.equals(code)) {
                sink.accept(key, value);
            }
        });
    }

    private static Map<String, SecondaryDictionary> dictionaries() {
        Map<String, SecondaryDictionary> dictionaries = new LinkedHashMap<>();
        dictionaries.put("en_us", dictionary("en_us", "fluid.water", "Water"));
        dictionaries.put("ja_jp", dictionary("ja_jp", "fluid.water", "水"));
        return dictionaries;
    }

    @Test
    public void returnsNamesWhenLocalizedNameMatchesTheStandardPath() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("en_us", "Water");
        expected.put("ja_jp", "水");
        assertEquals(expected, FluidSecondaryNames.resolve("fluid.water", "水", "水", dictionaries()));
    }

    @Test
    public void returnsNothingWhenLocalizedNameDiffers() {
        assertTrue(FluidSecondaryNames.resolve("fluid.water", "Holy Water", "水", dictionaries()).isEmpty());
        assertTrue(FluidSecondaryNames.resolve(null, "水", "水", dictionaries()).isEmpty());
        assertTrue(FluidSecondaryNames.resolve("fluid.water", null, null, dictionaries()).isEmpty());
    }

    @Test
    public void omitsLanguagesWithoutTheKey() {
        assertTrue(FluidSecondaryNames.resolve("fluid.lava", "熔岩", "熔岩", dictionaries()).isEmpty());
    }

    @Test
    public void picksTheFirstCandidateKeyWhoseTranslationMatches() {
        Map<String, String> lang = new HashMap<>();
        lang.put("tile.water.name", "水");
        assertEquals("tile.water.name", FluidSecondaryNames.consistentKey("水",
                Arrays.asList("fluid.water", null, "tile.water.name"), key -> lang.getOrDefault(key, key)));
        lang.put("fluid.water", "水");
        assertEquals("fluid.water", FluidSecondaryNames.consistentKey("水",
                Arrays.asList("fluid.water", "tile.water.name"), key -> lang.getOrDefault(key, key)));
    }

    @Test
    public void picksNoKeyWhenNoTranslationMatches() {
        assertNull(FluidSecondaryNames.consistentKey("Holy Water",
                Arrays.asList("fluid.water", "tile.water.name"), key -> "水"));
        assertNull(FluidSecondaryNames.consistentKey(null, Arrays.asList("fluid.water"), key -> key));
    }
}

