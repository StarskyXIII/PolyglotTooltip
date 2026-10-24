package com.starskyxiii.polyglottooltip.legacy.minecraft;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;

public final class KeyedNames {
    private KeyedNames() {}

    @SuppressWarnings("deprecation")
    public static SecondaryNames of(String shown, List<String> keys) {
        if (shown == null) {
            return SecondaryNames.EMPTY;
        }
        return FluidSecondaryNames.displayed(shown, resolve(plain(shown), keys,
                key -> plain(I18n.func_74838_a(key)), SecondaryLanguages.dictionaries()));
    }

    static Map<String, String> resolve(String actual, List<String> keys, Function<String, String> translate,
                                       Map<String, SecondaryDictionary> dictionaries) {
        String key = FluidSecondaryNames.consistentKey(actual, keys, translate);
        return FluidSecondaryNames.resolve(key, actual, actual, dictionaries);
    }

    private static String plain(String text) {
        return text == null ? null : TextFormatting.func_110646_a(text).trim();
    }
}
