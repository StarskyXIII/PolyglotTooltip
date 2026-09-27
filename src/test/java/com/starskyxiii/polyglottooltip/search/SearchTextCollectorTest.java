package com.starskyxiii.polyglottooltip.search;

import com.starskyxiii.polyglottooltip.TestConfigSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchTextCollectorTest {
    @BeforeEach
    @AfterEach
    void loadDisabledDefaults() {
        TestConfigSupport.load(false);
    }

    @Test
    void unicodeWhitespaceDoesNotCreateEntriesButNonBreakingSpaceRemainsSearchable() {
        String text = new SearchTextCollector()
                .addText("\u2003\u3000")
                .addTextBlock("\u2003\u3000")
                .addTextBlock("  Iron Ore \r\n鐵錠\u2028IRON ORE")
                .addText("\u00a0")
                .build();

        assertEquals("iron ore\n鐵錠\n\u00a0", text);
    }
}
