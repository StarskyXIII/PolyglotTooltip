package com.starskyxiii.polyglottooltip.legacy.integration.mekanism;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;

public class MekanismGasNamesTest {
    private static final Object NOT_A_GAS = new Object();

    @Test
    public void indexesNoGasNamesWhenMekanismIsNotLoaded() {
        assertFalse(MekanismGasNames.isGas(null, NOT_A_GAS));
        assertTrue(MekanismGasNames.search(null, NOT_A_GAS).isEmpty());
        assertTrue(MekanismGasNames.search(NOT_A_GAS).isEmpty());
    }

    @Test
    public void recognisesGasStacksByClassNameOnly() {
        IntegrationGuard guard = new IntegrationGuard("mekanism", (message, error) -> {});
        assertFalse(MekanismGasNames.isGas(guard, NOT_A_GAS));
        assertFalse(MekanismGasNames.isGas(guard, MekanismGasNames.GAS_STACK));
        assertFalse(MekanismGasNames.isGas(guard, null));
        assertTrue(MekanismGasNames.search(guard, NOT_A_GAS).isEmpty());
        assertEquals(0, guard.failures());
    }
}
