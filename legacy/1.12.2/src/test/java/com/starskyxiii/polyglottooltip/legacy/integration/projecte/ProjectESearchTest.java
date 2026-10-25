package com.starskyxiii.polyglottooltip.legacy.integration.projecte;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Locale;

import org.junit.Test;

import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryNames;

public class ProjectESearchTest {
    private static SecondaryNames names(String primary, String secondary) {
        return SecondaryNames.of(primary, Collections.singletonList("en_us"), Collections.singletonMap("en_us", secondary),
                false, "zh_cn");
    }

    @Test
    public void theFilteredNameContainsTheSecondaryQuery() {
        String text = ProjectESearch.TEXT.text("钻石", names("钻石", "Diamond")).toLowerCase(Locale.ROOT);
        assertTrue(text.contains("diamond"));
        assertTrue(text.contains("钻石"));
        assertFalse(text.contains("emerald"));
    }

    @Test
    public void keepsTheShownNameWhenThereAreNoSecondaryNames() {
        assertEquals("钻石", ProjectESearch.TEXT.text("钻石", SecondaryNames.EMPTY));
    }

    @Test
    public void withoutAStackTheShownNameIsKept() {
        assertEquals("钻石", ProjectESearch.searchText(null, "钻石"));
    }
}
