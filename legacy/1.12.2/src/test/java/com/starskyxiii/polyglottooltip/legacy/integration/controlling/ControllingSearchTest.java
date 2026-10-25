package com.starskyxiii.polyglottooltip.legacy.integration.controlling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class ControllingSearchTest {
    @Test
    public void searchTextAddsTheSecondaryNameAfterTheShownName() {
        assertEquals("跳跃\njump", ControllingSearch.text("跳跃", Collections.singletonList("Jump"), false));
        assertEquals("移动\nmovement\n移動",
                ControllingSearch.text("移动", Arrays.asList("Movement", "移動"), false));
    }

    @Test
    public void searchTextIsTheShownNameWithoutSecondaryNames() {
        assertEquals("跳跃", ControllingSearch.text("跳跃", Collections.emptyList(), false));
    }

    @Test
    public void theLowercasedSearchTextContainsTheSecondaryQuery() {
        String text = ControllingSearch.text("跳跃", Collections.singletonList("Jump"), false).toLowerCase();
        assertTrue(text.contains("jump"));
        assertTrue(text.contains("跳跃"));
    }

    @Test
    public void withoutAKeyTheShownNameIsKept() {
        assertEquals("跳跃", ControllingSearch.searchText(null, "跳跃"));
    }
}
