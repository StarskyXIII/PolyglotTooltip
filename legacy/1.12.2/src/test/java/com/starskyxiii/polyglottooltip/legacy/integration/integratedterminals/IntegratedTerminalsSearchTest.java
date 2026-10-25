package com.starskyxiii.polyglottooltip.legacy.integration.integratedterminals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.starskyxiii.polyglottooltip.legacy.integration.StorageSearchText;

public class IntegratedTerminalsSearchTest {
    private static String text(String primary, String secondary) {
        return StorageSearchText.compose(primary, Collections.singletonList(secondary), false);
    }

    @Test
    public void matchesTheRegexAgainstEachSecondaryLine() {
        String text = text("钻石", "Diamond");
        assertTrue(IntegratedTerminalsSearch.anySecondary(text, token -> token.matches(".*diam.*")));
        assertFalse(IntegratedTerminalsSearch.anySecondary(text, token -> token.matches(".*emerald.*")));
    }

    @Test
    public void leavesThePrimaryLineToTheNativeMatch() {
        List<String> tested = new ArrayList<>();
        IntegratedTerminalsSearch.anySecondary(text("水", "Water"), token -> tested.add(token) && false);
        assertFalse(tested.contains("水"));
        assertTrue(tested.contains("water"));
        assertFalse(IntegratedTerminalsSearch.anySecondary("水", token -> true));
        assertFalse(IntegratedTerminalsSearch.anySecondary(null, token -> true));
    }

    @Test
    public void aWholeLineRegexNoLongerFailsOnTheJoinedText() {
        String text = text("钻石", "Diamond");
        assertFalse(text.matches(".*diamond.*"));
        assertTrue(IntegratedTerminalsSearch.anySecondary(text, token -> token.matches(".*diamond.*")));
    }

    @Test
    public void withoutAStackNothingExtraMatches() {
        assertFalse(IntegratedTerminalsSearch.itemMatches(null, token -> true));
        assertFalse(IntegratedTerminalsSearch.fluidMatches(null, token -> true));
    }
}
