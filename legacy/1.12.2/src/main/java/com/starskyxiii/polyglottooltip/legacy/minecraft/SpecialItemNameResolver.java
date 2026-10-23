package com.starskyxiii.polyglottooltip.legacy.minecraft;

import net.minecraft.item.ItemStack;

public interface SpecialItemNameResolver {
    boolean accepts(ItemStack stack);

    String resolve(ItemStack stack, ConsistencyCheck.Result result, String languageCode,
                   SecondaryDictionary dictionary);

    default ResolvedName resolveName(ItemStack stack, ConsistencyCheck.Result result, String languageCode,
                                     SecondaryDictionary dictionary) {
        return ResolvedName.of(resolve(stack, result, languageCode, dictionary));
    }
}
