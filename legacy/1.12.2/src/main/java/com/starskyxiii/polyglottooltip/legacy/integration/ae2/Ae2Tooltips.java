package com.starskyxiii.polyglottooltip.legacy.integration.ae2;

import java.util.Arrays;
import java.util.List;

import com.starskyxiii.polyglottooltip.legacy.integration.IngredientTooltips;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.minecraft.FluidSecondaryNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.KeyedNames;
import com.starskyxiii.polyglottooltip.legacy.mixin.ae2.Ae2LabelKeyAccessor;

import net.minecraftforge.fluids.FluidStack;

public final class Ae2Tooltips {
    private Ae2Tooltips() {}

    public static List<String> fluidLines(List<String> lines, FluidStack stack) {
        if (stack == null || stack.getFluid() == null) {
            return lines;
        }
        return IngredientTooltips.withNames(Ae2Integration.active, lines, stack.getLocalizedName(),
                () -> FluidSecondaryNames.names(stack));
    }

    public static String fluidMessage(FluidStack stack, String name) {
        if (stack == null || stack.getFluid() == null) {
            return name;
        }
        return IngredientTooltips.message(Ae2Integration.active, name, () -> FluidSecondaryNames.names(stack));
    }

    public static String labelSearchText(Object entry, String name) {
        IntegrationGuard guard = Ae2Integration.active;
        if (guard == null || name == null || !(entry instanceof Ae2LabelKeyAccessor)) {
            return name;
        }
        return guard.call(() -> {
            String key = ((Ae2LabelKeyAccessor) entry).polyglot$unlocalizedName();
            return key == null ? name : Ae2Search.TEXT.text(name, KeyedNames.of(name, Arrays.asList(key + ".name", key)));
        }, name);
    }
}
