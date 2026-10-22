package com.starskyxiii.polyglottooltip.legacy.minecraft;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

public final class FluidSecondaryNames {
    private FluidSecondaryNames() {}

    @SuppressWarnings("deprecation")
    public static Map<String, String> of(FluidStack stack) {
        Fluid fluid = stack == null ? null : stack.getFluid();
        if (fluid == null) {
            return Collections.emptyMap();
        }
        try {
            String key = fluid.getUnlocalizedName(stack);
            if (key == null) {
                return Collections.emptyMap();
            }
            String actual = TextFormatting.func_110646_a(fluid.getLocalizedName(stack)).trim();
            String expected = TextFormatting.func_110646_a(I18n.func_74838_a(key)).trim();
            return resolve(key, actual, expected, SecondaryLanguages.dictionaries());
        } catch (RuntimeException error) {
            FailureLog.debug("fluid|" + FailureLog.failureKey(fluid, error),
                    "Fluid consistency check failed for " + fluid.getClass().getName(), error);
            return Collections.emptyMap();
        }
    }

    static Map<String, String> resolve(String key, String actual, String expected,
                                       Map<String, SecondaryDictionary> dictionaries) {
        if (key == null || actual == null || !actual.equals(expected)) {
            return Collections.emptyMap();
        }
        Map<String, String> names = new LinkedHashMap<>();
        for (Map.Entry<String, SecondaryDictionary> entry : dictionaries.entrySet()) {
            String name = entry.getValue().get(key);
            if (name != null) {
                names.put(entry.getKey(), name);
            }
        }
        return Collections.unmodifiableMap(names);
    }
}
