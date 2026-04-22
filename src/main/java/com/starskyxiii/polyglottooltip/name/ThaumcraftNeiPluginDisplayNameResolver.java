package com.starskyxiii.polyglottooltip.name;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.starskyxiii.polyglottooltip.i18n.LanguageCache;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

final class ThaumcraftNeiPluginDisplayNameResolver {

    private static final String ITEM_ASPECT_CLASS_NAME = "com.djgiannuzz.thaumcraftneiplugin.items.ItemAspect";
    private static final String THAUMCRAFT_ASPECT_CLASS_NAME = "thaumcraft.api.aspects.Aspect";
    private static final String THAUMCRAFT_CLASS_NAME = "thaumcraft.common.Thaumcraft";

    private static final String ASPECTS_NBT_KEY = "Aspects";
    private static final String ASPECT_TAG_NBT_KEY = "key";

    private static final String ASPECT_PREFIX_TRANSLATION_KEY = "item.itemaspect.aspectprefix";
    private static final String UNKNOWN_ASPECT_TRANSLATION_KEY = "tc.aspect.unknown";

    private ThaumcraftNeiPluginDisplayNameResolver() {}

    static String tryResolveDisplayName(ItemStack stack, String languageCode) {
        if (!isAspectItem(stack) || languageCode == null || languageCode.trim().isEmpty()) {
            return null;
        }

        String aspectTag = getAspectTag(stack);
        if (aspectTag == null) {
            return null;
        }

        String aspectName = resolveAspectDisplayName(aspectTag, languageCode.trim());
        if (aspectName == null || aspectName.isEmpty()) {
            return null;
        }

        String prefix = LanguageCache.translate(languageCode.trim(), ASPECT_PREFIX_TRANSLATION_KEY);
        if (prefix == null || prefix.isEmpty()) {
            return aspectName;
        }

        return prefix.trim() + ": " + aspectName.trim();
    }

    static boolean isAspectItem(ItemStack stack) {
        return stack != null
            && stack.getItem() != null
            && ITEM_ASPECT_CLASS_NAME.equals(stack.getItem().getClass().getName());
    }

    private static String resolveAspectDisplayName(String aspectTag, String languageCode) {
        Object aspect = getAspect(aspectTag);
        Boolean discovered = tryDetermineAspectDiscovery(aspect);
        if (Boolean.FALSE.equals(discovered)) {
            String unknownName = LanguageCache.translate(languageCode, UNKNOWN_ASPECT_TRANSLATION_KEY);
            return unknownName == null || unknownName.isEmpty() ? null : unknownName.trim();
        }

        String canonicalName = getCanonicalAspectName(aspect);
        if (canonicalName != null && !canonicalName.isEmpty()) {
            return canonicalName;
        }

        return formatAspectTagFallback(aspectTag);
    }

    private static String getAspectTag(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return null;
        }

        NBTTagCompound rootTag = stack.getTagCompound();
        if (rootTag == null || !rootTag.hasKey(ASPECTS_NBT_KEY, 9)) {
            return null;
        }

        NBTTagList aspectsTag = rootTag.getTagList(ASPECTS_NBT_KEY, 10);
        if (aspectsTag == null || aspectsTag.tagCount() <= 0) {
            return null;
        }

        NBTTagCompound firstAspectTag = aspectsTag.getCompoundTagAt(0);
        if (firstAspectTag == null || !firstAspectTag.hasKey(ASPECT_TAG_NBT_KEY, 8)) {
            return null;
        }

        String aspectTag = firstAspectTag.getString(ASPECT_TAG_NBT_KEY);
        if (aspectTag == null) {
            return null;
        }

        aspectTag = aspectTag.trim();
        return aspectTag.isEmpty() ? null : aspectTag;
    }

    private static Object getAspect(String aspectTag) {
        if (aspectTag == null || aspectTag.isEmpty()) {
            return null;
        }

        try {
            Class<?> aspectClass = Class.forName(THAUMCRAFT_ASPECT_CLASS_NAME);
            Method getAspect = aspectClass.getMethod("getAspect", String.class);
            return getAspect.invoke(null, aspectTag);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getCanonicalAspectName(Object aspect) {
        if (aspect == null) {
            return null;
        }

        try {
            Method getName = aspect.getClass().getMethod("getName");
            Object resolved = getName.invoke(aspect);
            if (resolved instanceof String) {
                String aspectName = ((String) resolved).trim();
                return aspectName.isEmpty() ? null : aspectName;
            }
        } catch (Exception ignored) {
            return null;
        }

        return null;
    }

    private static Boolean tryDetermineAspectDiscovery(Object aspect) {
        if (aspect == null) {
            return null;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null) {
            return null;
        }

        try {
            Class<?> aspectClass = Class.forName(THAUMCRAFT_ASPECT_CLASS_NAME);
            if (!aspectClass.isInstance(aspect)) {
                return null;
            }

            Object playerKnowledge = getThaumcraftPlayerKnowledge();
            if (playerKnowledge == null) {
                return null;
            }

            Method hasDiscoveredAspect =
                playerKnowledge.getClass().getMethod("hasDiscoveredAspect", String.class, aspectClass);
            Object resolved = hasDiscoveredAspect.invoke(playerKnowledge, minecraft.thePlayer.getDisplayName(), aspect);
            return resolved instanceof Boolean ? (Boolean) resolved : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object getThaumcraftPlayerKnowledge() {
        try {
            Class<?> thaumcraftClass = Class.forName(THAUMCRAFT_CLASS_NAME);
            Field proxyField = thaumcraftClass.getField("proxy");
            Object proxy = proxyField.get(null);
            return getFieldValue(proxy, "playerKnowledge");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object getFieldValue(Object target, String fieldName) {
        if (target == null || fieldName == null || fieldName.isEmpty()) {
            return null;
        }

        Class<?> currentClass = target.getClass();
        while (currentClass != null) {
            try {
                Field field = currentClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                currentClass = currentClass.getSuperclass();
            } catch (IllegalAccessException ignored) {
                return null;
            }
        }

        return null;
    }

    private static String formatAspectTagFallback(String aspectTag) {
        if (aspectTag == null) {
            return null;
        }

        String normalized = aspectTag.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder(normalized.length());
        boolean capitalizeNext = true;
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (current == '_' || current == '-' || current == ' ') {
                builder.append(current);
                capitalizeNext = true;
                continue;
            }

            if (capitalizeNext && Character.isLetter(current)) {
                builder.append(Character.toUpperCase(current));
                capitalizeNext = false;
                continue;
            }

            builder.append(current);
            capitalizeNext = false;
        }

        return builder.toString();
    }
}
