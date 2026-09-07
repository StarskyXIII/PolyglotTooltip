package com.starskyxiii.polyglottooltip.search;

import com.starskyxiii.polyglottooltip.TestConfigSupport;
import com.starskyxiii.polyglottooltip.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChineseScriptSearchMatcherTest {

    private static final String SIMPLIFIED = "台湾";
    private static final String TRADITIONAL = "臺灣";
    private static final String SECONDARY_NAME = "Iron Ore";

    @BeforeEach
    void loadDisabledDefaults() {
        TestConfigSupport.load(false);
    }

    @AfterEach
    void restoreDisabledDefaults() {
        TestConfigSupport.load(false);
    }

    @Test
    void disabledModeMatchesOriginalAndExtraNamesWithoutScriptExpansion() {
        assertFalse(Config.ENABLE_CHINESE_SCRIPT_MATCHING.get());
        assertEquals(Set.of(TRADITIONAL.toLowerCase(Locale.ROOT)),
                ChineseScriptSearchMatcher.getSearchVariants(TRADITIONAL));

        assertTrue(ChineseScriptSearchMatcher.containsMatch(TRADITIONAL, TRADITIONAL));
        assertTrue(ChineseScriptSearchMatcher.containsMatch("iron", List.of(TRADITIONAL, SECONDARY_NAME)));
        assertFalse(ChineseScriptSearchMatcher.containsMatch(SIMPLIFIED, TRADITIONAL));
        assertFalse(ChineseScriptSearchMatcher.containsMatch(TRADITIONAL, SIMPLIFIED));
    }

    @Test
    void enabledModeMatchesSimplifiedAndTraditionalQueriesAgainstEitherCandidate() {
        TestConfigSupport.setChineseScriptMatching(true);
        ChineseScriptSearchMatcher.clearCaches();

        Set<String> variants = ChineseScriptSearchMatcher.getSearchVariants(TRADITIONAL);
        assertTrue(variants.contains(TRADITIONAL));
        assertTrue(variants.contains(SIMPLIFIED));
        assertTrue(ChineseScriptSearchMatcher.containsMatch(SIMPLIFIED, TRADITIONAL));
        assertTrue(ChineseScriptSearchMatcher.containsMatch(TRADITIONAL, SIMPLIFIED));
        assertTrue(ChineseScriptSearchMatcher.containsMatch(SIMPLIFIED,
                List.of(SECONDARY_NAME, TRADITIONAL)));
    }

    @Test
    void blankQueriesMatchRegardlessOfCandidateCollection() {
        assertTrue(ChineseScriptSearchMatcher.containsMatch(null, "anything"));
        assertTrue(ChineseScriptSearchMatcher.containsMatch("  ", List.of()));
        assertTrue(ChineseScriptSearchMatcher.containsMatch("", List.of("anything")));
        assertTrue(ChineseScriptSearchMatcher.getSearchVariants(null).isEmpty());
    }

    @Test
    void wrappedMatcherPreservesNullPrimaryNameSemantics() {
        assertTrue(WrappedSearchMatcher.matches("", (String) null, () -> false, List::of));
        assertTrue(WrappedSearchMatcher.matches("native", (String) null, () -> true, List::of));
        assertFalse(WrappedSearchMatcher.matches("missing", (String) null, () -> false, List::of));
    }

    @Test
    void changingConfigRequiresReloadCacheInvalidation() {
        assertFalse(ChineseScriptSearchMatcher.containsMatch(SIMPLIFIED, TRADITIONAL));

        TestConfigSupport.setChineseScriptMatching(true);
        ChineseScriptSearchMatcher.clearCaches();
        assertTrue(ChineseScriptSearchMatcher.containsMatch(SIMPLIFIED, TRADITIONAL));

        TestConfigSupport.setChineseScriptMatching(false);
        ChineseScriptSearchMatcher.clearCaches();
        assertFalse(ChineseScriptSearchMatcher.containsMatch(SIMPLIFIED, TRADITIONAL));
    }

    @Test
    void variantIndexerAddsOnlyAdditionalEnabledScriptKeys() {
        TestConfigSupport.setChineseScriptMatching(true);
        ChineseScriptSearchMatcher.clearCaches();
        List<String> indexed = new ArrayList<>();

        ChineseScriptVariantIndexer.putVariants(TRADITIONAL, "value",
                (key, value) -> indexed.add(key));

        assertEquals(List.of(SIMPLIFIED), indexed);

        TestConfigSupport.setChineseScriptMatching(false);
        ChineseScriptSearchMatcher.clearCaches();
        indexed.clear();
        ChineseScriptVariantIndexer.putVariants(TRADITIONAL, "value",
                (key, value) -> indexed.add(key));
        assertTrue(indexed.isEmpty());
    }
}
