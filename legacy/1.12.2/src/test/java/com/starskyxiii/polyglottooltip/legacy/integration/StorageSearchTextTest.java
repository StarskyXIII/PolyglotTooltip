package com.starskyxiii.polyglottooltip.legacy.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Test;

public class StorageSearchTextTest {
    private static String text(String primary, String... names) {
        return StorageSearchText.compose(primary, Arrays.asList(names), true).toLowerCase();
    }

    private static boolean ae2Terms(String text, String query) {
        for (String term : query.toLowerCase().split(" ")) {
            boolean exclude = term.length() > 1 && (term.startsWith("-") || term.startsWith("!"));
            boolean found = text.contains(exclude ? term.substring(1) : term);
            if (found == exclude) {
                return false;
            }
        }
        return true;
    }

    private static boolean ae2Regex(String text, String query) {
        return Pattern.compile(Pattern.quote(query.toLowerCase()), Pattern.CASE_INSENSITIVE).matcher(text).find();
    }

    @Test
    public void secondaryNamesFollowThePrimaryOnTheirOwnLines() {
        assertEquals("石頭\nstone", StorageSearchText.compose("石頭", Collections.singletonList("Stone"), false));
        assertEquals("石頭\n石头\nstone", StorageSearchText.compose("石頭", Collections.singletonList("Stone"), true));
    }

    @Test
    public void simplifiedQueriesFindATraditionalPrimaryThatHasSecondaryNames() {
        assertTrue(text("鐵錠", "Iron Ingot").contains("铁锭"));
        assertTrue(text("铁锭", "Iron Ingot").contains("鐵錠"));
    }

    @Test
    public void withoutSecondaryNamesTheTextIsThePrimaryName() {
        assertEquals("Stone", StorageSearchText.compose("Stone", Collections.<String>emptyList(), true));
    }

    @Test
    public void ae2TermSearchMatchesASecondaryName() {
        String stone = text("石头", "Stone");
        assertTrue(ae2Terms(stone, "stone"));
        assertTrue(ae2Terms(stone, "石头"));
        assertFalse(ae2Terms(text("铁锭", "Iron Ingot"), "stone"));
        assertTrue(ae2Terms(text("铁锭", "Iron Ingot"), "iron ingot"));
        assertFalse(ae2Terms(text("铁锭", "Iron Ingot"), "iron -ingot"));
    }

    @Test
    public void ae2RegexSearchMatchesASecondaryName() {
        assertTrue(ae2Regex(text("铁锭", "Iron Ingot"), "Iron"));
        assertFalse(ae2Regex(text("铁锭", "Iron Ingot"), "Gold"));
    }

    @Test
    public void rsNameSearchMatchesASecondaryName() {
        assertTrue(text("Stone", "石頭").contains("石頭"));
        assertFalse(text("Stone", "石頭").contains("dirt"));
    }

    @Test
    public void simplifiedQueriesFindTraditionalSecondaryNamesAndBack() {
        assertTrue(text("Iron Ingot", "鐵錠").contains("铁锭"));
        assertTrue(text("Iron Ingot", "铁锭").contains("鐵錠"));
        List<String> names = Collections.singletonList("鐵錠");
        assertFalse(StorageSearchText.compose("Iron Ingot", names, false).contains("铁锭"));
    }

    @Test
    public void aQueryDoesNotMatchAcrossTwoNames() {
        String ingot = text("Iron Ingot", "鐵錠");
        assertFalse(ingot.contains("ingot鐵"));
        assertFalse(ingot.contains("ingot 鐵"));
    }
}
