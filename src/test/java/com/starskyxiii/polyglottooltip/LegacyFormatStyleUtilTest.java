package com.starskyxiii.polyglottooltip;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyFormatStyleUtilTest {

    @BeforeEach
    void loadDefaults() {
        TestConfigSupport.load(false);
    }

    @AfterEach
    void restoreDefaults() {
        TestConfigSupport.load(false);
    }

    @Test
    void validatorAcceptsOnlyCompleteLegacyCodeSequences() {
        assertTrue(LegacyFormatStyleUtil.isValidLegacyFormatString(""));
        assertTrue(LegacyFormatStyleUtil.isValidLegacyFormatString("  &7&A&l  "));
        assertTrue(LegacyFormatStyleUtil.isValidLegacyFormatString("&k&n&o&r"));

        assertFalse(LegacyFormatStyleUtil.isValidLegacyFormatString(null));
        assertFalse(LegacyFormatStyleUtil.isValidLegacyFormatString(7));
        assertFalse(LegacyFormatStyleUtil.isValidLegacyFormatString("&"));
        assertFalse(LegacyFormatStyleUtil.isValidLegacyFormatString("&x"));
        assertFalse(LegacyFormatStyleUtil.isValidLegacyFormatString("&7 text"));
        assertFalse(LegacyFormatStyleUtil.isValidLegacyFormatString("&7&"));
    }

    @Test
    void parserAppliesColorAndModifiersInOrder() {
        Style style = LegacyFormatStyleUtil.parse(" &a&l&o ");

        assertNotNull(style.getColor());
        assertEquals(ChatFormatting.GREEN.getColor(), style.getColor().getValue());
        assertTrue(style.isBold());
        assertTrue(style.isItalic());
        assertFalse(style.isUnderlined());
        assertFalse(style.isStrikethrough());
        assertFalse(style.isObfuscated());
    }

    @Test
    void resetClearsEarlierColorAndModifiers() {
        assertTrue(LegacyFormatStyleUtil.parse("&a&l&r").isEmpty());

        Style style = LegacyFormatStyleUtil.parse("&a&l&r&o");
        assertEquals(null, style.getColor());
        assertFalse(style.isBold());
        assertTrue(style.isItalic());
    }

    @Test
    void configBackedStylesUseValuesFromLoadedSpec() {
        TestConfigSupport.load(false, "&b&n", "&e&o");

        Style tooltipStyle = LegacyFormatStyleUtil.tooltipSecondaryNameStyle();
        assertNotNull(tooltipStyle.getColor());
        assertEquals(ChatFormatting.AQUA.getColor(), tooltipStyle.getColor().getValue());
        assertTrue(tooltipStyle.isUnderlined());

        Style jadeStyle = LegacyFormatStyleUtil.jadeSecondaryNameStyle();
        assertNotNull(jadeStyle.getColor());
        assertEquals(ChatFormatting.YELLOW.getColor(), jadeStyle.getColor().getValue());
        assertTrue(jadeStyle.isItalic());
    }
}
