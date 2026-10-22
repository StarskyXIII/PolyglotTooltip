package com.starskyxiii.polyglottooltip.legacy.integration.jei;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

import org.junit.Test;

public class SearchTokensTest {
    private static final UnaryOperator<String> LOWER = value -> value.toLowerCase(Locale.ROOT);

    @Test
    public void addsLowercaseSecondaryNamesButNotThePrimary() {
        List<String> tokens = SearchTokens.of("鑽石鎬", Collections.singletonList("Diamond Pickaxe"), false, LOWER);
        assertEquals(Collections.singletonList("diamond pickaxe"), tokens);
    }

    @Test
    public void stripsFormattingAndDropsDuplicates() {
        List<String> tokens = SearchTokens.of("§bStone", Arrays.asList("§7Stone", "Pierre", "pierre"), false, LOWER);
        assertEquals(Collections.singletonList("pierre"), tokens);
    }

    @Test
    public void noSecondaryNamesMeansNoTokensForLatinNames() {
        assertTrue(SearchTokens.of("Stone", Collections.<String>emptyList(), true, LOWER).isEmpty());
    }

    @Test
    public void addsSimplifiedVariantsOfTraditionalNamesWhenScriptMatchingIsOn() {
        List<String> tokens = SearchTokens.of("鑽石鎬", Collections.singletonList("Diamond Pickaxe"), true, LOWER);
        assertEquals(Arrays.asList("钻石镐", "diamond pickaxe"), tokens);
    }

    @Test
    public void addsTraditionalVariantsOfSimplifiedSecondaryNames() {
        List<String> tokens = SearchTokens.of("Diamond Pickaxe", Collections.singletonList("钻石镐"), true, LOWER);
        assertEquals(Arrays.asList("钻石镐", "鑽石鎬"), tokens);
    }

    @Test
    public void scriptMatchingOffAddsNoVariants() {
        List<String> tokens = SearchTokens.of("Diamond Pickaxe", Collections.singletonList("钻石镐"), false, LOWER);
        assertEquals(Collections.singletonList("钻石镐"), tokens);
    }
}
