package com.starskyxiii.polyglottooltip.legacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class SettingsTest {
    @Test
    public void lowercasesTrimsAndDeduplicatesInOrder() {
        Settings settings = new Settings(
                Arrays.asList(" ZH_TW ", "en_us", "zh_tw", "ja_JP"), false, true, "&7", "&7");
        assertEquals(Arrays.asList("zh_tw", "en_us", "ja_jp"), settings.displayLanguages());
    }

    @Test
    public void dropsInvalidCodes() {
        List<String> codes = Arrays.asList(
                null, "", "x", "zh-tw", "en us", "ü_de", "a234567890123456789012345678901234", "ab", "a2345678901234567890123456789012");
        assertEquals(Arrays.asList("ab", "a2345678901234567890123456789012"), Settings.normalizeLanguages(codes));
    }

    @Test
    public void keepsAtMostThirtyTwoLanguages() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            codes.add("l" + i);
        }
        List<String> normalized = Settings.normalizeLanguages(codes);
        assertEquals(Settings.MAX_LANGUAGES, normalized.size());
        assertEquals("l31", normalized.get(31));
    }

    @Test
    public void limitCountsDistinctCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            codes.add("l" + (i % 20));
        }
        codes.add("zz");
        assertEquals(21, Settings.normalizeLanguages(codes).size());
    }

    @Test
    public void allowsEmptyLanguageList() {
        assertTrue(Settings.normalizeLanguages(Collections.<String>emptyList()).isEmpty());
        assertTrue(Settings.normalizeLanguages(null).isEmpty());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void languagesAreImmutable() {
        Settings.DEFAULT.displayLanguages().add("zh_tw");
    }

    @Test
    public void defaultsMatchConfigDefaults() {
        assertEquals(Collections.singletonList("en_us"), Settings.DEFAULT.displayLanguages());
        assertEquals("§7", Settings.DEFAULT.tooltipPrefix());
        assertEquals("§7", Settings.DEFAULT.hudPrefix());
    }

    @Test
    public void convertsAmpersandFormattingCodes() {
        assertEquals("§7§o", Settings.formattingCodes("&7&o"));
        assertEquals("§a§l", Settings.formattingCodes("&A&L"));
        assertEquals("§r", Settings.formattingCodes("&r"));
    }

    @Test
    public void keepsAmpersandsThatAreNotFormattingCodes() {
        assertEquals("&z §7& ", Settings.formattingCodes("&z &7& "));
        assertEquals("&", Settings.formattingCodes("&"));
        assertEquals("[§6", Settings.formattingCodes("[&6"));
    }

    @Test
    public void treatsNullFormatAsEmpty() {
        Settings settings = new Settings(Collections.singletonList("en_us"), true, false, null, null);
        assertEquals("", settings.tooltipFormat());
        assertEquals("", settings.tooltipPrefix());
        assertEquals("", settings.hudPrefix());
        assertEquals("", Settings.formattingCodes(""));
    }

    @Test
    public void tooltipAndHudFormatsAreIndependent() {
        Settings settings = new Settings(Collections.singletonList("en_us"), false, true, "&8", "&b&o");
        assertEquals("§8", settings.tooltipPrefix());
        assertEquals("§b§o", settings.hudPrefix());
        assertEquals("&b&o", settings.hudFormat());
    }

    @Test
    public void equalityUsesNormalizedValues() {
        Settings a = new Settings(Arrays.asList("ZH_TW", "zh_tw"), false, true, "&7", "&7");
        Settings b = new Settings(Collections.singletonList("zh_tw"), false, true, "&7", "&7");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
