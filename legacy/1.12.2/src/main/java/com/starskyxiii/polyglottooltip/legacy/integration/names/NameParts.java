package com.starskyxiii.polyglottooltip.legacy.integration.names;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import com.starskyxiii.polyglottooltip.legacy.integration.generic.FormatNameResolver;
import com.starskyxiii.polyglottooltip.legacy.integration.generic.ReverseIndex;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ConsistencyCheck;
import com.starskyxiii.polyglottooltip.legacy.minecraft.FluidSecondaryNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryDictionary;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SpecialItemNameResolvers;

import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.fluids.FluidStack;

final class NameParts {
    @SuppressWarnings("deprecation")
    static final Function<String, String> PRIMARY = key -> I18n.func_94522_b(key) ? I18n.func_74838_a(key) : null;

    private NameParts() {}

    static String checked(String shown, String primary, Supplier<String> secondary) {
        if (shown == null || primary == null || !normalize(shown).equals(normalize(primary))) {
            return null;
        }
        String name = secondary.get();
        return name == null ? null : normalize(name);
    }

    static String normalize(String text) {
        return ReverseIndex.normalize(text);
    }

    static String format(String format, String... arguments) {
        return format == null ? null : FormatNameResolver.render(format, Arrays.asList(arguments));
    }

    static String replace(String template, String token, String value) {
        return template == null || value == null ? null : template.replace(token, value);
    }

    static String item(ItemStack stack, String languageCode, SecondaryDictionary dictionary) {
        Map<String, SecondaryDictionary> dictionaries = Collections.singletonMap(languageCode, dictionary);
        ConsistencyCheck.Result result = ConsistencyCheck.resolve(stack, dictionaries);
        Map<String, String> names = result.handOff()
                ? SpecialItemNameResolvers.resolve(stack, result, dictionaries)
                : result.names();
        return names.get(languageCode);
    }

    static String fluid(FluidStack stack, String languageCode) {
        return stack == null ? null : FluidSecondaryNames.of(stack).get(languageCode);
    }
}
