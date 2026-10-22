package com.starskyxiii.polyglottooltip.legacy.minecraft;

import java.util.Map;

import com.starskyxiii.polyglottooltip.legacy.Config;
import com.starskyxiii.polyglottooltip.legacy.Settings;

import net.minecraft.item.ItemStack;

public final class ItemNames {
    private static final NameCache<SecondaryNames> CACHE = new NameCache<>();

    private ItemNames() {}

    public static SecondaryNames of(ItemStack stack) {
        if (stack == null || stack.func_190926_b() || stack.func_82837_s()) {
            return SecondaryNames.EMPTY;
        }
        Map<String, SecondaryDictionary> dictionaries = SecondaryLanguages.dictionaries();
        if (dictionaries.isEmpty()) {
            return SecondaryNames.EMPTY;
        }
        int nbtHash = stack.func_77942_o() ? stack.func_77978_p().hashCode() : 0;
        NameCache.Key key = new NameCache.Key(
                stack.func_77973_b(), stack.func_77960_j(), nbtHash, SecondaryLanguages.generation());
        SecondaryNames cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        ConsistencyCheck.Result result = ConsistencyCheck.resolve(stack, dictionaries);
        Map<String, String> names = result.handOff()
                ? SpecialItemNameResolvers.resolve(stack, dictionaries)
                : result.names();
        Settings settings = Config.settings();
        SecondaryNames resolved = SecondaryNames.of(result.primary(), settings.displayLanguages(), names,
                settings.alwaysShow(), SecondaryLanguages.currentLanguage());
        CACHE.put(key, resolved);
        return resolved;
    }
}
