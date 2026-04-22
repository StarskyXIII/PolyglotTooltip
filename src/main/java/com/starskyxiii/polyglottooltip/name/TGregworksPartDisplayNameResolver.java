package com.starskyxiii.polyglottooltip.name;

import java.lang.reflect.Method;

import com.starskyxiii.polyglottooltip.i18n.GregTechMaterialTranslationResolver;
import com.starskyxiii.polyglottooltip.i18n.LanguageCache;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

final class TGregworksPartDisplayNameResolver {

    private static final String ITEM_TGREG_PART_CLASS_NAME = "vexatos.tgregworks.item.ItemTGregPart";
    private static final String PART_KEY_PREFIX = "tgregworks.toolpart.";
    private static final String UNKNOWN_MATERIAL_KEY = "tgregworks.materials.unknown";
    private static final String DEPRECATED_SUFFIX_KEY = "tgregworks.tool.deprecated";

    private TGregworksPartDisplayNameResolver() {}

    static String tryResolveDisplayName(ItemStack stack, String languageCode) {
        if (stack == null || stack.getItem() == null || languageCode == null || languageCode.trim().isEmpty()) {
            return null;
        }
        if (!ITEM_TGREG_PART_CLASS_NAME.equals(stack.getItem().getClass().getName())) {
            return null;
        }

        String partKey = resolvePartTranslationKey(stack);
        if (partKey == null || partKey.isEmpty()) {
            return null;
        }

        String template = LanguageCache.translate(languageCode, partKey);
        if (template == null || template.isEmpty()) {
            return null;
        }

        String materialDisplayName = resolveMaterialDisplayName(stack, languageCode);
        if (materialDisplayName == null || materialDisplayName.isEmpty()) {
            materialDisplayName = LanguageCache.translate(languageCode, UNKNOWN_MATERIAL_KEY);
        }
        if (materialDisplayName == null || materialDisplayName.isEmpty()) {
            return null;
        }

        String resolved = template.replace("%%material", materialDisplayName);
        if (stack.getItemDamage() == 0) {
            String deprecatedSuffix = LanguageCache.translate(languageCode, DEPRECATED_SUFFIX_KEY);
            if (deprecatedSuffix != null && !deprecatedSuffix.isEmpty()) {
                resolved = resolved + deprecatedSuffix;
            }
        }

        resolved = resolved.trim();
        return resolved.isEmpty() ? null : resolved;
    }

    private static String resolvePartTranslationKey(ItemStack stack) {
        Object partType = tryInvokeNoArg(stack.getItem(), "getType");
        Object partName = tryInvokeNoArg(partType, "getPartName");
        if (!(partName instanceof String) || ((String) partName).trim().isEmpty()) {
            return null;
        }

        String normalized = ((String) partName).trim().toLowerCase().replace(' ', '_');
        return PART_KEY_PREFIX + normalized;
    }

    private static String resolveMaterialDisplayName(ItemStack stack, String languageCode) {
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null || !tagCompound.hasKey("material")) {
            return null;
        }

        String materialName = tagCompound.getString("material");
        if (materialName == null || materialName.trim().isEmpty()) {
            return null;
        }

        String translated = GregTechMaterialTranslationResolver.resolveMaterialNameTranslation(languageCode, materialName);
        if (translated != null && !translated.isEmpty()) {
            return translated.trim();
        }

        if ("en_US".equalsIgnoreCase(languageCode.trim())) {
            return humanizeMaterialName(materialName);
        }

        return null;
    }

    private static String humanizeMaterialName(String materialName) {
        if (materialName == null) {
            return null;
        }

        String normalized = materialName.trim().replace('_', ' ');
        if (normalized.isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder(normalized.length() + 4);
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (i > 0 && Character.isUpperCase(current) && Character.isLowerCase(normalized.charAt(i - 1))) {
                builder.append(' ');
            }
            builder.append(current);
        }

        return builder.toString().trim();
    }

    private static Object tryInvokeNoArg(Object target, String methodName) {
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
}
