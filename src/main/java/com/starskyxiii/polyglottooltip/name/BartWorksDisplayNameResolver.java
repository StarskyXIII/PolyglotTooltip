package com.starskyxiii.polyglottooltip.name;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.starskyxiii.polyglottooltip.i18n.LanguageCache;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

final class BartWorksDisplayNameResolver {

    private static final String BARTWORKS_PACKAGE_PREFIX = "bartworks.";
    private static final String BW_META_GENERATED_ITEMS_CLASS_NAME = "bartworks.system.material.BWMetaGeneratedItems";
    private static final String BW_ITEM_META_GENERATED_BLOCK_CLASS_NAME =
        "bartworks.system.material.BWItemMetaGeneratedBlock";
    private static final String BW_ITEM_META_GENERATED_ORE_CLASS_NAME = "bartworks.system.material.BWItemMetaGeneratedOre";
    private static final String ORE_PREFIXES_CLASS_NAME = "gregtech.api.enums.OrePrefixes";
    private static final String WERKSTOFF_CLASS_NAME = "bartworks.system.material.Werkstoff";
    private static final String BW_WERKSTOFF_BLOCK_CASING_UNLOCALIZED_PREFIX = "bw.werkstoffblockscasing.01.";
    private static final String BW_WERKSTOFF_BLOCK_CASING_ADVANCED_UNLOCALIZED_PREFIX =
        "bw.werkstoffblockscasingadvanced.01.";

    private BartWorksDisplayNameResolver() {}

    static String tryResolveDisplayName(ItemStack stack, String languageCode) {
        if (stack == null || stack.getItem() == null || languageCode == null || languageCode.trim().isEmpty()) {
            return null;
        }

        if (!isBartWorksStack(stack)) {
            return null;
        }

        Object orePrefix = resolveOrePrefix(stack);
        if (orePrefix == null) {
            return tryResolveFromOreDictionary(stack, languageCode);
        }

        Object werkstoff = resolveWerkstoff(stack.getItemDamage());
        if (werkstoff == null) {
            return tryResolveFromOreDictionary(stack, languageCode);
        }

        Object materialForFormat = resolveMaterialForFormatting(werkstoff);
        String prefixKey = resolvePrefixTranslationKey(orePrefix, materialForFormat);
        if (prefixKey == null || prefixKey.isEmpty()) {
            prefixKey = resolvePrefixTranslationKey(orePrefix, werkstoff);
        }

        String materialName = resolveMaterialDisplayName(languageCode, materialForFormat);
        if (materialName == null || materialName.isEmpty()) {
            materialName = resolveMaterialDisplayName(languageCode, werkstoff);
        }

        String resolved = formatDisplayName(languageCode, orePrefix, prefixKey, materialForFormat, materialName);
        if (resolved != null && !resolved.isEmpty()) {
            return resolved.trim();
        }

        return tryResolveFromOreDictionary(stack, languageCode);
    }

    static String tryResolveGeneratedCasingDisplayName(ItemStack stack, String languageCode) {
        if (stack == null || stack.getItem() == null || languageCode == null || languageCode.trim().isEmpty()) {
            return null;
        }

        if (!BW_ITEM_META_GENERATED_BLOCK_CLASS_NAME.equals(stack.getItem().getClass().getName())) {
            return null;
        }

        String unlocalizedName = stack.getUnlocalizedName();
        if (unlocalizedName == null || unlocalizedName.trim().isEmpty()) {
            return null;
        }

        if (unlocalizedName.startsWith(BW_WERKSTOFF_BLOCK_CASING_UNLOCALIZED_PREFIX)) {
            return tryResolveDisplayNameWithStaticOrePrefix(stack, languageCode, "blockCasing");
        }

        if (unlocalizedName.startsWith(BW_WERKSTOFF_BLOCK_CASING_ADVANCED_UNLOCALIZED_PREFIX)) {
            return tryResolveDisplayNameWithStaticOrePrefix(stack, languageCode, "blockCasingAdvanced");
        }

        return null;
    }

    private static Object resolveOrePrefix(ItemStack stack) {
        ResolvedOrePrefix resolved = resolveOrePrefixResult(stack);
        return resolved == null ? null : resolved.value;
    }

