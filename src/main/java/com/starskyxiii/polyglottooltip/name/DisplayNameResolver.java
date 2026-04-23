package com.starskyxiii.polyglottooltip.name;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.starskyxiii.polyglottooltip.config.Config;
import com.starskyxiii.polyglottooltip.i18n.LanguageCache;
import com.starskyxiii.polyglottooltip.i18n.ProgrammaticDisplayNameLookup;
import com.starskyxiii.polyglottooltip.name.prebuilt.BuildProfiler;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCache;
import com.starskyxiii.polyglottooltip.report.NameQuality;
import com.starskyxiii.polyglottooltip.report.NameQualityClassifier;

import net.minecraft.block.Block;
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
    private static final String BINNIE_CLASS_PREFIX = "binnie.";
    private static final String GREGTECH_CLASS_PREFIX = "gregtech.";
    private static final String BARTWORKS_CLASS_PREFIX = "bartworks.";

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
            long identityStartNs = startProfiledSection();
            String depth0ItemClassName = null;
            String depth0BlockClassName = null;
            String depth0RegistryName = null;
            String depth0UnlocalizedName = null;
            try {
                depth0ItemClassName = safeGetItemClassName(stack);
                depth0BlockClassName = safeGetBlockClassName(stack);
                depth0RegistryName = getRegistryName(stack);
                depth0UnlocalizedName = safeGetNormalizedUnlocalizedName(stack);
            } finally {
                finishProfiledSection(
                    "depth0.identity",
                    stack,
                    languageCode,
                    identityStartNs,
                    depth0ItemClassName == null ? depth0RegistryName : depth0ItemClassName);
            }

            long ae2StartNs = startProfiledSection();
            String ae2DisplayName = null;
            try {
                ae2DisplayName = Ae2DisplayNameResolver.tryResolveDisplayName(stack, languageCode);
            } finally {
                finishProfiledSection("depth0.ae2_prepass", stack, languageCode, ae2StartNs, ae2DisplayName);
            }
            if (ae2DisplayName != null && !ae2DisplayName.isEmpty()) {
                return ae2DisplayName;
            }

            // TConstruct modular tools: full-name-cache keys on (registry, damage) but all
            // TConstruct tools have damage=0 regardless of material; getSubItems() collapses
            // all variants to the same cache key. Read material from InfiTool.Head NBT instead.
            long tconstructStartNs = startProfiledSection();
            String tconstructDisplayName = null;
            try {
                tconstructDisplayName = TinkerConstructDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
            } finally {
                finishProfiledSection("depth0.tconstruct", stack, languageCode, tconstructStartNs, tconstructDisplayName);
            }
            if (tconstructDisplayName != null && !tconstructDisplayName.isEmpty()) {
                return tconstructDisplayName;
            }

            long tgregworksStartNs = startProfiledSection();
            String tgregworksPartDisplayName = null;
            try {
                tgregworksPartDisplayName = TGregworksPartDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
            } finally {
                finishProfiledSection(
                    "depth0.tgregworks",
                    stack,
                    languageCode,
                    tgregworksStartNs,
                    tgregworksPartDisplayName);
            }
            if (tgregworksPartDisplayName != null && !tgregworksPartDisplayName.isEmpty()) {
                return tgregworksPartDisplayName;
            }

            // Thaumcraft wands: same NBT-keyed-variant problem; many cap+rod combinations share
            // the same (registry, damage) cache key. Read cap/rod tags from NBT directly.
            long thaumcraftStartNs = startProfiledSection();
            String thaumcraftDisplayName = null;
            try {
                thaumcraftDisplayName = ThaumcraftDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
            } finally {
                finishProfiledSection("depth0.thaumcraft", stack, languageCode, thaumcraftStartNs, thaumcraftDisplayName);
            }
            if (thaumcraftDisplayName != null && !thaumcraftDisplayName.isEmpty()) {
                return thaumcraftDisplayName;
            }

            // Thaumcraft NEI Plugin aspects also share one (registry, damage) cache key and
            // differ only by the NBT "Aspects" payload, so resolve them before cache lookup.
            long thaumcraftNeiStartNs = startProfiledSection();
            String thaumcraftNeiPluginDisplayName = null;
            try {
                thaumcraftNeiPluginDisplayName =
                    ThaumcraftNeiPluginDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
            } finally {
                finishProfiledSection(
                    "depth0.thaumcraft_nei",
                    stack,
                    languageCode,
                    thaumcraftNeiStartNs,
                    thaumcraftNeiPluginDisplayName);
            }
            if (thaumcraftNeiPluginDisplayName != null && !thaumcraftNeiPluginDisplayName.isEmpty()) {
                return thaumcraftNeiPluginDisplayName;
            }

            // ElectriCraft: ore names stored in ElectriOres.oreName at init time.
            long electriStartNs = startProfiledSection();
            String electriDisplayName = null;
            try {
                electriDisplayName = ElectriCraftDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
            } finally {
                finishProfiledSection("depth0.electricraft", stack, languageCode, electriStartNs, electriDisplayName);
            }
            if (electriDisplayName != null && !electriDisplayName.isEmpty()) {
                return electriDisplayName;
            }

            // ReactorCraft: ore/crafting names stored in ReactorOres.oreName and
            // CraftingItems.itemName at init time.
            long reactorStartNs = startProfiledSection();
            String reactorDisplayName = null;
            try {
                reactorDisplayName = ReactorCraftDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
            } finally {
                finishProfiledSection("depth0.reactorcraft", stack, languageCode, reactorStartNs, reactorDisplayName);
            }
            if (reactorDisplayName != null && !reactorDisplayName.isEmpty()) {
                return reactorDisplayName;
            }

            long binnieGateStartNs = startProfiledSection();
            boolean binnieCandidate = false;
            try {
                binnieCandidate = isBinnieCandidate(
                    depth0ItemClassName,
                    depth0BlockClassName,
                    depth0RegistryName,
                    depth0UnlocalizedName);
            } finally {
                finishProfiledSection(
                    "gate.binnie_candidate",
                    stack,
                    languageCode,
                    binnieGateStartNs,
                    binnieCandidate ? "candidate" : null);
            }
            if (binnieCandidate) {
                long binnieStartNs = startProfiledSection();
                String binnieDisplayName = null;
                try {
                    binnieDisplayName = BinnieDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
                } finally {
                    finishProfiledSection("depth0.binnie", stack, languageCode, binnieStartNs, binnieDisplayName);
                }
                if (binnieDisplayName != null && !binnieDisplayName.isEmpty()) {
                    return binnieDisplayName;
                }
            }

            long gtNeiOreStartNs = startProfiledSection();
            String gtNeiOrePluginDisplayName = null;
            try {
                gtNeiOrePluginDisplayName = GtNeiOrePluginDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
            } finally {
                finishProfiledSection(
                    "depth0.gt_nei_ore",
                    stack,
                    languageCode,
                    gtNeiOreStartNs,
                    gtNeiOrePluginDisplayName);
            }
            if (gtNeiOrePluginDisplayName != null && !gtNeiOrePluginDisplayName.isEmpty()) {
                return gtNeiOrePluginDisplayName;
            }

            long bartWorksStartNs = startProfiledSection();
            String bartWorksDisplayName = null;
            try {
                bartWorksDisplayName = BartWorksDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
            } finally {
                finishProfiledSection("depth0.bartworks", stack, languageCode, bartWorksStartNs, bartWorksDisplayName);
            }
            if (bartWorksDisplayName != null && !bartWorksDisplayName.isEmpty()) {
                return bartWorksDisplayName;
            }

            long gregTechGateStartNs = startProfiledSection();
            boolean gregTechCandidate = false;
            try {
                gregTechCandidate = isGregTechCandidate(
                    depth0ItemClassName,
                    depth0BlockClassName,
                    depth0RegistryName,
                    depth0UnlocalizedName);
            } finally {
                finishProfiledSection(
                    "gate.gregtech_candidate",
                    stack,
                    languageCode,
                    gregTechGateStartNs,
                    gregTechCandidate ? "candidate" : null);
            }
            if (gregTechCandidate) {
                long gregTechStartNs = startProfiledSection();
                String gregTechDisplayName = null;
                try {
                    gregTechDisplayName = GregTechDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
                } finally {
                    finishProfiledSection("depth0.gregtech", stack, languageCode, gregTechStartNs, gregTechDisplayName);
                }
                if (gregTechDisplayName != null && !gregTechDisplayName.isEmpty()) {
                    return gregTechDisplayName;
                }
            }

            long gtPlusPlusStartNs = startProfiledSection();
            String gtPlusPlusDisplayName = null;
            try {
                gtPlusPlusDisplayName = GtPlusPlusDisplayNameResolver.tryResolveDisplayName(stack, languageCode);
            } finally {
                finishProfiledSection("depth0.gtplusplus", stack, languageCode, gtPlusPlusStartNs, gtPlusPlusDisplayName);
            }
            if (gtPlusPlusDisplayName != null && !gtPlusPlusDisplayName.isEmpty()) {
                return gtPlusPlusDisplayName;
            }

            long spawnEggStartNs = startProfiledSection();
            String spawnEggDisplayName = null;
            try {
                spawnEggDisplayName = SpawnEggResolver.tryResolveDisplayName(stack, languageCode);
            } finally {
                finishProfiledSection("depth0.spawn_egg", stack, languageCode, spawnEggStartNs, spawnEggDisplayName);
            }
            if (spawnEggDisplayName != null && !spawnEggDisplayName.isEmpty()) {
                return spawnEggDisplayName;
            }

            // ManaMetal AlchemyGem: OKingot stores a runtime-translated gem name so the full
            // cache can contain mixed-language entries. Use the dedicated resolver first.
            long alchemyGemStartNs = startProfiledSection();
            String alchemyGemDisplayName = null;
            try {
                alchemyGemDisplayName = ManaMetalDisplayNameResolver.tryResolveAlchemyGemDisplayName(stack, languageCode);
            } finally {
                finishProfiledSection(
                    "depth0.manametal_alchemy",
                    stack,
                    languageCode,
                    alchemyGemStartNs,
                    alchemyGemDisplayName);
            }
            if (alchemyGemDisplayName != null && !alchemyGemDisplayName.isEmpty()) {
                return alchemyGemDisplayName;
            }
        }

        // Fast path: check the full prebuilt cache first (populated by /polyglotbuild).
        // Only at depth 0 to avoid incorrectly short-circuiting recursive calls (e.g. AE2 facades).
        if (depth == 0 && !ThaumcraftNeiPluginDisplayNameResolver.isAspectItem(stack)) {
            long cacheStartNs = startProfiledSection();
            String acceptedCachedName = null;
            try {
                String cachedName = resolveFromFullCache(stack, languageCode);
                if (cachedName != null
                    && !cachedName.isEmpty()
                    && !shouldIgnoreSuspiciousBartWorksCasingCache(stack, languageCode, cachedName)) {
                    acceptedCachedName = cachedName;
                }
            } finally {
                finishProfiledSection("cache.full_name", stack, languageCode, cacheStartNs, acceptedCachedName);
            }
            if (acceptedCachedName != null && !acceptedCachedName.isEmpty()) {
                return acceptedCachedName;
            }

            long bartWorksCasingStartNs = startProfiledSection();
            String bartWorksCasingDisplayName = null;
            try {
                bartWorksCasingDisplayName =
                    BartWorksDisplayNameResolver.tryResolveGeneratedCasingDisplayName(stack, languageCode);
            } finally {
                finishProfiledSection(
                    "cache.bartworks_generated_casing",
                    stack,
                    languageCode,
                    bartWorksCasingStartNs,
                    bartWorksCasingDisplayName);
            }
            if (bartWorksCasingDisplayName != null && !bartWorksCasingDisplayName.isEmpty()) {
                return bartWorksCasingDisplayName;
            }
        }

        long facadeStartNs = startProfiledSection();
        String facadeDisplayName = null;
        try {
            facadeDisplayName = resolveFacadeDisplayName(stack, languageCode, depth);
        } finally {
            finishProfiledSection("stage.facade", stack, languageCode, facadeStartNs, facadeDisplayName);
        }
        if (facadeDisplayName != null && !facadeDisplayName.isEmpty()) {
            return facadeDisplayName;
        }

        if (stack.getItem() instanceof ItemPotion) {
            long potionStartNs = startProfiledSection();
            String potionDisplayName = null;
            try {
                potionDisplayName = resolvePotionDisplayName(stack, languageCode);
            } finally {
                finishProfiledSection("stage.potion", stack, languageCode, potionStartNs, potionDisplayName);
            }
            return potionDisplayName;
        }

        if (stack.getItem() instanceof ItemSkull) {
            long skullStartNs = startProfiledSection();
            String skullDisplayName = null;
            try {
                skullDisplayName = resolveSkullDisplayName(stack, languageCode);
            } finally {
                finishProfiledSection("stage.skull", stack, languageCode, skullStartNs, skullDisplayName);
            }
            return skullDisplayName;
        }

        long dynamicStartNs = startProfiledSection();
        String dynamicDisplayName = null;
        try {
            dynamicDisplayName = resolveDynamicDisplayName(stack, languageCode, depth);
        } finally {
            finishProfiledSection("stage.dynamic", stack, languageCode, dynamicStartNs, dynamicDisplayName);
        }
        if (dynamicDisplayName != null && !dynamicDisplayName.isEmpty()) {
            return dynamicDisplayName;
        }

        long genericStartNs = startProfiledSection();
        String genericDisplayName = null;
        try {
            genericDisplayName = resolveGenericDisplayName(stack, languageCode);
        } finally {
            finishProfiledSection("stage.generic", stack, languageCode, genericStartNs, genericDisplayName);
        }
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
            long textureFallbackStartNs = startProfiledSection();
            String textureDisplayName = null;
            try {
                textureDisplayName = ProgrammaticDisplayNameLookup.getItemDisplayName(textureItem, languageCode);
            } finally {
                finishProfiledSection(
                    "facade.texture_fallback_display_name",
                    textureItem,
                    languageCode,
                    textureFallbackStartNs,
                    textureDisplayName);
            }
            if (textureDisplayName != null && !textureDisplayName.trim().isEmpty()) {
                String strippedTextureName = EnumChatFormatting.getTextWithoutFormattingCodes(textureDisplayName);
                textureName = strippedTextureName != null
                    ? strippedTextureName.trim()
                    : textureDisplayName.trim();
            }
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

    private static boolean isBinnieCandidate(String itemClassName, String blockClassName,
            String registryName, String unlocalizedName) {
        if (startsWithIgnoreCase(itemClassName, BINNIE_CLASS_PREFIX)) {
            return true;
        }
        if (startsWithIgnoreCase(blockClassName, BINNIE_CLASS_PREFIX)) {
            return true;
        }

        if (startsWithIgnoreCase(registryName, "ExtraBees:")
            || startsWithIgnoreCase(registryName, "ExtraTrees:")
            || startsWithIgnoreCase(registryName, "Botany:")
            || startsWithIgnoreCase(registryName, "Genetics:")
            || startsWithIgnoreCase(registryName, "BinnieCore:")) {
            return true;
        }

        return startsWithIgnoreCase(unlocalizedName, "extrabees.")
            || startsWithIgnoreCase(unlocalizedName, "extratrees.")
            || startsWithIgnoreCase(unlocalizedName, "botany.")
            || startsWithIgnoreCase(unlocalizedName, "genetics.")
            || startsWithIgnoreCase(unlocalizedName, "binniecore.")
            || startsWithIgnoreCase(unlocalizedName, "for.extratrees.");
    }

    private static boolean isGregTechCandidate(String itemClassName, String blockClassName,
            String registryName, String unlocalizedName) {
        if (startsWithIgnoreCase(itemClassName, GREGTECH_CLASS_PREFIX)
            || startsWithIgnoreCase(itemClassName, BARTWORKS_CLASS_PREFIX)
            || startsWithIgnoreCase(blockClassName, GREGTECH_CLASS_PREFIX)
            || startsWithIgnoreCase(blockClassName, BARTWORKS_CLASS_PREFIX)) {
            return true;
        }

        if (startsWithIgnoreCase(registryName, "gregtech:")
            || startsWithIgnoreCase(registryName, "bartworks:")) {
            return true;
        }

        return startsWithIgnoreCase(unlocalizedName, "gt.")
            || startsWithIgnoreCase(unlocalizedName, "bw.")
            || startsWithIgnoreCase(unlocalizedName, "gtplusplus.")
            || startsWithIgnoreCase(unlocalizedName, "comb.")
            || startsWithIgnoreCase(unlocalizedName, "propolis.");
    }

    private static String getRegistryName(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        try {
            Object registryNameObj = Item.itemRegistry.getNameForObject(stack.getItem());
            return registryNameObj == null ? null : String.valueOf(registryNameObj);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String safeGetItemClassName(ItemStack stack) {
        try {
            return stack == null || stack.getItem() == null ? null : stack.getItem().getClass().getName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String safeGetBlockClassName(ItemStack stack) {
        try {
            if (stack == null || stack.getItem() == null) {
                return null;
            }
            Block block = Block.getBlockFromItem(stack.getItem());
            return block == null ? null : block.getClass().getName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String safeGetNormalizedUnlocalizedName(ItemStack stack) {
        try {
            return stack == null ? null : normalizeUnlocalizedName(stack.getUnlocalizedName());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String normalizeUnlocalizedName(String unlocalizedName) {
        if (unlocalizedName == null) {
            return null;
        }

        String normalized = unlocalizedName.trim();
        if (normalized.startsWith("item.")) {
            return normalized.substring(5);
        }
        if (normalized.startsWith("tile.")) {
            return normalized.substring(5);
        }
        return normalized;
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        if (value == null || prefix == null) {
            return false;
        }
        if (value.length() < prefix.length()) {
            return false;
        }
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
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

    private static long startProfiledSection() {
        return BuildProfiler.startSection();
    }

    private static void finishProfiledSection(String section, ItemStack stack, String languageCode,
            long startNs, String result) {
        BuildProfiler.record(section, stack, languageCode, startNs, result);
    }

}
