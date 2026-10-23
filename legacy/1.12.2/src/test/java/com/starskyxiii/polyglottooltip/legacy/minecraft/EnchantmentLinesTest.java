package com.starskyxiii.polyglottooltip.legacy.minecraft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
    public void appendsToTheVanillaEnchantmentLine() {
        List<String> lines = tooltip("Diamond Pickaxe", "§7效率 III", "§7耐久 III", "When in main hand:");
        assertTrue(EnchantmentLines.append(lines, "§7效率 III",
                Collections.singletonList("Efficiency III")));
        assertEquals(tooltip("Diamond Pickaxe", "§7效率 III §7｜ §7Efficiency III", "§7耐久 III",
                "When in main hand:"), lines);
    }

    @Test
    public void keepsTheCurseColourOnTheSecondaryName() {
        List<String> lines = tooltip("Elytra", "§c消失詛咒");
        assertTrue(EnchantmentLines.append(lines, "§c消失詛咒",
                Collections.singletonList("Curse of Vanishing")));
        assertEquals(tooltip("Elytra", "§c消失詛咒 §7｜ §cCurse of Vanishing"), lines);
    }

    @Test
    public void repeatsTheSeparatorForEachLanguage() {
        List<String> lines = tooltip("Diamond Pickaxe", "§7效率 III");
        assertTrue(EnchantmentLines.append(lines, "§7效率 III",
                Arrays.asList("Efficiency III", "効率強化 III")));
        assertEquals(tooltip("Diamond Pickaxe", "§7效率 III §7｜ §7Efficiency III §7｜ §7効率強化 III"), lines);
    }

    @Test
    public void appendsWithoutStyleWhenTheLineHasNone() {
        List<String> lines = tooltip("Diamond Pickaxe", "效率 V");
        assertTrue(EnchantmentLines.append(lines, "效率 V", Collections.singletonList("Efficiency V")));
        assertEquals(tooltip("Diamond Pickaxe", "效率 V §7｜ Efficiency V"), lines);
    }

    @Test
    public void skipsNamesEqualToTheShownName() {
        List<String> lines = tooltip("Enchanted Book", "§7Mending");
        assertFalse(EnchantmentLines.append(lines, "§7Mending", Collections.singletonList("Mending")));
        assertEquals(tooltip("Enchanted Book", "§7Mending"), lines);
        assertTrue(EnchantmentLines.append(lines, "§7Mending", Arrays.asList("Mending", "經驗修補")));
        assertEquals(tooltip("Enchanted Book", "§7Mending §7｜ §7經驗修補"), lines);
    }

    @Test
    public void readsOnlyTheLeadingStyleCodes() {
        assertEquals("§7§o", EnchantmentLines.leadingStyle("§7§oName §cRed"));
        assertEquals("", EnchantmentLines.leadingStyle("Name"));
        assertEquals("", EnchantmentLines.leadingStyle("§"));
    }

    @Test
    public void readsTheDisplayedLevelAfterTheName() {
        assertEquals(" 11", EnchantmentLines.levelSuffix("§7效率 11", "效率"));
        assertEquals(" XI", EnchantmentLines.levelSuffix("效率 XI", "效率"));
        assertEquals("", EnchantmentLines.levelSuffix("§c消失詛咒", "消失詛咒"));
        assertNull(EnchantmentLines.levelSuffix("效率強化 3", "效率"));
        assertNull(EnchantmentLines.levelSuffix("Efficiency 5", "效率"));
        assertNull(EnchantmentLines.levelSuffix("Efficiency 5", ""));
    }

    @Test
    public void skipsWhenNoLineMatchesExactly() {
        List<String> lines = tooltip("Diamond Pickaxe", "§9效率 V");
        assertFalse(EnchantmentLines.append(lines, "效率 V",
                Collections.singletonList("Efficiency V")));
        assertEquals(tooltip("Diamond Pickaxe", "§9效率 V"), lines);
    }

    @Test
    public void neverMatchesTheNameLine() {
        List<String> lines = tooltip("Mending");
        assertFalse(EnchantmentLines.append(lines, "Mending",
                Collections.singletonList("經驗修補")));
        assertFalse(EnchantmentLines.append(new ArrayList<String>(), "Mending",
                Collections.singletonList("經驗修補")));
        assertEquals(tooltip("Mending"), lines);
    }

    @Test
    public void skipsWhenNoSecondaryNameRemains() {
        List<String> lines = tooltip("Enchanted Book", "Mending");
        assertFalse(EnchantmentLines.append(lines, "Mending", Collections.<String>emptyList()));
        assertEquals(tooltip("Enchanted Book", "Mending"), lines);
    }
}
