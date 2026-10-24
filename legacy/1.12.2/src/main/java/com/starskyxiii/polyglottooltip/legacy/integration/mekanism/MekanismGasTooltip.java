package com.starskyxiii.polyglottooltip.legacy.integration.mekanism;

import java.util.List;

import com.starskyxiii.polyglottooltip.legacy.integration.IngredientTooltips;
import com.starskyxiii.polyglottooltip.legacy.minecraft.FluidSecondaryNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryLanguages;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryNames;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;

public final class MekanismGasTooltip {
    private MekanismGasTooltip() {}

    public static void append(GasStack stack, List<String> lines) {
        IngredientTooltips.append(MekanismIntegration.active, lines, () -> names(stack));
    }

    static List<String> searchNames(Object stack) {
        return namesOf(stack).names();
    }

    static SecondaryNames namesOf(Object stack) {
        return names((GasStack) stack);
    }

    @SuppressWarnings("deprecation")
    static SecondaryNames names(GasStack stack) {
        Gas gas = stack == null ? null : stack.getGas();
        if (gas == null) {
            return SecondaryNames.EMPTY;
        }
        String key = gas.getUnlocalizedName();
        String actual = plain(gas.getLocalizedName());
        String expected = plain(I18n.func_74838_a(key));
        return FluidSecondaryNames.displayed(actual,
                FluidSecondaryNames.resolve(key, actual, expected, SecondaryLanguages.dictionaries()));
    }

    private static String plain(String text) {
        return text == null ? null : TextFormatting.func_110646_a(text).trim();
    }
}
