package com.starskyxiii.polyglottooltip.legacy.integration.generic;

import java.util.function.Supplier;

import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ConsistencyCheck;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ResolvedName;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryDictionary;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SpecialItemNameResolver;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;

public final class BareKeyResolver implements SpecialItemNameResolver {
    private final IntegrationGuard guard;

    public BareKeyResolver(IntegrationGuard guard) {
        this.guard = guard;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return guard.active();
    }

    @Override
    public String resolve(ItemStack stack, ConsistencyCheck.Result result, String languageCode,
                          SecondaryDictionary dictionary) {
        ResolvedName name = resolveName(stack, result, languageCode, dictionary);
        return name == null ? null : name.name();
    }

    @Override
    public ResolvedName resolveName(ItemStack stack, ConsistencyCheck.Result result, String languageCode,
                                    SecondaryDictionary dictionary) {
        return guard.call(() -> resolveNow(stack, result, dictionary), null);
    }

    @SuppressWarnings("deprecation")
    private static ResolvedName resolveNow(ItemStack stack, ConsistencyCheck.Result result,
                                           SecondaryDictionary dictionary) {
        Item item = stack.func_77973_b();
        String bare = item == null ? null : item.func_77667_c(stack);
        String value = bare == null ? null : dictionary.get(bare);
        String actual = result.primary();
        if (value == null || actual == null) {
            return null;
        }
        String expected = TextFormatting.func_110646_a(I18n.func_74838_a(bare)).trim();
        return named(expected, actual, value,
                () -> CompositeNameResolver.around(stack, result.key(), expected, actual, value, dictionary));
    }

    static ResolvedName named(String expected, String actual, String value, Supplier<ResolvedName> composite) {
        if (expected.isEmpty()) {
            return null;
        }
        return expected.equals(actual) ? ResolvedName.of(value) : composite.get();
    }
}
