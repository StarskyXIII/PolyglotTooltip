package com.starskyxiii.polyglottooltip;
//~ forge_neoforge_package

//? !fabric {
import net.neoforged.neoforge.common.ModConfigSpec;
//?}
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigDefaultsTest {

    @Test
    void preservesVersionSpecificBaselineDefaults() {
        assertEquals(List.of("en_us"), defaultValue(Config.DISPLAY_LANGUAGE));
        assertFalse((Boolean) defaultValue(Config.ALWAYS_SHOW));
        //? fabric {
        /*assertTrue((Boolean) defaultValue(Config.ENABLE_CHINESE_SCRIPT_MATCHING));
        *///?} else if <=1.20.1 {
        /*assertTrue((Boolean) defaultValue(Config.ENABLE_CHINESE_SCRIPT_MATCHING));
        *///?} else {
        assertFalse((Boolean) defaultValue(Config.ENABLE_CHINESE_SCRIPT_MATCHING));
        //?}
        assertEquals("&7", defaultValue(Config.TOOLTIP_SECONDARY_NAME_FORMAT));
        assertEquals("&7", defaultValue(Config.JADE_SECONDARY_NAME_FORMAT));
    }

    //? if <=1.20.1 || fabric {
    /*@Test
    void forgeDefaultCanBeExplicitlyDisabled() {
        TestConfigSupport.load(false);

        assertFalse(Config.ENABLE_CHINESE_SCRIPT_MATCHING.get());
    }
    *///?}

    //? fabric {
    /*private static Object defaultValue(FabricConfigValue<?> value) {
        return value.getDefault();
    }
    *///?} else {
    private static Object defaultValue(ModConfigSpec.ConfigValue<?> value) {
        Object valueSpec = Config.SPEC.getSpec().get(value.getPath());
        return ((ModConfigSpec.ValueSpec) valueSpec).getDefault();
    }
    //?}
}
