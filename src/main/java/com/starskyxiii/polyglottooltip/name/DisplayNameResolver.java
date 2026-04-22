package com.starskyxiii.polyglottooltip.name;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.starskyxiii.polyglottooltip.config.Config;
import com.starskyxiii.polyglottooltip.i18n.LanguageCache;
import com.starskyxiii.polyglottooltip.report.NameQuality;
import com.starskyxiii.polyglottooltip.report.NameQualityClassifier;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCache;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
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
    private static final String BW_WERKSTOFF_BLOCK_CASING_REGISTRY_NAME = "bartworks:bw.werkstoffblockscasing.01";
    private static final String BW_WERKSTOFF_BLOCK_CASING_ADVANCED_REGISTRY_NAME =
        "bartworks:bw.werkstoffblockscasingadvanced.01";

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

        // Mods that bake translated strings at class-init time (before any language switch):
        // their getDisplayName() always returns the startup language (e.g. zh_CN) regardless
        // of what language is requested. Resolve these before the cache, which would contain
        // stale startup-language names.
        if (depth == 0) {
            String ae2DisplayName = Ae2DisplayNameResolver.tryResolveDisplayName(stack, languageCode);
            if (ae2DisplayName != null && !ae2DisplayName.isEmpty()) {
                return ae2DisplayName;
            }

            // TConstruct modular tools: full-name-cache keys on (registry, damage) but all
            // TConstruct tools have damage=0 regardless of material — getSubItems() collapses
            // all variants to the same cache key. Read material from InfiTool.Head NBT instead.
            String tconstructDisplayName = TinkerConstructDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
            if (tconstructDisplayName != null && !tconstructDisplayName.isEmpty()) {
                return tconstructDisplayName;
            }

            String tgregworksPartDisplayName = TGregworksPartDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
            if (tgregworksPartDisplayName != null && !tgregworksPartDisplayName.isEmpty()) {
                return tgregworksPartDisplayName;
            }

            // Thaumcraft wands: same NBT-keyed-variant problem — many cap+rod combinations share
            // the same (registry, damage) cache key. Read cap/rod tags from NBT directly.
            String thaumcraftDisplayName = ThaumcraftDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
            if (thaumcraftDisplayName != null && !thaumcraftDisplayName.isEmpty()) {
                return thaumcraftDisplayName;
            }

            // Thaumcraft NEI Plugin aspects also share one (registry, damage) cache key and
            // differ only by the NBT "Aspects" payload, so resolve them before cache lookup.
            String thaumcraftNeiPluginDisplayName =
                ThaumcraftNeiPluginDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
            if (thaumcraftNeiPluginDisplayName != null && !thaumcraftNeiPluginDisplayName.isEmpty()) {
                return thaumcraftNeiPluginDisplayName;
            }

            // ElectriCraft: ore names stored in ElectriOres.oreName at init time.
            String electriDisplayName = ElectriCraftDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
            if (electriDisplayName != null && !electriDisplayName.isEmpty()) {
                return electriDisplayName;
            }

            // ReactorCraft: ore/crafting names stored in ReactorOres.oreName and
            // CraftingItems.itemName at init time.
            String reactorDisplayName = ReactorCraftDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
            if (reactorDisplayName != null && !reactorDisplayName.isEmpty()) {
                return reactorDisplayName;
            }

            String binnieDisplayName = BinnieDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
            if (binnieDisplayName != null && !binnieDisplayName.isEmpty()) {
                return binnieDisplayName;
            }

            String gtNeiOrePluginDisplayName = GtNeiOrePluginDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
            if (gtNeiOrePluginDisplayName != null && !gtNeiOrePluginDisplayName.isEmpty()) {
                return gtNeiOrePluginDisplayName;
            }

            String bartWorksDisplayName = BartWorksDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
            if (bartWorksDisplayName != null && !bartWorksDisplayName.isEmpty()) {
                return bartWorksDisplayName;
            }

            String gregTechDisplayName = GregTechDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
            if (gregTechDisplayName != null && !gregTechDisplayName.isEmpty()) {
                return gregTechDisplayName;
            }

            String gtPlusPlusDisplayName = GtPlusPlusDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
            if (gtPlusPlusDisplayName != null && !gtPlusPlusDisplayName.isEmpty()) {
                return gtPlusPlusDisplayName;
            }

            String spawnEggDisplayName = SpawnEggResolver.tryResolveDisplayName(stack, languageCode);
            if (spawnEggDisplayName != null && !spawnEggDisplayName.isEmpty()) {
                return spawnEggDisplayName;
            }

            // ManaMetal AlchemyGem: OKingot stores a runtime-translated gem name (e.g. "钻石")
            // so the full cache contains mixed-language entries like "Shattered钻石". The
            // dedicated resolver now uses hardcoded English key suffixes and is correct here.
            // Other ManaMetal items (e.g. ItemPetEggs1) use the normal chain below so the
            // full cache can still serve intentionally mixed names like "大黑塔Pet Egg".
            String alchemyGemDisplayName = ManaMetalDisplayNameResolver.tryResolveAlchemyGemDisplayName(stack, languageCode);
            if (alchemyGemDisplayName != null && !alchemyGemDisplayName.isEmpty()) {
                return alchemyGemDisplayName;
            }
        }

        // Fast path: check the full prebuilt cache first (populated by /polyglotbuild).
        // Only at depth 0 to avoid incorrectly short-circuiting recursive calls (e.g. AE2 facades).
        if (depth == 0 && !ThaumcraftNeiPluginDisplayNameResolver.isAspectItem(stack)) {
            String cachedName = resolveFromFullCache(stack, languageCode);
            if (cachedName != null
                && !cachedName.isEmpty()
                && !shouldIgnoreSuspiciousBartWorksCasingCache(stack, languageCode, cachedName)) {
                return cachedName;
            }

            String bartWorksCasingDisplayName =
                BartWorksDisplayNameResolver.tryResolveGeneratedCasingDisplayName(stack, languageCode);
            if (bartWorksCasingDisplayName != null && !bartWorksCasingDisplayName.isEmpty()) {
                return bartWorksCasingDisplayName;
            }
        }

        String facadeDisplayName = resolveFacadeDisplayName(stack, languageCode, depth);
        if (facadeDisplayName != null && !facadeDisplayName.isEmpty()) {
            return facadeDisplayName;
        }

        if (stack.getItem() instanceof ItemPotion) {
            return resolvePotionDisplayName(stack, languageCode);
        }

        if (stack.getItem() instanceof ItemSkull) {
            return resolveSkullDisplayName(stack, languageCode);
        }

        String dynamicDisplayName = resolveDynamicDisplayName(stack, languageCode, depth);
        if (dynamicDisplayName != null && !dynamicDisplayName.isEmpty()) {
            return dynamicDisplayName;
        }

        String genericDisplayName = resolveGenericDisplayName(stack, languageCode);
        if (genericDisplayName != null && !genericDisplayName.isEmpty()) {
            return genericDisplayName;
        }

        return null;
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
        String ae2DisplayName = Ae2DisplayNameResolver.tryResolveDisplayName(stack, languageCode);
        if (ae2DisplayName != null && !ae2DisplayName.isEmpty()) {
            return ae2DisplayName;
        }

        return ManaMetalDisplayNameResolver.tryResolveDisplayName(stack, languageCode, depth);
    }

    private static String resolveFromFullCache(ItemStack stack, String languageCode) {
        if (FullNameCache.isEmpty()) return null;
        if (stack == null || stack.getItem() == null) return null;
        Object registryNameObj = Item.itemRegistry.getNameForObject(stack.getItem());
        if (registryNameObj == null) return null;
        return FullNameCache.lookup(String.valueOf(registryNameObj), stack.getItemDamage(), languageCode);
    }

    private static boolean shouldIgnoreSuspiciousBartWorksCasingCache(ItemStack stack, String languageCode,
        String cachedName) {
        if (stack == null || stack.getItem() == null || cachedName == null || cachedName.trim().isEmpty()) {
            return false;
        }

        Object registryNameObj = Item.itemRegistry.getNameForObject(stack.getItem());
        if (registryNameObj == null) {
            return false;
        }

        String registryName = String.valueOf(registryNameObj);
        if (!BW_WERKSTOFF_BLOCK_CASING_REGISTRY_NAME.equals(registryName)
            && !BW_WERKSTOFF_BLOCK_CASING_ADVANCED_REGISTRY_NAME.equals(registryName)) {
            return false;
        }

        NameQuality quality = NameQualityClassifier.classify(cachedName.trim(), languageCode);
        return quality == NameQuality.CONTAINS_CJK || quality == NameQuality.MIXED_LANGUAGE;
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
