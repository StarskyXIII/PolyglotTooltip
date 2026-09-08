package com.starskyxiii.polyglottooltip;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigDefaultsTest {

    @Test
    void preservesVersionSpecificBaselineDefaults() {
        assertEquals(List.of("en_us"), Config.DISPLAY_LANGUAGE.getDefault());
        assertFalse(Config.ALWAYS_SHOW.getDefault());
        //? if <=1.20.1 {
        /*assertTrue(Config.ENABLE_CHINESE_SCRIPT_MATCHING.getDefault());
        *///?} else {
        assertFalse(Config.ENABLE_CHINESE_SCRIPT_MATCHING.getDefault());
        //?}
        assertEquals("&7", Config.TOOLTIP_SECONDARY_NAME_FORMAT.getDefault());
        assertEquals("&7", Config.JADE_SECONDARY_NAME_FORMAT.getDefault());
    }

    //? if <=1.20.1 {
    /*@Test
    void forgeDefaultCanBeExplicitlyDisabled() {
        TestConfigSupport.load(false);

        assertFalse(Config.ENABLE_CHINESE_SCRIPT_MATCHING.get());
    }
    *///?}
}
