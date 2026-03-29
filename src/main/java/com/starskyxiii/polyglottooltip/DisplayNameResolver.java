package com.starskyxiii.polyglottooltip;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSkull;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionHelper;
import net.minecraft.util.EnumChatFormatting;

public final class DisplayNameResolver {

    private static final String AE2_FACADE_CLASS_NAME = "appeng.items.parts.ItemFacade";
    private static final String POTION_GRENADE_PREFIX = "potion.prefix.grenade";
    private static final String EMPTY_POTION_NAME = "item.emptyPotion.name";
    private static final String PLAYER_SKULL_NAME = "item.skull.player.name";
    private static final int MAX_RESOLVE_DEPTH = 8;

    private DisplayNameResolver() {}

    public static List<String> resolveSecondaryDisplayNames(ItemStack stack) {
        LinkedHashSet<String> resolvedNames = new LinkedHashSet<String>();

        if (stack == null || stack.getItem() == null) {
            return new ArrayList<String>(resolvedNames);
        }

        for (String languageCode : Config.displayLanguages) {
            String resolvedName = resolveDisplayName(stack, languageCode);
            if (resolvedName != null && !resolvedName.isEmpty()) {
                resolvedNames.add(resolvedName);
            }
        }

        return new ArrayList<String>(resolvedNames);
    }

    public static String resolveSecondaryDisplayNameForLanguage(ItemStack stack, String languageCode) {
        String resolvedName = resolveDisplayName(stack, languageCode);
        return resolvedName == null ? null : resolvedName.trim();
    }

    private static String resolveDisplayName(ItemStack stack, String languageCode) {
        return resolveDisplayName(stack, languageCode, 0);
    }

    private static String resolveDisplayName(ItemStack stack, String languageCode, int depth) {
        if (stack == null || stack.getItem() == null || depth > MAX_RESOLVE_DEPTH) {
            return null;
        }

        String facadeDisplayName = resolveFacadeDisplayName(stack, languageCode, depth);
        if (facadeDisplayName != null && !facadeDisplayName.isEmpty()) {
            return facadeDisplayName;
        }

        if (stack.getItem() instanceof ItemPotion) {
            return resolvePotionDisplayName(stack, languageCode);
        }

        String spawnEggDisplayName = SpawnEggResolver.tryResolveDisplayName(stack, languageCode);
        if (spawnEggDisplayName != null && !spawnEggDisplayName.isEmpty()) {
            return spawnEggDisplayName;
        }

        if (stack.getItem() instanceof ItemSkull) {
            return resolveSkullDisplayName(stack, languageCode);
        }

        String dynamicDisplayName = resolveDynamicDisplayName(stack, languageCode, depth);
        if (dynamicDisplayName != null && !dynamicDisplayName.isEmpty()) {
            return dynamicDisplayName;
        }

        return resolveGenericDisplayName(stack, languageCode);
    }

    private static String resolveFacadeDisplayName(ItemStack stack, String languageCode, int depth) {
        if (!isAe2Facade(stack)) {
            return null;
        }

        String facadeName = resolveGenericDisplayName(stack, languageCode);
        ItemStack textureItem = getFacadeTextureItem(stack);
        if (textureItem == null || textureItem.getItem() == null) {
            return facadeName;
        }

        String textureName = resolveDisplayName(textureItem, languageCode, depth + 1);
        if (textureName == null || textureName.isEmpty()) {
            textureName = EnumChatFormatting.getTextWithoutFormattingCodes(textureItem.getDisplayName());
        }

        if (textureName == null || textureName.isEmpty()) {
            return facadeName;
        }

        if (facadeName == null || facadeName.isEmpty()) {
            return textureName;
        }

        return facadeName + " - " + textureName;
    }

    private static String resolveGenericDisplayName(ItemStack stack, String languageCode) {
        return LanguageCache.translate(languageCode, getTranslationKey(stack));
    }

    private static String resolvePotionDisplayName(ItemStack stack, String languageCode) {
        if (stack.getItemDamage() == 0) {
            return LanguageCache.translate(languageCode, EMPTY_POTION_NAME);
        }

        StringBuilder builder = new StringBuilder();

        if (ItemPotion.isSplash(stack.getItemDamage())) {
            appendTranslated(builder, languageCode, POTION_GRENADE_PREFIX);
        }

        List<PotionEffect> effects = Items.potionitem.getEffects(stack);
        if (effects != null && !effects.isEmpty()) {
            PotionEffect effect = effects.get(0);
            appendTranslated(builder, languageCode, effect.getEffectName() + ".postfix");
            return builder.length() == 0 ? null : builder.toString().trim();
        }

        appendTranslated(builder, languageCode, PotionHelper.func_77905_c(stack.getItemDamage()));
        appendTranslated(builder, languageCode, getTranslationKey(stack));
        return builder.length() == 0 ? null : builder.toString().trim();
    }

    private static String resolveSkullDisplayName(ItemStack stack, String languageCode) {
        if (stack.getItemDamage() == 3 && stack.hasTagCompound()) {
            NBTTagCompound tagCompound = stack.getTagCompound();
            if (tagCompound.hasKey("SkullOwner", 10)) {
                String ownerName = tagCompound.getCompoundTag("SkullOwner").getString("Name");
                String translated = LanguageCache.format(languageCode, PLAYER_SKULL_NAME, ownerName);
                if (translated != null && !translated.isEmpty()) {
                    return translated;
                }
            } else if (tagCompound.hasKey("SkullOwner", 8)) {
                String ownerName = tagCompound.getString("SkullOwner");
                String translated = LanguageCache.format(languageCode, PLAYER_SKULL_NAME, ownerName);
                if (translated != null && !translated.isEmpty()) {
                    return translated;
                }
            }
        }

        return resolveGenericDisplayName(stack, languageCode);
    }

    private static String resolveDynamicDisplayName(ItemStack stack, String languageCode, int depth) {
        return ManaMetalDisplayNameResolver.tryResolveDisplayName(stack, languageCode, depth);
    }

    private static String getTranslationKey(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        String unlocalizedName = stack.getUnlocalizedName();
        if (unlocalizedName == null || unlocalizedName.isEmpty()) {
            return null;
        }
        return unlocalizedName + ".name";
    }

    private static boolean isAe2Facade(ItemStack stack) {
        return stack != null
            && stack.getItem() != null
            && AE2_FACADE_CLASS_NAME.equals(stack.getItem().getClass().getName());
    }

    static String resolveDisplayNameForLanguage(ItemStack stack, String languageCode, int depth) {
        return resolveDisplayName(stack, languageCode, depth);
    }

    static String resolveGenericDisplayNameForLanguage(ItemStack stack, String languageCode) {
        return resolveGenericDisplayName(stack, languageCode);
    }

    private static ItemStack getFacadeTextureItem(ItemStack stack) {
        try {
            Method getTextureItem = stack.getItem().getClass().getMethod("getTextureItem", ItemStack.class);
            Object resolved = getTextureItem.invoke(stack.getItem(), stack);
            if (resolved instanceof ItemStack) {
                return (ItemStack) resolved;
            }
        } catch (Exception ignored) {
            // Ignore and fall back to the facade base name.
        }

        return null;
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
