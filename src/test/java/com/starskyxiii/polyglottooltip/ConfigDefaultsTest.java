package com.starskyxiii.polyglottooltip;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ConfigDefaultsTest {

    @Test
    void preservesNeoForgeBaselineDefaults() {
        assertEquals(List.of("en_us"), Config.DISPLAY_LANGUAGE.getDefault());
        assertFalse(Config.ALWAYS_SHOW.getDefault());
        assertFalse(Config.ENABLE_CHINESE_SCRIPT_MATCHING.getDefault());
        assertEquals("&7", Config.TOOLTIP_SECONDARY_NAME_FORMAT.getDefault());
        assertEquals("&7", Config.JADE_SECONDARY_NAME_FORMAT.getDefault());
    }
}
