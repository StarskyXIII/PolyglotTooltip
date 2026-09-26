package com.starskyxiii.polyglottooltip.integration.mekanism;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

public class MekanismTooltipHelperTest {

    @Test
    void resolvesLegacyChemicalStackThroughGetType() {
        Component name = Component.translatable("gas.test.hydrogen");

        assertSame(name, MekanismTooltipHelper.getChemicalNameFromStack(
                new LegacyChemicalStack(new LegacyChemical(name))
        ).orElseThrow());
    }

    @Test
    void resolvesModernChemicalStackThroughGetChemical() {
        Component name = Component.translatable("chemical.test.hydrogen");

        assertSame(name, MekanismTooltipHelper.getChemicalNameFromStack(
                new ModernChemicalStack(new LegacyChemical(name))
        ).orElseThrow());
    }

    public record LegacyChemicalStack(LegacyChemical type) {
        public LegacyChemical getType() {
            return type;
        }
    }

    public record ModernChemicalStack(LegacyChemical chemical) {
        public LegacyChemical getChemical() {
            return chemical;
        }
    }

    public record LegacyChemical(Component textComponent) {
        public Component getTextComponent() {
            return textComponent;
        }
    }
}
