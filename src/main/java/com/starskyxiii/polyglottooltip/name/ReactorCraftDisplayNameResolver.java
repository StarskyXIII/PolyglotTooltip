package com.starskyxiii.polyglottooltip.name;

import com.starskyxiii.polyglottooltip.i18n.LanguageCache;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Resolves display names for ReactorCraft ore blocks and crafting items using
 * {@link LanguageCache} instead of the pre-translated strings stored in ReactorCraft's enums.
 *
 * <p>ReactorCraft stores translated display names in enum fields ({@code ReactorOres.oreName},
 * {@code CraftingItems.itemName}) by calling {@code StatCollector.translateToLocal(key)} at
 * class-initialization time. Since initialization happens at game startup before any language
 * switch, these fields permanently contain the startup language (e.g. zh_CN). No language
 * switch during cache building can fix them.
 *
 * <p>This resolver maps each item's damage value directly to its raw translation key and
 * resolves it via {@link LanguageCache}, which correctly loads the requested lang file.
 *
 * <p>Affected items:
 * <ul>
 *   <li>{@code ReactorCraft:reactorcraft_block_ore} damages 0–9</li>
 *   <li>{@code ReactorCraft:reactorcraft_item_crafting} damages 0–18</li>
 * </ul>
 */
final class ReactorCraftDisplayNameResolver {

    private static final String BLOCK_ORE_REGISTRY      = "ReactorCraft:reactorcraft_block_ore";
    private static final String ITEM_CRAFTING_REGISTRY  = "ReactorCraft:reactorcraft_item_crafting";

    /**
     * Translation keys for {@code reactorcraft_block_ore} indexed by item damage.
     * Order matches {@code ReactorOres} enum ordinals (source: ReactorOres.java lines 44–53).
     * ENDBLENDE (ordinal 5) shares the pitchblende key — it is a visual variant, not a new ore.
     */
    private static final String[] ORE_KEYS = {
        "ore.fluorite",    // 0 FLUORITE
        "ore.pitchblende", // 1 PITCHBLENDE
        "ore.cadmium",     // 2 CADMIUM
        "ore.indium",      // 3 INDIUM
        "ore.silver",      // 4 SILVER
        "ore.pitchblende", // 5 ENDBLENDE (visual variant of pitchblende)
        "ore.ammonium",    // 6 AMMONIUM
        "ore.calcite",     // 7 CALCITE
        "ore.magnetite",   // 8 MAGNETITE
        "ore.thorium",     // 9 THORIUM
    };

    /**
     * Translation keys for {@code reactorcraft_item_crafting} indexed by item damage.
     * Order matches {@code CraftingItems} enum ordinals (source: CraftingItems.java).
     * Keys follow the pattern {@code "crafting." + enumName.toLowerCase()}.
     */
    private static final String[] CRAFTING_KEYS = {
        "crafting.canister",       //  0 CANISTER
        "crafting.rod",            //  1 ROD
        "crafting.tank",           //  2 TANK
        "crafting.alloy",          //  3 ALLOY
        "crafting.backing",        //  4 BACKING
        "crafting.magnetic",       //  5 MAGNETIC
        "crafting.magnetcore",     //  6 MAGNETCORE
        "crafting.coolant",        //  7 COOLANT
        "crafting.wire",           //  8 WIRE
        "crafting.shield",         //  9 SHIELD
        "crafting.ferroingot",     // 10 FERROINGOT
        "crafting.hysteresis",     // 11 HYSTERESIS
        "crafting.hysteresisring", // 12 HYSTERESISRING
        "crafting.graphite",       // 13 GRAPHITE
        "crafting.udust",          // 14 UDUST
        "crafting.fabric",         // 15 FABRIC
        "crafting.carbideflakes",  // 16 CARBIDEFLAKES
        "crafting.carbide",        // 17 CARBIDE
        "crafting.turbcore",       // 18 TURBCORE
    };

    private ReactorCraftDisplayNameResolver() {}

    /**
     * Attempts to resolve the display name for the given stack in the requested language.
     *
     * @return the localised item name, or {@code null} if the stack is not an affected
     *         ReactorCraft item or resolution fails.
     */
    static String tryResolveDisplayName(ItemStack stack, String languageCode) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        Object registryNameObj = Item.itemRegistry.getNameForObject(stack.getItem());
        String registryName = String.valueOf(registryNameObj);
        int dmg = stack.getItemDamage();

        if (BLOCK_ORE_REGISTRY.equals(registryName)) {
            if (dmg < 0 || dmg >= ORE_KEYS.length) {
                return null;
            }
            return LanguageCache.translate(languageCode, ORE_KEYS[dmg]);
        }

        if (ITEM_CRAFTING_REGISTRY.equals(registryName)) {
            if (dmg < 0 || dmg >= CRAFTING_KEYS.length) {
                return null;
            }
            return LanguageCache.translate(languageCode, CRAFTING_KEYS[dmg]);
        }

        return null;
    }
}
