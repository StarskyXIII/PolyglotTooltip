package com.starskyxiii.polyglottooltip.legacy.integration.jei;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.junit.Test;

import com.starskyxiii.polyglottooltip.legacy.minecraft.FluidSecondaryNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryDictionary;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryNames;

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

    private static List<String> ingredientNames(String key, String actual, String expected, String language) {
        Map<String, SecondaryDictionary> dictionaries = new LinkedHashMap<>();
        dictionaries.put(language, SecondaryDictionary.load(language, (code, sink) -> {
            sink.accept("fluid.water", "en_us".equals(code) ? "Water" : "水");
            sink.accept("gas.hydrogen", "en_us".equals(code) ? "Hydrogen" : "氢气");
        }));
        return SecondaryNames.of(actual, Collections.singletonList(language),
                FluidSecondaryNames.resolve(key, actual, expected, dictionaries), false, "zh_tw").names();
    }

    @Test
    public void indexesFluidAndGasSecondaryNamesWithScriptVariants() {
        assertEquals(Arrays.asList("water"),
                SearchTokens.of("水", ingredientNames("fluid.water", "水", "水", "en_us"), true, LOWER));
        assertEquals(Arrays.asList("氢气", "hydrogen"),
                SearchTokens.of("氫氣", ingredientNames("gas.hydrogen", "氫氣", "氫氣", "en_us"), true, LOWER));
        assertEquals(Arrays.asList("氢气", "氫氣"),
                SearchTokens.of("Hydrogen", ingredientNames("gas.hydrogen", "Hydrogen", "Hydrogen", "zh_cn"), true,
                        LOWER));
        assertEquals(Collections.singletonList("hydrogen"),
                SearchTokens.of("氫氣", ingredientNames("gas.hydrogen", "氫氣", "氫氣", "en_us"), false, LOWER));
    }

    @Test
    public void indexesNoGasNamesThatFailTheConsistencyCheck() {
        assertEquals(Collections.singletonList("液态氢"),
                SearchTokens.of("液態氫", ingredientNames("gas.hydrogen", "液態氫", "氫氣", "en_us"), true, LOWER));
    }
}
