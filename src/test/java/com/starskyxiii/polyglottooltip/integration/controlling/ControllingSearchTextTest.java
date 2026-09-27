package com.starskyxiii.polyglottooltip.integration.controlling;

//? if >1.16.5 {
import me.towdium.jecharacters.utils.Match;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllingSearchTextTest {
    @BeforeEach
    void resetMatcher() {
        Match.clearCalls();
    }

    @Test
    void nativeMatchSucceedsWithoutOptionalMatcher() {
        assertTrue(ControllingSearchUtil.matchesText("Jump\n跳跃", "JUMP"));
        assertTrue(Match.candidates().isEmpty());
    }

    @Test
    void optionalMatcherReceivesEachCompleteCandidate() {
        assertTrue(ControllingSearchUtil.matchesText("Taipei\n臺灣", "taiwan"));
        assertEquals(List.of("Taipei", "臺灣"), Match.candidates());
    }

    @Test
    void optionalMatcherCannotCombineSeparateLines() {
        assertFalse(ControllingSearchUtil.matchesText("臺\n灣", "taiwan"));
        assertEquals(List.of("臺", "灣"), Match.candidates());
    }
}
//?}
