package com.starskyxiii.polyglottooltip.name;

import com.starskyxiii.polyglottooltip.i18n.GregTechMaterialTranslationResolver;
import com.starskyxiii.polyglottooltip.i18n.LanguageCache;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

final class GregTechDisplayNameResolver {

    private GregTechDisplayNameResolver() {}

    static String tryResolveDisplayName(ItemStack stack, String languageCode) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        String beeProductDisplayName = GregTechBeeProductDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
        if (beeProductDisplayName != null && !beeProductDisplayName.isEmpty()) {
            return beeProductDisplayName;
        }

        String translationKey = getTranslationKey(stack);
        if (!isGregTechTranslationKey(translationKey)) {
            return null;
        }

        String translated = translate(languageCode, translationKey);
        if (translated == null || translated.isEmpty()) {
            translated = resolveFromCurrentDisplayNameKey(stack, languageCode);
            if (translated == null || translated.isEmpty()) {
                return null;
            }
        }

        if (translated.contains("%material")) {
            String materialName = GregTechMaterialTranslationResolver.resolveMaterialTranslation(languageCode, translationKey, stack);
            if (materialName == null || materialName.isEmpty()) {
                return null;
            }
            translated = translated.replace("%material", materialName);
        }

        translated = normalizeTranslatedName(translationKey, translated);
        return translated.isEmpty() ? null : translated;
    }

    static String debugDescribe(ItemStack stack, String languageCode) {
        if (stack == null || stack.getItem() == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        String unlocalizedName = stack.getUnlocalizedName();
        String beeKey = GregTechBeeProductDisplayNameResolver.debugResolveTranslationKey(stack);
        String genericKey = getTranslationKey(stack);

        builder.append("itemClass=")
            .append(stack.getItem().getClass().getName());
        builder.append(";unlocalized=")
            .append(unlocalizedName == null ? "" : unlocalizedName);
        builder.append(";beeKey=")
            .append(beeKey == null ? "" : beeKey);
        builder.append(";beeValue=")
            .append(safeValue(LanguageCache.translate(languageCode, beeKey)));
        builder.append(";genericKey=")
            .append(genericKey == null ? "" : genericKey);
        builder.append(";genericValue=")
            .append(safeValue(translate(languageCode, genericKey)));

        if (genericKey != null) {
            String materialName = GregTechMaterialTranslationResolver.resolveMaterialTranslation(languageCode, genericKey, stack);
            builder.append(";materialValue=")
                .append(safeValue(materialName));
        }

        return builder.toString();
    }

    static String debugGenericTranslationKey(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        return getTranslationKey(stack);
    }

    private static String getTranslationKey(ItemStack stack) {
        String unlocalizedName = stack.getUnlocalizedName();
        if (unlocalizedName == null || unlocalizedName.isEmpty()) {
            return null;
        }

        return unlocalizedName + ".name";
    }

    private static String translate(String languageCode, String translationKey) {
        String translated = LanguageCache.translate(languageCode, translationKey);
        if (translated != null && !translated.isEmpty()) {
            return translated;
        }

        if (translationKey != null && translationKey.endsWith(".name")) {
            String bareKey = translationKey.substring(0, translationKey.length() - 5);
            translated = LanguageCache.translate(languageCode, bareKey);
            if (translated != null && !translated.isEmpty()) {
                return translated;
            }
        }

        return null;
    }

    private static String resolveFromCurrentDisplayNameKey(ItemStack stack, String languageCode) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        String currentDisplayName = EnumChatFormatting.getTextWithoutFormattingCodes(stack.getDisplayName());
        if (currentDisplayName == null || currentDisplayName.trim().isEmpty()) {
            return null;
        }

        String[] candidatePrefixes = new String[] { "gt.", "bw.", "gtplusplus.", "propolis.", "comb." };
        for (String prefix : candidatePrefixes) {
            String resolvedKey = LanguageCache.findCurrentLanguageTranslationKey(currentDisplayName.trim(), prefix);
            if (resolvedKey == null || resolvedKey.trim().isEmpty()) {
                continue;
            }

            String translated = translate(languageCode, resolvedKey.trim());
            if (translated != null && !translated.trim().isEmpty()) {
                return translated.trim();
            }
        }

        return null;
    }

    private static boolean isGregTechTranslationKey(String translationKey) {
        return translationKey != null
            && (translationKey.startsWith("gt.")
                || translationKey.startsWith("bw.")
                || translationKey.startsWith("gtplusplus.")
                || translationKey.startsWith("propolis.")
                || translationKey.startsWith("comb."));
    }

    private static String normalizeTranslatedName(String translationKey, String translated) {
        String normalized = translated == null ? "" : translated.trim();
        if (normalized.isEmpty()) {
            return normalized;
        }

        if (translationKey != null && translationKey.contains(".gt_frame_")) {
            normalized = normalized.replaceFirst("\\s*\\([^)]*\\)\\s*$", "").trim();
        }

        return normalized;
    }

    private static String safeValue(String value) {
        return value == null ? "" : value;
    }
}