    private static ResolvedOrePrefix resolveOrePrefixResult(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        String itemClassName = stack.getItem().getClass().getName();
        if (BW_META_GENERATED_ITEMS_CLASS_NAME.equals(itemClassName)) {
            Object itemPrefix = getFieldValue(stack.getItem(), "orePrefixes");
            if (itemPrefix != null) {
                return new ResolvedOrePrefix(itemPrefix);
            }
        }

        if (BW_ITEM_META_GENERATED_ORE_CLASS_NAME.equals(itemClassName)) {
            Object blockOre = getFieldValue(stack.getItem(), "blockOre");
            Object orePrefix = invokeNoArg(blockOre, "getPrefix");
            if (orePrefix != null) {
                return new ResolvedOrePrefix(orePrefix);
            }

            orePrefix = invokeDeclaredNoArg(stack.getItem(), "getOrePrefix");
            if (orePrefix != null) {
                return new ResolvedOrePrefix(orePrefix);
            }
        }

        Object itemPrefix = getFieldValue(stack.getItem(), "orePrefixes");
        if (itemPrefix != null) {
            return new ResolvedOrePrefix(itemPrefix);
        }

        Object blockOre = getFieldValue(stack.getItem(), "blockOre");
        Object orePrefix = invokeNoArg(blockOre, "getPrefix");
        if (orePrefix != null) {
            return new ResolvedOrePrefix(orePrefix);
        }

        orePrefix = invokeDeclaredNoArg(stack.getItem(), "getOrePrefix");
        if (orePrefix != null) {
            return new ResolvedOrePrefix(orePrefix);
        }

        Block block = Block.getBlockFromItem(stack.getItem());
        if (block == null) {
            return null;
        }

        Object blockPrefix = getFieldValue(block, "prefix");
        if (blockPrefix != null) {
            return new ResolvedOrePrefix(blockPrefix);
        }

        return null;
    }

    private static String tryResolveDisplayNameWithStaticOrePrefix(ItemStack stack, String languageCode, String fieldName) {
        Object orePrefix = getStaticFieldValue(ORE_PREFIXES_CLASS_NAME, fieldName);
        if (orePrefix == null) {
            return null;
        }

        Object werkstoff = resolveWerkstoff(stack.getItemDamage());
        if (werkstoff == null) {
            return null;
        }

        Object materialForFormat = resolveMaterialForFormatting(werkstoff);
        String prefixKey = resolvePrefixTranslationKey(orePrefix, materialForFormat);
        if (prefixKey == null || prefixKey.isEmpty()) {
            prefixKey = resolvePrefixTranslationKey(orePrefix, werkstoff);
        }

        String materialName = resolveMaterialDisplayName(languageCode, materialForFormat);
        if (materialName == null || materialName.isEmpty()) {
            materialName = resolveMaterialDisplayName(languageCode, werkstoff);
        }

        String resolved = formatDisplayName(languageCode, orePrefix, prefixKey, materialForFormat, materialName);
        if ((resolved == null || resolved.isEmpty()) && materialName != null && !materialName.isEmpty()) {
            resolved = formatDisplayName(languageCode, orePrefix, prefixKey, werkstoff, materialName);
        }

        return resolved == null || resolved.trim().isEmpty() ? null : resolved.trim();
    }

