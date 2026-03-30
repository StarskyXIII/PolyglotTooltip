package com.starskyxiii.polyglottooltip.name;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.starskyxiii.polyglottooltip.i18n.LanguageCache;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

final class GregTechBeeProductDisplayNameResolver {

    private static final String ITEM_COMB_CLASS_NAME = "gregtech.common.items.ItemComb";
    private static final String ITEM_PROPOLIS_CLASS_NAME = "gregtech.common.items.ItemPropolis";
    private static final String ITEM_DROP_CLASS_NAME = "gregtech.common.items.ItemDrop";

    private static final String COMB_ENUM_CLASS_NAME = "gregtech.common.items.CombType";
    private static final String PROPOLIS_ENUM_CLASS_NAME = "gregtech.common.items.PropolisType";
    private static final String DROP_ENUM_CLASS_NAME = "gregtech.common.items.DropType";

    private static final String COMB_UNLOCALIZED_NAME = "gt.comb";
    private static final String PROPOLIS_UNLOCALIZED_NAME = "gt.propolis";
    private static final String DROP_UNLOCALIZED_NAME = "gt.drop";

    private static Class<?> itemCombClass;
    private static Class<?> itemPropolisClass;
    private static Class<?> itemDropClass;
    private static boolean itemClassReflectionInitialized;

    private static Field enumNameField;
    private static Class<?> enumNameFieldOwner;

    private GregTechBeeProductDisplayNameResolver() {}

    static String tryResolveDisplayName(ItemStack stack, String languageCode) {
        if (stack == null || stack.getItem() == null || languageCode == null || languageCode.trim().isEmpty()) {
            return null;
        }

        String translationKey = resolveTranslationKey(stack);
        if (translationKey == null || translationKey.isEmpty()) {
            return null;
        }

        String translated = LanguageCache.translate(languageCode.trim(), translationKey);
        if (translated == null || translated.trim().isEmpty()) {
            return null;
        }

        return translated.trim();
    }

    static String debugResolveTranslationKey(ItemStack stack) {
        return stack == null ? null : resolveTranslationKey(stack);
    }

    private static String resolveTranslationKey(ItemStack stack) {
        initializeItemClassReflection();
        Object item = stack.getItem();
        String unlocalizedName = normalizeUnlocalizedName(stack.getUnlocalizedName());
        int metadata = stack.getItemDamage();

        if (isItemInstance(item, itemCombClass, ITEM_COMB_CLASS_NAME) || COMB_UNLOCALIZED_NAME.equals(unlocalizedName)) {
            return buildTranslationKey(COMB_ENUM_CLASS_NAME, metadata, "comb.", stack, "comb.");
        }

        if (isItemInstance(item, itemPropolisClass, ITEM_PROPOLIS_CLASS_NAME)
            || PROPOLIS_UNLOCALIZED_NAME.equals(unlocalizedName)) {
            return buildTranslationKey(PROPOLIS_ENUM_CLASS_NAME, metadata, "propolis.", stack, "propolis.");
        }

        if (isItemInstance(item, itemDropClass, ITEM_DROP_CLASS_NAME) || DROP_UNLOCALIZED_NAME.equals(unlocalizedName)) {
            return buildTranslationKey(DROP_ENUM_CLASS_NAME, metadata, "drop.", stack, "drop.");
        }

        return null;
    }

    private static boolean isItemInstance(Object item, Class<?> expectedClass, String fallbackClassName) {
        if (item == null) {
            return false;
        }

        if (expectedClass != null && expectedClass.isInstance(item)) {
            return true;
        }

        String runtimeClassName = item.getClass()
            .getName();
        return fallbackClassName.equals(runtimeClassName) || runtimeClassName.endsWith('.' + fallbackClassName.substring(fallbackClassName.lastIndexOf('.') + 1));
    }

    private static String normalizeUnlocalizedName(String unlocalizedName) {
        if (unlocalizedName == null) {
            return null;
        }

        String normalized = unlocalizedName.trim();
        if (normalized.startsWith("item.")) {
            normalized = normalized.substring(5);
        } else if (normalized.startsWith("tile.")) {
            normalized = normalized.substring(5);
        }

        return normalized;
    }

    private static String buildTranslationKey(String enumClassName, int metadata, String prefix, ItemStack stack,
        String currentLanguagePrefix) {
        Object enumConstant = getEnumConstant(enumClassName, metadata);
        if (enumConstant != null) {
            String suffix = getEnumTranslationSuffix(enumConstant);
            if (suffix != null && !suffix.isEmpty()) {
                return prefix + suffix;
            }
        }

        return findTranslationKeyFromCurrentDisplayName(stack, currentLanguagePrefix);
    }

    private static Object getEnumConstant(String enumClassName, int metadata) {
        if (metadata < 0) {
            return null;
        }

        try {
            Class<?> enumClass = Class.forName(enumClassName);
            try {
                Method valueOfMethod = enumClass.getMethod("valueOf", int.class);
                Object value = valueOfMethod.invoke(null, metadata);
                if (value != null) {
                    return value;
                }
            } catch (Exception ignored) {
                // Fall through to raw enum constant lookup.
            }

            Object constants = enumClass.getEnumConstants();
            if (constants == null || !constants.getClass().isArray()) {
                return null;
            }

            int length = Array.getLength(constants);
            if (metadata >= length) {
                return null;
            }

            return Array.get(constants, metadata);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getEnumTranslationSuffix(Object enumConstant) {
        if (enumConstant == null) {
            return null;
        }

        initializeReflection(enumConstant.getClass());
        if (enumNameField == null || !enumNameField.getDeclaringClass().isAssignableFrom(enumConstant.getClass())) {
            return null;
        }

        try {
            Object value = enumNameField.get(enumConstant);
            return value instanceof String ? ((String) value).trim() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static synchronized void initializeReflection(Class<?> enumClass) {
        if (enumClass == null) {
            return;
        }

        if (enumNameField != null && enumNameFieldOwner == enumClass) {
            return;
        }

        try {
            enumNameField = enumClass.getDeclaredField("name");
            enumNameField.setAccessible(true);
            enumNameFieldOwner = enumClass;
        } catch (Exception ignored) {
            enumNameField = null;
            enumNameFieldOwner = null;
        }
    }

    private static synchronized void initializeItemClassReflection() {
        if (itemClassReflectionInitialized) {
            return;
        }

        itemClassReflectionInitialized = true;
        itemCombClass = tryLoadClass(ITEM_COMB_CLASS_NAME);
        itemPropolisClass = tryLoadClass(ITEM_PROPOLIS_CLASS_NAME);
        itemDropClass = tryLoadClass(ITEM_DROP_CLASS_NAME);
    }

    private static Class<?> tryLoadClass(String className) {
        try {
            return Class.forName(className);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String findTranslationKeyFromCurrentDisplayName(ItemStack stack, String requiredKeyPrefix) {
        if (stack == null || stack.getItem() == null || requiredKeyPrefix == null || requiredKeyPrefix.isEmpty()) {
            return null;
        }

        String displayName = stack.getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            return null;
        }

        displayName = EnumChatFormatting.getTextWithoutFormattingCodes(displayName);
        if (displayName == null) {
            return null;
        }

        displayName = displayName.trim();
        if (displayName.isEmpty()) {
            return null;
        }

        return LanguageCache.findCurrentLanguageTranslationKey(displayName, requiredKeyPrefix);
    }
}
