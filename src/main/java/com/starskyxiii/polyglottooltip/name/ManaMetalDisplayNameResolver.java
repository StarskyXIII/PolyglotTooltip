package com.starskyxiii.polyglottooltip.name;

import java.lang.reflect.Field;
import java.util.List;

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

    private static String tryResolveAlchemyGemDisplayName(ItemStack stack, String languageCode) {
        if (stack == null
            || stack.getItem() == null
            || !ITEM_ALCHEMY_GEM_CLASS_NAME.equals(stack.getItem().getClass().getName())) {
            return null;
        }

        List<?> gemNames = getListField(stack.getItem(), "OKingot");
        if (gemNames == null || gemNames.isEmpty()) {
            return null;
        }

        int itemDamage = stack.getItemDamage();
        int gemIndex = itemDamage <= 0 ? 0 : itemDamage / 9;
        if (gemIndex < 0 || gemIndex >= gemNames.size()) {
            return null;
        }

        Object gemNameValue = gemNames.get(gemIndex);
        if (!(gemNameValue instanceof String)) {
            return null;
        }

        String gemName = ((String) gemNameValue).trim();
        if (gemName.isEmpty()) {
            return null;
        }

        int qualityIndex = itemDamage % 9;
        if (qualityIndex < 0) {
            qualityIndex += 9;
        }

        String qualityName = LanguageCache.translate(languageCode, "AlchemyGem.type." + qualityIndex);
        String baseName = LanguageCache.translate(languageCode, "item.gem" + gemName + ".name");
        if (qualityName == null || qualityName.isEmpty() || baseName == null || baseName.isEmpty()) {
            return null;
        }

        return qualityName + baseName;
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

    private static List<?> getListField(Object target, String fieldName) {
        Field field = findField(target, fieldName);
        if (field == null) {
            return null;
        }

        try {
            Object value = field.get(target);
            return value instanceof List ? (List<?>) value : null;
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
