package com.starskyxiii.polyglottooltip;

import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;

public final class SpawnEggResolver {

    private static final String VANILLA_SPAWN_EGG_NAME = "item.monsterPlacer.name";

    private SpawnEggResolver() {}

    public static String tryResolveDisplayName(ItemStack stack, String languageCode) {
        String mappedSpawnEggName = resolveMappedSpawnEggDisplayName(stack, languageCode);
        if (mappedSpawnEggName != null && !mappedSpawnEggName.isEmpty()) {
            return mappedSpawnEggName;
        }

        if (stack != null && stack.getItem() instanceof ItemMonsterPlacer) {
            return resolveVanillaSpawnEggDisplayName(stack, languageCode);
        }

        return null;
    }

    private static String resolveVanillaSpawnEggDisplayName(ItemStack stack, String languageCode) {
        StringBuilder builder = new StringBuilder();
        appendTranslated(builder, languageCode, getTranslationKey(stack));

        String entityName = EntityList.getStringFromID(stack.getItemDamage());
        if (entityName != null) {
            appendTranslated(builder, languageCode, "entity." + entityName + ".name");
        }

        return builder.length() == 0 ? null : builder.toString().trim();
    }

    private static String resolveMappedSpawnEggDisplayName(ItemStack stack, String languageCode) {
        String entityTranslationKey = getMappedSpawnEggEntityTranslationKey(stack);
        if (entityTranslationKey == null || entityTranslationKey.isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();

        String eggName = LanguageCache.translate(languageCode, getTranslationKey(stack));
        if (eggName == null || eggName.isEmpty()) {
            eggName = LanguageCache.translate(languageCode, VANILLA_SPAWN_EGG_NAME);
        }

        if (eggName != null && !eggName.isEmpty()) {
            builder.append(eggName.trim());
        }

        String entityName = LanguageCache.translate(languageCode, entityTranslationKey);
        if (entityName != null && !entityName.isEmpty()) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(entityName.trim());
        }

        return builder.length() == 0 ? null : builder.toString();
    }

    private static String getMappedSpawnEggEntityTranslationKey(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        try {
            List<?> mappings = getSpawnEggMappings(stack.getItem());
            if (mappings == null || stack.getItemDamage() < 0 || stack.getItemDamage() >= mappings.size()) {
                return null;
            }

            Object mapping = mappings.get(stack.getItemDamage());
            if (mapping == null) {
                return null;
            }

            Object entityClassObject = tryInvokeNoArg(mapping, "getFirst");
            if (entityClassObject instanceof Class<?>) {
                @SuppressWarnings("unchecked")
                Class<? extends Entity> entityClass = (Class<? extends Entity>) entityClassObject;
                Object entityName = EntityList.classToStringMapping.get(entityClass);
                if (entityName instanceof String && !((String) entityName).isEmpty()) {
                    return "entity." + entityName + ".name";
                }
            }

            Object entityName = getAccessibleFieldValue(mapping.getClass(), mapping, "name");
            if (!(entityName instanceof String) || ((String) entityName).isEmpty()) {
                entityName = tryInvokeNoArg(mapping, "getName");
            }

            if (entityName instanceof String && !((String) entityName).isEmpty()) {
                return "entity." + entityName + ".name";
            }
        } catch (Exception ignored) {
            // Ignore and fall back to other display-name resolvers.
        }

        return null;
    }

    private static List<?> getSpawnEggMappings(net.minecraft.item.Item item) throws Exception {
        Object directMappings = getAccessibleFieldValue(item.getClass(), null, "mappings");
        if (directMappings instanceof List<?>) {
            return (List<?>) directMappings;
        }

        Object directSpawnList = getAccessibleFieldValue(item.getClass(), null, "spawnList");
        if (directSpawnList instanceof List<?>) {
            return (List<?>) directSpawnList;
        }

        Object companion = getAccessibleFieldValue(item.getClass(), null, "Companion");
        if (companion != null) {
            Object companionMappings = tryInvokeNoArg(companion, "getMappings");
            if (companionMappings instanceof List<?>) {
                return (List<?>) companionMappings;
            }

            Object companionSpawnList = tryInvokeNoArg(companion, "getSpawnList");
            if (companionSpawnList instanceof List<?>) {
                return (List<?>) companionSpawnList;
            }
        }

        Object instanceMappings = tryInvokeNoArg(item, "getMappings");
        if (instanceMappings instanceof List<?>) {
            return (List<?>) instanceMappings;
        }

        Object instanceSpawnList = tryInvokeNoArg(item, "getSpawnList");
        if (instanceSpawnList instanceof List<?>) {
            return (List<?>) instanceSpawnList;
        }

        return null;
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

    private static Object getAccessibleFieldValue(Class<?> owner, Object target, String fieldName) throws Exception {
        try {
            java.lang.reflect.Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private static String getTranslationKey(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        String unlocalizedName = stack.getItem().getUnlocalizedNameInefficiently(stack);
        if (unlocalizedName == null || unlocalizedName.isEmpty()) {
            return null;
        }

        return unlocalizedName + ".name";
    }

    private static void appendTranslated(StringBuilder builder, String languageCode, String translationKey) {
        String translated = LanguageCache.translate(languageCode, translationKey);
        if (translated == null || translated.isEmpty()) {
            return;
        }

        if (builder.length() > 0) {
            builder.append(' ');
        }

        builder.append(translated.trim());
    }
}
