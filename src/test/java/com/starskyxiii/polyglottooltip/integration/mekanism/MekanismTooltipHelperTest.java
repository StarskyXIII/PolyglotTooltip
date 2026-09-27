package com.starskyxiii.polyglottooltip.integration.mekanism;
//~ minecraft_116_packages minecraft_legacy_components minecraft_118_factories

import com.starskyxiii.polyglottooltip.minecraft.ComponentFactory;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

public class MekanismTooltipHelperTest {

    @Test
    void resolvesLegacyChemicalStackThroughGetType() {
        Component name = ComponentFactory.translatable("gas.test.hydrogen");

        assertSame(name, MekanismTooltipHelper.getChemicalNameFromStack(
                new LegacyChemicalStack(new LegacyChemical(name))
        ).orElseThrow());
    }

    @Test
    void resolvesModernChemicalStackThroughGetChemical() {
        Component name = ComponentFactory.translatable("chemical.test.hydrogen");

        assertSame(name, MekanismTooltipHelper.getChemicalNameFromStack(
                new ModernChemicalStack(new LegacyChemical(name))
        ).orElseThrow());
    }

    public static final class LegacyChemicalStack {
        private final LegacyChemical type;

        public LegacyChemicalStack(LegacyChemical type) {
            this.type = type;
        }

        public LegacyChemical getType() {
            return type;
        }
    }

    public static final class ModernChemicalStack {
        private final LegacyChemical chemical;

        public ModernChemicalStack(LegacyChemical chemical) {
            this.chemical = chemical;
        }

        public LegacyChemical getChemical() {
            return chemical;
        }
    }

    public static final class LegacyChemical {
        private final Component textComponent;

        public LegacyChemical(Component textComponent) {
            this.textComponent = textComponent;
        }

        public Component getTextComponent() {
            return textComponent;
        }
    }
}
