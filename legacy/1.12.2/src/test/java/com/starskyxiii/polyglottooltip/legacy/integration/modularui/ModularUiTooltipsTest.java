package com.starskyxiii.polyglottooltip.legacy.integration.modularui;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class ModularUiTooltipsTest {
    @Test
    public void addsOneFormattedLinePerSecondaryName() {
        assertEquals(Arrays.asList("§7Water", "§7Eau"), ModularUiTooltips.lines("水", Arrays.asList("Water", "Eau"), "§7"));
        assertEquals(Collections.singletonList("§7Water"), ModularUiTooltips.lines("水", Arrays.asList("Water", "§f水"), "§7"));
    }

    @Test
    public void skipsNamesEqualToTheShownName() {
        assertEquals(Collections.emptyList(), ModularUiTooltips.lines("§bWater", Collections.singletonList("Water"), "§7"));
        assertEquals(Collections.emptyList(), ModularUiTooltips.lines("水", Collections.emptyList(), "§7"));
    }
}
