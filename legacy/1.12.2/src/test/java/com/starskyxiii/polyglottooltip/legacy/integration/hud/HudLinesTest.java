package com.starskyxiii.polyglottooltip.legacy.integration.hud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class HudLinesTest {
    @Test
    public void prefixesEachNameAsItsOwnLine() {
        assertEquals(Arrays.asList("§7Stone", "§7石"),
                HudLines.compose(Arrays.asList("Stone", "石"), "§7", Collections.<String>emptyList()));
    }

    @Test
    public void dropsNamesAlreadyShownIgnoringFormatting() {
        assertEquals(Collections.singletonList("§7Stone"),
                HudLines.compose(Arrays.asList("Stone", "§e石頭"), "§7", Arrays.asList("§f石頭", "§9§oMinecraft")));
    }

    @Test
    public void dropsBlankAndRepeatedNames() {
        assertEquals(Collections.singletonList("Cow"),
                HudLines.compose(Arrays.asList(" ", "§r", null, "Cow", "§lCow"), "", null));
    }

    @Test
    public void returnsNothingWithoutNames() {
        assertTrue(HudLines.compose(Collections.<String>emptyList(), "§7", Collections.singletonList("Stone")).isEmpty());
        assertTrue(HudLines.compose(null, "§7", null).isEmpty());
    }

    @Test
    public void toleratesMissingPrefix() {
        assertEquals(Collections.singletonList("Water"),
                HudLines.compose(Collections.singletonList("Water"), null, Collections.<String>emptyList()));
    }
}
