package com.starskyxiii.polyglottooltip.name;

import java.util.List;

import com.starskyxiii.polyglottooltip.i18n.GregTechMaterialTranslationResolver;

import net.minecraft.item.ItemStack;

public final class GregTechDisplayNameDebugBridge {

    private GregTechDisplayNameDebugBridge() {}

    public static String describe(ItemStack stack, String languageCode) {
        return GregTechDisplayNameResolver.debugDescribe(stack, languageCode);
    }

    public static List<String> collectOreDictionaryNames(ItemStack stack) {
        return GregTechMaterialTranslationResolver.collectOreDictionaryNames(stack);
    }

    public static String describeMaterial(ItemStack stack, String languageCode) {
        String translationKey = GregTechDisplayNameResolver.debugGenericTranslationKey(stack);
        return GregTechMaterialTranslationResolver.describeMaterialResolution(languageCode, translationKey, stack);
    }
}
