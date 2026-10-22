package com.starskyxiii.polyglottooltip.legacy.minecraft;

import net.minecraft.item.ItemStack;

public interface SpecialItemNameResolver {
    boolean accepts(ItemStack stack);

    String resolve(ItemStack stack, String languageCode, SecondaryDictionary dictionary);
}
