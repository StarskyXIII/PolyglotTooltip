package com.starskyxiii.polyglottooltip.legacy.minecraft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class SecondaryNamesTest {
    private static final List<String> LANGUAGES = Arrays.asList("ja_jp", "en_us", "zh_cn");

    private static Map<String, String> names() {
        Map<String, String> names = new HashMap<>();
        names.put("zh_cn", "铁锭");
        names.put("en_us", "Iron Ingot");
        names.put("ja_jp", "鉄インゴット");
        return names;
    }

    @Test
    public void followsConfiguredLanguageOrder() {
        SecondaryNames result = SecondaryNames.of("鐵錠", LANGUAGES, names(), false, "zh_tw");
        assertEquals(Arrays.asList("鉄インゴット", "Iron Ingot", "铁锭"), result.names());
    }

    @Test
    public void dropsNamesEqualToPrimaryIgnoringFormatting() {
        SecondaryNames result = SecondaryNames.of("§bIron Ingot ", LANGUAGES, names(), true, "en_us");
        assertEquals(Arrays.asList("鉄インゴット", "铁锭"), result.names());
    }

    @Test
    public void skipsCurrentLanguageUnlessAlwaysShow() {
        Map<String, String> names = names();
        names.put("en_us", "Iron Bar");
        assertEquals(Arrays.asList("鉄インゴット", "铁锭"),
                SecondaryNames.of("Iron Ingot", LANGUAGES, names, false, "en_us").names());
        assertEquals(Arrays.asList("鉄インゴット", "Iron Bar", "铁锭"),
                SecondaryNames.of("Iron Ingot", LANGUAGES, names, true, "en_us").names());
    }

    @Test
    public void dropsBlankAndDuplicateNames() {
        Map<String, String> names = new HashMap<>();
        names.put("ja_jp", " ");
        names.put("en_us", "Ingot");
        names.put("zh_cn", "Ingot");
        assertEquals(Collections.singletonList("Ingot"),
                SecondaryNames.of("錠", LANGUAGES, names, false, "zh_tw").names());
    }

    @Test
    public void emptyWhenNothingRemains() {
        assertSame(SecondaryNames.EMPTY, SecondaryNames.of("x", LANGUAGES, Collections.<String, String>emptyMap(), false, "zh_tw"));
        assertSame(SecondaryNames.EMPTY, SecondaryNames.of("x", LANGUAGES, null, false, "zh_tw"));
        assertSame(SecondaryNames.EMPTY, SecondaryNames.EMPTY.withSource("standard"));
    }

    @Test
    public void withSourceKeepsTheNames() {
        SecondaryNames result = SecondaryNames.of("鐵錠", LANGUAGES, names(), false, "zh_tw").withSource("standard");
        assertEquals("standard", result.source());
        assertEquals(SecondaryNames.of("鐵錠", LANGUAGES, names(), false, "zh_tw").names(), result.names());
        assertFalse(result.partial());
    }

    @Test
    public void partialNamesKeepTheUntranslatedPartsOfShownLanguages() {
        Map<String, String> names = new HashMap<>();
        names.put("en_us", "平凡的Potion");
        names.put("ja_jp", "平凡的ポーション");
        names.put("zh_cn", "平凡的药水");
        Map<String, List<String>> untranslated = new HashMap<>();
        untranslated.put("en_us", Collections.singletonList("平凡的"));
        untranslated.put("zh_cn", Collections.singletonList("平凡的"));
        SecondaryNames result = SecondaryNames.of("平凡的药水", LANGUAGES, names, untranslated, false, "zh_tw")
                .withSource("CompositeNameResolver");
        assertEquals(Arrays.asList("平凡的ポーション", "平凡的Potion"), result.names());
        assertTrue(result.partial());
        assertEquals("CompositeNameResolver" + SecondaryNames.PARTIAL_SUFFIX, result.source());
        assertEquals(Collections.singletonMap("en_us", Collections.singletonList("平凡的")), result.untranslated());
    }
}
