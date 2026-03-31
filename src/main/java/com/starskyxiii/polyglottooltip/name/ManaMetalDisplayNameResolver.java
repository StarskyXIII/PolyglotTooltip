package com.starskyxiii.polyglottooltip.name;

import java.lang.reflect.Field;

import com.starskyxiii.polyglottooltip.i18n.LanguageCache;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

final class ManaMetalDisplayNameResolver {

    private static final String MANAMETAL_CLASS_PREFIX = "project.studio.manametalmod.";
    private static final String ITEM_TOOL_ARMOR_SPECIAL_CLASS_NAME =
        "project.studio.manametalmod.items.armor.ItemToolArmorSpecial";
    private static final String ITEM_ALCHEMY_GEM_CLASS_NAME =
        "project.studio.manametalmod.produce.gemcraft.ItemAlchemyGem";
    private static final String MAGIC_ITEM_MEDAL_FX_CLASS_NAME =
        "project.studio.manametalmod.magic.magicItem.MagicItemMedalFX";
    private static final String[] ARMOR_PART_TRANSLATION_KEYS = new String[]{
        "IASPT.0", "IASPT.1", "IASPT.2", "IASPT.3"
    };

    /**
     * English key suffixes for AlchemyGem gem types, indexed by gemIndex (itemDamage / 9).
     * Used to construct the lang key {@code "item.gem<Name>.name"} regardless of what language
     * the {@code OKingot} field was initialised in. The field stores a runtime-translated name
     * (e.g. "钻石" in a Chinese-language game), which cannot be used as a lang key suffix.
     * Order matches the OKingot list order as observed from in-game item variants.
     */
    private static final String[] ALCHEMY_GEM_KEYS = {
        "Diamond", "Emerald", "Nether Quartz", "Lapis Lazuli", "Amber",
        "Amethyst", "Aquamarine", "Citrine", "Iolite", "Garnet",
        "Jade", "Moonstone", "Opal", "Ruby", "Sapphire",
        "Spinel", "Sunstone", "Tanzanite", "Tourmaline", "Zircon",
        "Chrysoberyl", "Turquoise", "Agate", "Jet", "Tiger's Eye",
    };

    private ManaMetalDisplayNameResolver() {}

    static String tryResolveDisplayName(ItemStack stack, String languageCode, int depth) {
        if (!isManaMetalItem(stack)) {
            return null;
        }

        String specialArmorDisplayName = tryResolveSpecialArmorDisplayName(stack, languageCode);
        if (specialArmorDisplayName != null && !specialArmorDisplayName.isEmpty()) {
            return specialArmorDisplayName;
        }

        if (isMagicItemMedal(stack)) {
            String medalDisplayName = tryResolveMagicItemMedalDisplayName(stack, languageCode);
            if (medalDisplayName != null && !medalDisplayName.isEmpty()) {
                return medalDisplayName;
            }

            return DisplayNameResolver.resolveGenericDisplayNameForLanguage(stack, languageCode);
        }

        String numericDisplayName = tryResolveNumericSuffixDisplayName(stack, languageCode);
        if (numericDisplayName != null && !numericDisplayName.isEmpty()) {
            return numericDisplayName;
        }

        String alchemyGemDisplayName = tryResolveAlchemyGemDisplayName(stack, languageCode);
        if (alchemyGemDisplayName != null && !alchemyGemDisplayName.isEmpty()) {
            return alchemyGemDisplayName;
        }

        return DisplayNameResolver.resolveGenericDisplayNameForLanguage(stack, languageCode);
    }

    private static boolean isManaMetalItem(ItemStack stack) {
        return stack != null
            && stack.getItem() != null
            && stack.getItem().getClass().getName().startsWith(MANAMETAL_CLASS_PREFIX);
    }

    private static String tryResolveSpecialArmorDisplayName(ItemStack stack, String languageCode) {
        if (stack == null
            || stack.getItem() == null
            || !ITEM_TOOL_ARMOR_SPECIAL_CLASS_NAME.equals(stack.getItem().getClass().getName())) {
            return null;
        }

        String armorName = getStringField(stack.getItem(), "ArmorName");
        if (armorName == null || armorName.isEmpty()) {
            return null;
        }

        int armorType = getArmorPowerType(stack);
        int armorPart = getArmorPart(stack.getItem());
        if (armorPart < 0 || armorPart >= ARMOR_PART_TRANSLATION_KEYS.length) {
            return null;
        }

        String baseName = LanguageCache.translate(languageCode, "item." + armorName + "_" + armorType);
        String partName = LanguageCache.translate(languageCode, ARMOR_PART_TRANSLATION_KEYS[armorPart]);
        if (baseName == null || baseName.isEmpty() || partName == null || partName.isEmpty()) {
            return null;
        }

        return baseName + partName;
    }

    private static boolean isMagicItemMedal(ItemStack stack) {
        return stack != null
            && stack.getItem() != null
            && MAGIC_ITEM_MEDAL_FX_CLASS_NAME.equals(stack.getItem().getClass().getName());
    }

