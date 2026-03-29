package com.starskyxiii.polyglottooltip;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

public final class EntityDisplayNameResolver {

    private EntityDisplayNameResolver() {}

    public static List<String> resolveSecondaryDisplayNames(Entity entity) {
        LinkedHashSet<String> resolvedNames = new LinkedHashSet<String>();

        if (entity == null) {
            return new ArrayList<String>(resolvedNames);
        }

        String primaryName = normalize(entity.getCommandSenderName());
        for (String languageCode : Config.displayLanguages) {
            String resolvedName = resolveDisplayName(entity, languageCode, primaryName);
            if (resolvedName != null && !resolvedName.isEmpty()) {
                resolvedNames.add(resolvedName);
            }
        }

        return new ArrayList<String>(resolvedNames);
    }

    private static String resolveDisplayName(Entity entity, String languageCode, String primaryName) {
        if (entity instanceof EntityPlayer) {
            return null;
        }

        String translationKey = getTranslationKey(entity);
        if (translationKey == null) {
            return null;
        }

        // Keep the entity overlay conservative: only add secondary names when the
        // current label is the entity's default translated name, not a custom tag
        // or a variant-specific override that would need dedicated handling.
        String currentLocalizedName = normalize(StatCollector.translateToLocal(translationKey));
        if (currentLocalizedName == null || !currentLocalizedName.equals(primaryName)) {
            return null;
        }

        String translated = LanguageCache.translate(languageCode, translationKey);
        translated = normalize(translated);
        if (translated == null || translated.equals(primaryName)) {
            return null;
        }

        return translated;
    }

    private static String getTranslationKey(Entity entity) {
        String entityId = EntityList.getEntityString(entity);
        if (entityId == null || entityId.isEmpty()) {
            return null;
        }

        return "entity." + entityId + ".name";
    }

    private static String normalize(String text) {
        if (text == null) {
            return null;
        }

        String normalized = EnumChatFormatting.getTextWithoutFormattingCodes(text);
        if (normalized == null) {
            return null;
        }

        normalized = normalized.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
