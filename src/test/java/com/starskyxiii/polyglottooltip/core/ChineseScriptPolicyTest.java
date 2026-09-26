package com.starskyxiii.polyglottooltip.core;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChineseScriptPolicyTest {
    @Test
    void rawVariantsPreserveCaseWhitespaceAndRegexPunctuation() {
        Set<String> variants = ChineseScriptPolicy.scriptVariants("  ^臺灣.*AbC$  ", true);

        assertTrue(variants.contains("  ^臺灣.*AbC$  "));
        assertTrue(variants.contains("  ^台湾.*AbC$  "));
    }

    @Test
    void disabledRawVariantsRetainOnlyTheExactInput() {
        assertEquals(Set.of("  鐵錠 AbC  "),
                ChineseScriptPolicy.scriptVariants("  鐵錠 AbC  ", false));
    }

    @Test
    void rawVariantsKeepWhitespaceOnlyValuesButRejectNullAndEmpty() {
        assertEquals(Set.of("  "), ChineseScriptPolicy.scriptVariants("  ", true));
        assertTrue(ChineseScriptPolicy.scriptVariants(null, true).isEmpty());
        assertTrue(ChineseScriptPolicy.scriptVariants("", true).isEmpty());
    }
}