    private static String tryResolveMagicItemMedalDisplayName(ItemStack stack, String languageCode) {
        if (!isMagicItemMedal(stack)) {
            return null;
        }

        String translationKey = stack.getUnlocalizedName();
        if (translationKey == null || translationKey.isEmpty()) {
            return null;
        }

        return LanguageCache.translate(languageCode, translationKey + ".name");
    }

    private static int getArmorPowerType(ItemStack stack) {
        if (stack != null
            && stack.hasTagCompound()
            && stack.getTagCompound().hasKey("ArmorPowerType", 3)) {
            return stack.getTagCompound().getInteger("ArmorPowerType");
        }

        return 0;
    }

    private static String tryResolveNumericSuffixDisplayName(ItemStack stack, String languageCode) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        NumericSuffixRule rule = getNumericSuffixRule(stack);
        if (rule == null) {
            return null;
        }

        String prefix = LanguageCache.translate(languageCode, rule.translationKey);
        if (prefix == null || prefix.isEmpty()) {
            return null;
        }

        int value = stack.getItemDamage() * rule.multiplier + rule.offset;
        if (value < 0) {
            return null;
        }

        return prefix + value + rule.suffix;
    }

    private static NumericSuffixRule getNumericSuffixRule(ItemStack stack) {
        if (matchesItem(stack, "manametalmod:DoubleEXPReel", "item.DoubleEXPReel")) {
            return new NumericSuffixRule("item.DoubleEXPReelV2.name", 50, 50, "%");
        }

        if (matchesItem(stack, "manametalmod:PG_WTR", "item.PG_WTR")) {
            return new NumericSuffixRule("WTRItemPages", 1, 1, "");
        }

        return null;
    }

    static String tryResolveAlchemyGemDisplayName(ItemStack stack, String languageCode) {
        if (stack == null
            || stack.getItem() == null
            || !ITEM_ALCHEMY_GEM_CLASS_NAME.equals(stack.getItem().getClass().getName())) {
            return null;
        }

        int itemDamage = stack.getItemDamage();
        int gemIndex = itemDamage <= 0 ? 0 : itemDamage / 9;
        if (gemIndex < 0 || gemIndex >= ALCHEMY_GEM_KEYS.length) {
            return null;
        }

        int qualityIndex = itemDamage % 9;
        if (qualityIndex < 0) {
            qualityIndex += 9;
        }

        String qualityName = LanguageCache.translate(languageCode, "AlchemyGem.type." + qualityIndex);
        if (qualityName == null || qualityName.isEmpty()) {
            return null;
        }

        // OKingot stores a runtime-translated gem name (e.g. "钻石" in Chinese), so it cannot
        // be used directly as a lang-key suffix. Use the stable English name from ALCHEMY_GEM_KEYS
        // to construct the key, then fall back to the English name itself for en_US if the key
        // is absent from the lang file.
        String gemKeyBase = ALCHEMY_GEM_KEYS[gemIndex];
        String baseName = LanguageCache.translate(languageCode, "item.gem" + gemKeyBase + ".name");
        if (baseName == null || baseName.isEmpty()) {
            if (!"en_US".equalsIgnoreCase(languageCode)) {
                return null;
            }
            baseName = gemKeyBase;
        }

        return qualityName + " " + baseName;
    }

    private static int getArmorPart(Object item) {
        Integer value = getIntField(item, "field_77881_a");
        if (value != null) {
            return value.intValue();
        }

        value = getIntField(item, "armorType");
        return value == null ? -1 : value.intValue();
    }

    private static boolean matchesItem(ItemStack stack, String registryName, String unlocalizedName) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }

        String currentRegistryName = getRegistryName(stack);
        if (registryName != null && registryName.equals(currentRegistryName)) {
            return true;
        }

        String currentUnlocalizedName = stack.getUnlocalizedName();
        return unlocalizedName != null && unlocalizedName.equals(currentUnlocalizedName);
    }

    private static String getRegistryName(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        Object name = Item.itemRegistry.getNameForObject(stack.getItem());
        return name == null ? null : String.valueOf(name);
    }

    private static Integer getIntField(Object target, String fieldName) {
        Field field = findField(target, fieldName);
        if (field == null) {
            return null;
        }

        try {
            return Integer.valueOf(field.getInt(target));
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static String getStringField(Object target, String fieldName) {
        Field field = findField(target, fieldName);
        if (field == null) {
            return null;
        }

        try {
            Object value = field.get(target);
            return value instanceof String ? (String) value : null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static Field findField(Object target, String fieldName) {
        if (target == null || fieldName == null || fieldName.isEmpty()) {
            return null;
        }

        Class<?> currentClass = target.getClass();
        while (currentClass != null) {
            try {
                Field field = currentClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                currentClass = currentClass.getSuperclass();
            }
        }

        return null;
    }

    private static final class NumericSuffixRule {

        private final String translationKey;
        private final int multiplier;
        private final int offset;
        private final String suffix;

        private NumericSuffixRule(String translationKey, int multiplier, int offset, String suffix) {
            this.translationKey = translationKey;
            this.multiplier = multiplier;
            this.offset = offset;
            this.suffix = suffix == null ? "" : suffix;
        }
    }
}
