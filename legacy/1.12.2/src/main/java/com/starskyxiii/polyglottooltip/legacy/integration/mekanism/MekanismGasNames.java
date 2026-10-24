package com.starskyxiii.polyglottooltip.legacy.integration.mekanism;

import java.util.Collections;
import java.util.List;

import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;

public final class MekanismGasNames {
    static final String GAS_STACK = "mekanism.api.gas.GasStack";

    private MekanismGasNames() {}

    public static boolean isGas(Object ingredient) {
        return isGas(MekanismIntegration.active, ingredient);
    }

    public static List<String> search(Object ingredient) {
        return search(MekanismIntegration.active, ingredient);
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
