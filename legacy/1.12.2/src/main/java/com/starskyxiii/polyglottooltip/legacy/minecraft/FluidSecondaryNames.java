package com.starskyxiii.polyglottooltip.legacy.minecraft;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.starskyxiii.polyglottooltip.legacy.Config;
import com.starskyxiii.polyglottooltip.legacy.Settings;

import net.minecraft.block.Block;
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
            String actual = TextFormatting.func_110646_a(fluid.getLocalizedName(stack)).trim();
            Block block = fluid.getBlock();
            String key = consistentKey(actual, Arrays.asList(fluid.getUnlocalizedName(stack),
                    block == null ? null : block.func_149739_a() + ".name"),
                    candidate -> TextFormatting.func_110646_a(I18n.func_74838_a(candidate)).trim());
            return resolve(key, actual, actual, SecondaryLanguages.dictionaries());
        } catch (RuntimeException error) {
            FailureLog.debug("fluid|" + FailureLog.failureKey(fluid, error),
                    "Fluid consistency check failed for " + fluid.getClass().getName(), error);
            return Collections.emptyMap();
        }
    }

    public static SecondaryNames names(FluidStack stack) {
        Map<String, String> names = of(stack);
        return names.isEmpty() ? SecondaryNames.EMPTY : displayed(stack.getLocalizedName(), names);
    }

    public static SecondaryNames displayed(String primary, Map<String, String> namesByLanguage) {
        Settings settings = Config.settings();
        return SecondaryNames.of(primary, settings.displayLanguages(), namesByLanguage, settings.alwaysShow(),
                SecondaryLanguages.currentLanguage());
    }

    static String consistentKey(String actual, List<String> candidates, Function<String, String> translate) {
        for (String candidate : candidates) {
            if (candidate != null && actual != null && actual.equals(translate.apply(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    public static Map<String, String> resolve(String key, String actual, String expected,
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
