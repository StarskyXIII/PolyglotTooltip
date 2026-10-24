package com.starskyxiii.polyglottooltip.legacy.integration.mekanismenergistics;

import java.util.List;

import com.mekeng.github.common.me.data.IAEGasStack;
import com.starskyxiii.polyglottooltip.legacy.integration.IngredientTooltips;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.integration.StorageSearchText;
import com.starskyxiii.polyglottooltip.legacy.integration.mekanism.MekanismGasNames;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;

public final class MekanismEnergisticsGases {
    static final StorageSearchText TEXT = new StorageSearchText();

    private MekanismEnergisticsGases() {}

    public static String searchText(IAEGasStack stack, String primary) {
        return searchText(stack == null ? null : stack.getGasStack(), primary);
    }

    public static String searchText(GasStack stack, String primary) {
        IntegrationGuard guard = MekanismEnergisticsIntegration.active;
        if (guard == null || primary == null || stack == null) {
            return primary;
        }
        return guard.call(() -> TEXT.text(primary, MekanismGasNames.names(guard, stack)), primary);
    }

    public static List<String> withNames(List<String> lines, Gas gas) {
        IntegrationGuard guard = MekanismEnergisticsIntegration.active;
        if (gas == null) {
            return lines;
        }
        return IngredientTooltips.withNames(guard, lines, gas.getLocalizedName(),
                () -> MekanismGasNames.names(guard, new GasStack(gas, 1)));
    }

    public static String message(Gas gas, String name) {
        IntegrationGuard guard = MekanismEnergisticsIntegration.active;
        if (gas == null) {
            return name;
        }
        return IngredientTooltips.message(guard, name, () -> MekanismGasNames.names(guard, new GasStack(gas, 1)));
    }
}
