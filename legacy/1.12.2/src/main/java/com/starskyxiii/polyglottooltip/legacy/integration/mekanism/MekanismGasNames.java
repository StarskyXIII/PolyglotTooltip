package com.starskyxiii.polyglottooltip.legacy.integration.mekanism;

import java.util.Collections;
import java.util.List;

import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryNames;

public final class MekanismGasNames {
    static final String GAS_STACK = "mekanism.api.gas.GasStack";

    private MekanismGasNames() {}

    public static boolean isGas(Object ingredient) {
        return isGas(MekanismIntegration.active, ingredient);
    }

    public static List<String> search(Object ingredient) {
        return search(MekanismIntegration.active, ingredient);
    }

    public static SecondaryNames names(IntegrationGuard guard, Object ingredient) {
        if (!isGas(guard, ingredient)) {
            return SecondaryNames.EMPTY;
        }
        return guard.call(() -> MekanismGasTooltip.namesOf(ingredient), SecondaryNames.EMPTY);
    }

    static boolean isGas(IntegrationGuard guard, Object ingredient) {
        return guard != null && guard.active() && ingredient != null && GAS_STACK.equals(ingredient.getClass().getName());
    }

    static List<String> search(IntegrationGuard guard, Object ingredient) {
        if (!isGas(guard, ingredient)) {
            return Collections.emptyList();
        }
        return guard.call(() -> MekanismGasTooltip.searchNames(ingredient), Collections.<String>emptyList());
    }
}
