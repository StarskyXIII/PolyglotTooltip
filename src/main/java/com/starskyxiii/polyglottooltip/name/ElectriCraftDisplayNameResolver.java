package com.starskyxiii.polyglottooltip.name;

import com.starskyxiii.polyglottooltip.i18n.LanguageCache;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Resolves display names for ElectriCraft ore blocks using {@link LanguageCache} instead of
 * the pre-translated strings stored in ElectriCraft's enum fields.
 *
 * <p>ElectriCraft stores translated display names in {@code ElectriOres.oreName} by calling
 * {@code StatCollector.translateToLocal(key)} at class-initialization time. Since initialization
 * happens at game startup (before any language switch), these fields permanently contain the
 * game's startup language (e.g. zh_CN). No language switch during cache building can fix them.
 *
 * <p>This resolver bypasses those pre-baked strings by mapping the item damage value directly
 * to the raw translation key and resolving it via {@link LanguageCache}, which correctly loads
 * the requested language's lang file.
 *
 * <p>Affected items: {@code ElectriCraft:electricraft_block_ore} damages 0–5.
 */
final class ElectriCraftDisplayNameResolver {

    private static final String BLOCK_ORE_REGISTRY = "ElectriCraft:electricraft_block_ore";

    /**
     * Translation keys for {@code electricraft_block_ore} indexed by item damage.
     * Order matches {@code ElectriOres} enum ordinals (source: ElectriOres.java lines 39–44).
     */
    private static final String[] ORE_KEYS = {
        "ore.copper",   // 0 COPPER
        "ore.tin",      // 1 TIN
        "ore.silver",   // 2 SILVER
        "ore.nickel",   // 3 NICKEL
        "ore.aluminum", // 4 ALUMINUM
        "ore.platinum", // 5 PLATINUM
    };

    private ElectriCraftDisplayNameResolver() {}

    /**
     * Attempts to resolve the display name for the given stack in the requested language.
     *
     * @return the localised ore name, or {@code null} if the stack is not an affected
     *         ElectriCraft item or resolution fails.
     */
    static String tryResolveDisplayName(ItemStack stack, String languageCode) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        Object registryNameObj = Item.itemRegistry.getNameForObject(stack.getItem());
        if (!BLOCK_ORE_REGISTRY.equals(String.valueOf(registryNameObj))) {
            return null;
        }

        int dmg = stack.getItemDamage();
        if (dmg < 0 || dmg >= ORE_KEYS.length) {
            return null;
        }

        return LanguageCache.translate(languageCode, ORE_KEYS[dmg]);
    }
}