    private static Object resolveWerkstoff(int metadata) {
        try {
            Class<?> werkstoffClass = Class.forName(WERKSTOFF_CLASS_NAME);
            Field mapField = werkstoffClass.getField("werkstoffHashMap");
            Object werkstoffMap = mapField.get(null);
            Object resolved = invokeSingleArg(werkstoffMap, "get", Short.valueOf((short) metadata));
            if (resolved != null) {
                return resolved;
            }

            Object defaultWerkstoff = getStaticFieldValue(werkstoffClass, "default_null_Werkstoff");
            return defaultWerkstoff;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object resolveMaterialForFormatting(Object werkstoff) {
        Object bridgeMaterial = invokeNoArg(werkstoff, "getBridgeMaterial");
        return bridgeMaterial != null ? bridgeMaterial : werkstoff;
    }

    private static String resolvePrefixTranslationKey(Object orePrefix, Object material) {
        if (orePrefix == null || material == null) {
            return null;
        }

        Object resolved = invokeSingleArg(orePrefix, "getOreprefixKey", material);
        return resolved instanceof String && !((String) resolved).trim().isEmpty() ? ((String) resolved).trim() : null;
    }

    private static String resolveMaterialDisplayName(String languageCode, Object material) {
        if (material == null) {
            return null;
        }

        String translated = translateLocalizedNameKey(languageCode, material);
        if (translated != null && !translated.isEmpty()) {
            return translated;
        }

        Object defaultName = invokeNoArg(material, "getDefaultName");
        if (defaultName instanceof String && !((String) defaultName).trim().isEmpty()) {
            return ((String) defaultName).trim();
        }

        return null;
    }

    private static String translateLocalizedNameKey(String languageCode, Object material) {
        Object localizedNameKey = invokeNoArg(material, "getLocalizedNameKey");
        if (!(localizedNameKey instanceof String) || ((String) localizedNameKey).trim().isEmpty()) {
            return null;
        }

        String translated = LanguageCache.translate(languageCode, (String) localizedNameKey);
        return translated == null || translated.trim().isEmpty() ? null : translated.trim();
    }

    private static String formatDisplayName(String languageCode, Object orePrefix, String prefixKey, Object material,
        String materialName) {
        if (materialName == null || materialName.isEmpty()) {
            return null;
        }

        if (prefixKey != null && !prefixKey.isEmpty()) {
            String formatted = LanguageCache.format(languageCode, prefixKey, materialName);
            if (formatted != null && !formatted.trim().isEmpty()) {
                return formatted.trim();
            }

            String template = LanguageCache.translate(languageCode, prefixKey);
            String resolvedTemplate = applyMaterialToTemplate(template, materialName);
            if (resolvedTemplate != null && !resolvedTemplate.isEmpty()) {
                return resolvedTemplate;
            }
        }

        Object defaultFormat = invokeSingleArg(orePrefix, "getDefaultLocalNameFormatForItem", material);
        if (defaultFormat instanceof String) {
            return applyMaterialToTemplate((String) defaultFormat, materialName);
        }

        return null;
    }

    private static String applyMaterialToTemplate(String template, String materialName) {
        if (template == null || template.trim().isEmpty() || materialName == null || materialName.trim().isEmpty()) {
            return null;
        }

        String normalizedTemplate = template.trim();
        if (normalizedTemplate.contains("%material")) {
            return normalizedTemplate.replace("%material", materialName).trim();
        }

        if (normalizedTemplate.contains("%s")) {
            try {
                return String.format(normalizedTemplate, materialName).trim();
            } catch (Exception ignored) {
                // Fall through to the plain template below.
            }
        }

        return normalizedTemplate;
    }

    private static String tryResolveFromOreDictionary(ItemStack stack, String languageCode) {
        if (!isBartWorksStack(stack)) {
            return null;
        }

        Object werkstoff = resolveWerkstoff(stack.getItemDamage());
        if (werkstoff == null) {
            return null;
        }

        String materialVarName = resolveMaterialVarName(werkstoff);
        if (materialVarName == null || materialVarName.isEmpty()) {
            return null;
        }

        Object materialForFormat = resolveMaterialForFormatting(werkstoff);
        String materialName = resolveMaterialDisplayName(languageCode, materialForFormat);
        if (materialName == null || materialName.isEmpty()) {
            materialName = resolveMaterialDisplayName(languageCode, werkstoff);
        }
        if (materialName == null || materialName.isEmpty()) {
            return null;
        }

        for (String oreDictionaryName : collectOreDictionaryNames(stack)) {
            Object orePrefix = resolveOrePrefixFromOreDictionaryName(oreDictionaryName, materialVarName);
            if (orePrefix == null) {
                continue;
            }

            String prefixKey = resolvePrefixTranslationKey(orePrefix, materialForFormat);
            if (prefixKey == null || prefixKey.isEmpty()) {
                prefixKey = resolvePrefixTranslationKey(orePrefix, werkstoff);
            }

            String resolved = formatDisplayName(languageCode, orePrefix, prefixKey, materialForFormat, materialName);
            if (resolved == null || resolved.isEmpty()) {
                resolved = formatDisplayName(languageCode, orePrefix, prefixKey, werkstoff, materialName);
            }

            if (resolved != null && !resolved.isEmpty()) {
                return resolved.trim();
            }
        }

        return null;
    }

    private static List<String> collectOreDictionaryNames(ItemStack stack) {
        ArrayList<String> names = new ArrayList<String>();
        if (stack == null || stack.getItem() == null) {
            return names;
        }

        int[] oreIds = OreDictionary.getOreIDs(stack);
        if (oreIds == null || oreIds.length == 0) {
            return names;
        }

        LinkedHashSet<String> deduped = new LinkedHashSet<String>();
        for (int oreId : oreIds) {
            if (oreId < 0) {
                continue;
            }

            String oreName = OreDictionary.getOreName(oreId);
            if (oreName != null && !oreName.trim().isEmpty()) {
                deduped.add(oreName.trim());
            }
        }

        names.addAll(deduped);
        return names;
    }

    private static Object resolveOrePrefixFromOreDictionaryName(String oreDictionaryName, String materialVarName) {
        if (oreDictionaryName == null || materialVarName == null) {
            return null;
        }

        String trimmedOreDictionaryName = oreDictionaryName.trim();
        String trimmedMaterialVarName = materialVarName.trim();
        if (trimmedOreDictionaryName.isEmpty()
            || trimmedMaterialVarName.isEmpty()
            || !trimmedOreDictionaryName.endsWith(trimmedMaterialVarName)
            || trimmedOreDictionaryName.length() <= trimmedMaterialVarName.length()) {
            return null;
        }

        String prefixName =
            trimmedOreDictionaryName.substring(0, trimmedOreDictionaryName.length() - trimmedMaterialVarName.length())
                .trim();
        if (prefixName.isEmpty()) {
            return null;
        }

        Object resolved = invokeStatic(
            ORE_PREFIXES_CLASS_NAME,
            "getPrefix",
            new Class<?>[] { String.class },
            prefixName);
        if (resolved != null) {
            return resolved;
        }

        return getStaticFieldValue(ORE_PREFIXES_CLASS_NAME, prefixName);
    }

    private static String resolveMaterialVarName(Object material) {
        Object value = invokeNoArg(material, "getVarName");
        if (value instanceof String && !((String) value).trim().isEmpty()) {
            return ((String) value).trim();
        }

        value = invokeNoArg(material, "getInternalName");
        if (value instanceof String && !((String) value).trim().isEmpty()) {
            return ((String) value).trim();
        }

        return null;
    }

    private static boolean isBartWorksStack(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }

        String itemClassName = stack.getItem().getClass().getName();
        if (itemClassName != null && itemClassName.startsWith(BARTWORKS_PACKAGE_PREFIX)) {
            return true;
        }

        Block block = Block.getBlockFromItem(stack.getItem());
        return block != null
            && block.getClass().getName() != null
            && block.getClass().getName().startsWith(BARTWORKS_PACKAGE_PREFIX);
    }

    private static Object getStaticFieldValue(Class<?> owner, String fieldName) {
        Field field = findField(owner, fieldName);
        if (field == null) {
            return null;
        }

        try {
            return field.get(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object getStaticFieldValue(String className, String fieldName) {
        try {
            Class<?> owner = Class.forName(className);
            return getStaticFieldValue(owner, fieldName);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object getFieldValue(Object target, String fieldName) {
        if (target == null) {
            return null;
        }

        Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            return null;
        }

        try {
            return field.get(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> owner, String fieldName) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (Exception ignored) {
                // Continue searching up the hierarchy.
            }
        }

        return null;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }

        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object invokeDeclaredNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }

        for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Exception ignored) {
                // Continue searching up the hierarchy.
            }
        }

        return null;
    }

    private static Object invokeStatic(String className, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Class<?> owner = Class.forName(className);
            Method method = owner.getMethod(methodName, parameterTypes);
            return method.invoke(null, args);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object invokeSingleArg(Object target, String methodName, Object arg) {
        if (target == null || arg == null) {
            return null;
        }

        Method method = findCompatibleMethod(target.getClass(), methodName, arg);
        if (method == null) {
            return null;
        }

        try {
            return method.invoke(target, arg);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Method findCompatibleMethod(Class<?> owner, String methodName, Object arg) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            Method[] methods = current.getDeclaredMethods();
            for (Method method : methods) {
                if (!methodName.equals(method.getName()) || method.getParameterTypes().length != 1) {
                    continue;
                }

                Class<?> parameterType = method.getParameterTypes()[0];
                if (!isCompatible(parameterType, arg.getClass())) {
                    continue;
                }

                method.setAccessible(true);
                return method;
            }
        }

        Method[] publicMethods = owner.getMethods();
        for (Method method : publicMethods) {
            if (!methodName.equals(method.getName()) || method.getParameterTypes().length != 1) {
                continue;
            }

            Class<?> parameterType = method.getParameterTypes()[0];
            if (!isCompatible(parameterType, arg.getClass())) {
                continue;
            }

            return method;
        }

        return null;
    }

    private static boolean isCompatible(Class<?> parameterType, Class<?> argumentType) {
        if (parameterType.isAssignableFrom(argumentType)) {
            return true;
        }

        if (!parameterType.isPrimitive()) {
            return false;
        }

        if (parameterType == Short.TYPE) {
            return Short.class.equals(argumentType);
        }
        if (parameterType == Integer.TYPE) {
            return Integer.class.equals(argumentType);
        }
        if (parameterType == Long.TYPE) {
            return Long.class.equals(argumentType);
        }
        if (parameterType == Boolean.TYPE) {
            return Boolean.class.equals(argumentType);
        }
        if (parameterType == Byte.TYPE) {
            return Byte.class.equals(argumentType);
        }
        if (parameterType == Character.TYPE) {
            return Character.class.equals(argumentType);
        }
        if (parameterType == Float.TYPE) {
            return Float.class.equals(argumentType);
        }
        if (parameterType == Double.TYPE) {
            return Double.class.equals(argumentType);
        }

        return false;
    }

    private static final class ResolvedOrePrefix {

        private final Object value;

        private ResolvedOrePrefix(Object value) {
            this.value = value;
        }
    }
}
