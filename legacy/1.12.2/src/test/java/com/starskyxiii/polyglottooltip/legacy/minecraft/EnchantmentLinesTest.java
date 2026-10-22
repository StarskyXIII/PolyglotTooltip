package com.starskyxiii.polyglottooltip.legacy.minecraft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class EnchantmentLinesTest {
    private static List<String> tooltip(String... lines) {
        return new ArrayList<>(Arrays.asList(lines));
    }

    @Test
    public void composesLikeGetTranslatedName() {
        assertEquals("Efficiency V", EnchantmentLines.compose("Efficiency", "V", true));
        assertEquals("Silk Touch", EnchantmentLines.compose("Silk Touch", "I", false));
        assertEquals("Mending", EnchantmentLines.compose("Mending", null, false));
        assertNull(EnchantmentLines.compose(null, "V", true));
        assertNull(EnchantmentLines.compose("Efficiency", null, true));
    }

    @Test
    public void insertsAfterTheMatchingLine() {
        List<String> lines = tooltip("Diamond Pickaxe", "效率 V", "耐久 III", "When in main hand:");
        int added = EnchantmentLines.insert(lines, "效率 V", "效率 V", Collections.singletonList("Efficiency V"), "§7");
        assertEquals(1, added);
        assertEquals(tooltip("Diamond Pickaxe", "效率 V", "§7Efficiency V", "耐久 III", "When in main hand:"), lines);
    }

    @Test
    public void curseColourIsIgnoredWhenComparingWithTheStandardName() {
        List<String> lines = tooltip("Elytra", "§c消失詛咒");
        assertEquals(1, EnchantmentLines.insert(lines, "§c消失詛咒", "§c消失詛咒",
                Collections.singletonList("Curse of Vanishing"), ""));
        assertEquals(tooltip("Elytra", "§c消失詛咒", "Curse of Vanishing"), lines);
    }

    @Test
    public void skipsWhenTheTranslatedNameIsNotTheStandardComposition() {
        List<String> lines = tooltip("Diamond Pickaxe", "Efficiency 5");
        assertEquals(0, EnchantmentLines.insert(lines, "Efficiency 5", "效率 V",
                Collections.singletonList("Efficiency V"), ""));
        assertEquals(tooltip("Diamond Pickaxe", "Efficiency 5"), lines);
    }

    @Test
    public void skipsWhenNoLineMatchesExactly() {
        List<String> lines = tooltip("Diamond Pickaxe", "§9效率 V");
        assertEquals(0, EnchantmentLines.insert(lines, "效率 V", "效率 V",
                Collections.singletonList("Efficiency V"), ""));
        assertEquals(tooltip("Diamond Pickaxe", "§9效率 V"), lines);
    }

    @Test
    public void neverMatchesTheNameLine() {
        List<String> lines = tooltip("Mending");
        assertEquals(0, EnchantmentLines.insert(lines, "Mending", "Mending",
                Collections.singletonList("經驗修補"), ""));
        assertEquals(0, EnchantmentLines.insert(new ArrayList<String>(), "Mending", "Mending",
                Collections.singletonList("經驗修補"), ""));
        assertEquals(tooltip("Mending"), lines);
    }

    @Test
    public void skipsWhenNoSecondaryNameRemains() {
        List<String> lines = tooltip("Enchanted Book", "Mending");
        assertEquals(0, EnchantmentLines.insert(lines, "Mending", "Mending", Collections.<String>emptyList(), ""));
        assertEquals(tooltip("Enchanted Book", "Mending"), lines);
    }
}
