package com.starskyxiii.polyglottooltip.legacy.integration.names;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ConsistencyCheck;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryDictionary;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SpecialItemNameResolver;

import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionType;
import net.minecraft.potion.PotionUtils;

public final class PotionNameResolver implements SpecialItemNameResolver {
    private static final Map<String, String> PREFIXES = new HashMap<>();

    static {
        PREFIXES.put("net.minecraft.item.ItemPotion", "potion.effect.");
        PREFIXES.put("net.minecraft.item.ItemSplashPotion", "splash_potion.effect.");
        PREFIXES.put("net.minecraft.item.ItemLingeringPotion", "lingering_potion.effect.");
        PREFIXES.put("net.minecraft.item.ItemTippedArrow", "tipped_arrow.effect.");
    }

    private final IntegrationGuard guard;

    public PotionNameResolver(IntegrationGuard guard) {
        this.guard = guard;
    }

    static String prefix(String itemClass) {
        return PREFIXES.get(itemClass);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return guard.active() && prefix(stack.func_77973_b().getClass().getName()) != null;
    }

    @Override
    public String resolve(ItemStack stack, ConsistencyCheck.Result result, String languageCode,
                          SecondaryDictionary dictionary) {
        return guard.call(() -> {
            PotionType type = PotionUtils.func_185191_c(stack);
            String prefix = prefix(stack.func_77973_b().getClass().getName());
            return type == null || prefix == null ? null
                    : name(type.func_185174_b(prefix), result.primary(), NameParts.PRIMARY, dictionary::get);
        }, null);
    }

    static String name(String key, String primary, Function<String, String> primaryLang,
                       Function<String, String> secondaryLang) {
        return NameParts.checked(primaryLang.apply(key), primary, () -> secondaryLang.apply(key));
    }
}
