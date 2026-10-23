package com.starskyxiii.polyglottooltip.legacy.integration.names;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import com.starskyxiii.polyglottooltip.legacy.integration.generic.FormatNameResolver;
import com.starskyxiii.polyglottooltip.legacy.integration.generic.ReverseIndex;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ConsistencyCheck;
import com.starskyxiii.polyglottooltip.legacy.minecraft.FluidSecondaryNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ResolvedName;
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
        ResolvedName name = itemName(stack, languageCode, dictionary);
        return name == null ? null : name.name();
    }

    static ResolvedName itemName(ItemStack stack, String languageCode, SecondaryDictionary dictionary) {
        Map<String, SecondaryDictionary> dictionaries = Collections.singletonMap(languageCode, dictionary);
        ConsistencyCheck.Result result = ConsistencyCheck.resolve(stack, dictionaries);
        if (!result.handOff()) {
            return ResolvedName.of(result.names().get(languageCode));
        }
        SpecialItemNameResolvers.Resolution resolution = SpecialItemNameResolvers.resolution(stack, result, dictionaries);
        return ResolvedName.of(resolution.names().get(languageCode), resolution.untranslated().get(languageCode));
    }

    static Function<String, String> reverse(SecondaryDictionary dictionary) {
        return text -> ReverseIndex.current().translate(text, dictionary);
    }

    static String orPrimary(String primary, ResolvedName secondary, Function<String, String> reverse,
                            List<String> untranslated) {
        if (secondary != null) {
            untranslated.addAll(secondary.untranslated());
            return secondary.name();
        }
        String reversed = reverse.apply(primary);
        if (reversed != null) {
            return reversed;
        }
        if (primary.codePoints().anyMatch(Character::isLetter)) {
            untranslated.add(primary);
        }
        return primary;
    }

    static final class Joined {
        private final Function<String, String> reverse;
        private final StringBuilder primary = new StringBuilder();
        private final StringBuilder secondary = new StringBuilder();
        private final List<String> untranslated = new ArrayList<>();
        private boolean missing;

        Joined(Function<String, String> reverse) {
            this.reverse = reverse;
        }

        Joined text(String text) {
            primary.append(text);
            secondary.append(text);
            return this;
        }

        Joined part(String primaryPart, ResolvedName secondaryPart) {
            if (primaryPart == null) {
                missing = true;
                return this;
            }
            String shown = normalize(primaryPart);
            primary.append(shown);
            secondary.append(orPrimary(shown, secondaryPart, reverse, untranslated));
            return this;
        }

        ResolvedName checked(String shown) {
            if (missing || shown == null || !normalize(primary.toString()).equals(normalize(shown))) {
                return null;
            }
            return ResolvedName.of(secondary.toString(), untranslated);
        }
    }

    static String fluid(FluidStack stack, String languageCode) {
        return stack == null ? null : FluidSecondaryNames.of(stack).get(languageCode);
    }
}
