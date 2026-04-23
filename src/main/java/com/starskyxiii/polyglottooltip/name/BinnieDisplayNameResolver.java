package com.starskyxiii.polyglottooltip.name;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.starskyxiii.polyglottooltip.i18n.LanguageCache;
import com.starskyxiii.polyglottooltip.i18n.ProgrammaticDisplayNameLookup;
import com.starskyxiii.polyglottooltip.name.prebuilt.BuildProfiler;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

final class BinnieDisplayNameResolver {

    private static final String EXTRA_TREE_PLANK_VALUES_CLASS_NAME = "binnie.extratrees.block.PlankType$ExtraTreePlanks";
    private static final String EXTRA_TREE_LOG_VALUES_CLASS_NAME = "binnie.extratrees.block.ILogType$ExtraTreeLog";
    private static final String VANILLA_PLANK_CLASS_NAME = "binnie.extratrees.block.PlankType$VanillaPlanks";
    private static final String FORESTRY_PLANK_CLASS_NAME = "binnie.extratrees.block.PlankType$ForestryPlanks";
    private static final String EXTRA_BIOMES_PLANK_CLASS_NAME = "binnie.extratrees.block.PlankType$ExtraBiomesPlank";
    private static final String WOOD_MANAGER_CLASS_NAME = "binnie.extratrees.block.WoodManager";
    private static final String BLOCK_ET_DOOR_CLASS_NAME = "binnie.extratrees.block.BlockETDoor";
    private static final String MODULE_CARPENTRY_CLASS_NAME = "binnie.extratrees.carpentry.ModuleCarpentry";
    private static final String DESIGN_SYSTEM_CLASS_NAME = "binnie.extratrees.api.IDesignSystem";

    private static final String BLOCK_ET_LOG_CLASS_NAME = "binnie.extratrees.block.BlockETLog";
    private static final String BLOCK_ET_PLANKS_CLASS_NAME = "binnie.extratrees.block.BlockETPlanks";
    private static final String BLOCK_ET_SLAB_CLASS_NAME = "binnie.extratrees.block.BlockETSlab";
    private static final String ITEM_ET_STAIRS_CLASS_NAME = "binnie.extratrees.block.ItemETStairs";
    private static final String BLOCK_FENCE_CLASS_NAME = "binnie.extratrees.block.decor.BlockFence";
    private static final String BLOCK_GATE_CLASS_NAME = "binnie.extratrees.block.decor.BlockGate";
    private static final String BLOCK_CARPENTRY_CLASS_NAME = "binnie.extratrees.carpentry.BlockCarpentry";
    private static final String BLOCK_CARPENTRY_PANEL_CLASS_NAME = "binnie.extratrees.carpentry.BlockCarpentryPanel";
    private static final String BLOCK_STAINED_DESIGN_CLASS_NAME = "binnie.extratrees.carpentry.BlockStainedDesign";
    private static final String BLOCK_MACHINE_CLASS_NAME = "binnie.core.machines.BlockMachine";
    private static final String ITEM_META_KEY = "meta";

    private static final String[] DIRECT_KEY_PREFIXES = new String[] {
        "extrabees.item.comb.",
        "extrabees.item.honeydrop.",
        "extrabees.item.",
        "extrabees.block.hive.",
        "extratrees.item.",
        "for.extratrees.item.food.",
        "botany.item.",
        "genetics.item.",
        "binniecore.item."
    };

    private BinnieDisplayNameResolver() {}

    static String tryResolveDisplayName(ItemStack stack, String languageCode) {
        if (stack == null || stack.getItem() == null || languageCode == null || languageCode.trim().isEmpty()) {
            return null;
        }

        Block block = Block.getBlockFromItem(stack.getItem());
        String itemClassName = stack.getItem().getClass().getName();
        String blockClassName = block == null ? null : block.getClass().getName();

        if (!isBinnieClassName(itemClassName) && !isBinnieClassName(blockClassName)) {
            return null;
        }

        long specialStartNs = BuildProfiler.startSection();
        String specialName = null;
        try {
            specialName = tryResolveSpecialDisplayName(stack, languageCode, block, itemClassName, blockClassName);
        } finally {
            BuildProfiler.record("binnie.special", stack, languageCode, specialStartNs, specialName);
        }
        if (specialName != null && !specialName.isEmpty()) {
            return specialName;
        }

        long directStartNs = BuildProfiler.startSection();
        String directName = null;
        try {
            directName = tryResolveDirectKeyName(stack, languageCode);
        } finally {
            BuildProfiler.record("binnie.direct", stack, languageCode, directStartNs, directName);
        }
        return directName;
    }

    private static String tryResolveSpecialDisplayName(ItemStack stack, String languageCode, Block block,
        String itemClassName, String blockClassName) {
        String extraTreesName = tryResolveExtraTreesCompositeName(stack, languageCode, itemClassName, blockClassName);
        if (extraTreesName != null && !extraTreesName.isEmpty()) {
            return extraTreesName;
        }

        String carpentryName = tryResolveCarpentryDisplayName(block, languageCode, blockClassName, getMetadata(stack));
        if (carpentryName != null && !carpentryName.isEmpty()) {
            return carpentryName;
        }

        return tryResolveMachineDisplayName(block, languageCode, stack.getItemDamage());
    }

    private static String tryResolveDirectKeyName(ItemStack stack, String languageCode) {
        long liveNameStartNs = BuildProfiler.startSection();
        String currentDisplayName = null;
        try {
            currentDisplayName = EnumChatFormatting.getTextWithoutFormattingCodes(
                ProgrammaticDisplayNameLookup.getLiveLanguageDisplayName(stack));
        } finally {
            BuildProfiler.record("binnie.direct.live_name", stack, languageCode, liveNameStartNs, currentDisplayName);
        }
        if (currentDisplayName == null || currentDisplayName.trim().isEmpty()) {
            return null;
        }

        long reverseLookupStartNs = BuildProfiler.startSection();
        String resolvedName = null;
        for (String prefix : DIRECT_KEY_PREFIXES) {
            String translationKey = LanguageCache.findCurrentLanguageTranslationKey(currentDisplayName.trim(), prefix);
            if (translationKey == null || translationKey.trim().isEmpty()) {
                continue;
            }

            String translated = LanguageCache.translate(languageCode, translationKey);
            if (translated != null && !translated.trim().isEmpty()) {
                resolvedName = translated.trim();
                break;
            }
        }

        BuildProfiler.record("binnie.direct.reverse_lookup", stack, languageCode, reverseLookupStartNs, resolvedName);
        return resolvedName;
    }

    private static String tryResolveExtraTreesCompositeName(ItemStack stack, String languageCode, String itemClassName,
        String blockClassName) {
        int metadata = getMetadata(stack);

        if (BLOCK_ET_LOG_CLASS_NAME.equals(blockClassName)) {
            String logName = resolveExtraTreeLogName(languageCode, metadata);
            return format(languageCode, "extratrees.block.log.name", logName);
        }

        if (BLOCK_ET_PLANKS_CLASS_NAME.equals(blockClassName)) {
            String plankName = resolveExtraTreePlankName(languageCode, metadata);
            return format(languageCode, "extratrees.block.plank.name", plankName);
        }

        if (BLOCK_ET_SLAB_CLASS_NAME.equals(blockClassName)) {
            String plankName = resolveExtraTreePlankName(languageCode, metadata);
            return format(languageCode, "extratrees.block.woodslab.name", plankName);
        }

        if (ITEM_ET_STAIRS_CLASS_NAME.equals(itemClassName)) {
            String plankName = resolveWoodManagerPlankName(languageCode, metadata + 32);
            return format(languageCode, "extratrees.block.woodstairs.name", plankName);
        }

        if (BLOCK_FENCE_CLASS_NAME.equals(blockClassName)) {
            String plankName = resolveWoodManagerPlankName(languageCode, metadata);
            return format(languageCode, "extratrees.block.woodfence.name", plankName);
        }

        if (BLOCK_GATE_CLASS_NAME.equals(blockClassName)) {
            String plankName = resolveWoodManagerPlankName(languageCode, metadata);
            return format(languageCode, "extratrees.block.woodgate.name", plankName);
        }

        if (BLOCK_ET_DOOR_CLASS_NAME.equals(blockClassName)) {
            return resolveExtraTreesDoorName(languageCode, metadata);
        }

        return null;
    }

    private static String tryResolveCarpentryDisplayName(Block block, String languageCode, String blockClassName,
        int metadata) {
        if (block == null) {
            return null;
        }

        String translationKey = null;
        if (BLOCK_CARPENTRY_CLASS_NAME.equals(blockClassName)) {
            translationKey = "extratrees.block.woodentile.name";
        } else if (BLOCK_CARPENTRY_PANEL_CLASS_NAME.equals(blockClassName)) {
            translationKey = "extratrees.block.woodenpanel.name";
        } else if (BLOCK_STAINED_DESIGN_CLASS_NAME.equals(blockClassName)) {
            translationKey = "extratrees.block.stainedglass.name";
        }

        if (translationKey == null) {
            return null;
        }

        Object designBlock = resolveCarpentryDesignBlock(block, metadata);
        if (designBlock == null) {
            return null;
        }

        Object design = invokeNoArg(designBlock, "getDesign");
        String designName = translateCurrentLocalizedValue(languageCode, extractCurrentLocalizedName(design), "botany.design.");
        return format(languageCode, translationKey, designName);
    }

    private static String tryResolveMachineDisplayName(Block block, String languageCode, int metadata) {
        if (block == null || !BLOCK_MACHINE_CLASS_NAME.equals(block.getClass().getName())) {
            return null;
        }

        Object machinePackage = invoke(block, "getPackage", new Class<?>[] { int.class }, Integer.valueOf(metadata));
        if (machinePackage == null) {
            return null;
        }

        String translationKey = buildMachineTranslationKey(machinePackage);
        if (translationKey == null || translationKey.isEmpty()) {
            return extractString(machinePackage, "getDisplayName");
        }

        String translated = LanguageCache.translate(languageCode, translationKey);
        if (translated != null && !translated.trim().isEmpty()) {
            return translated.trim();
        }

        return extractString(machinePackage, "getDisplayName");
    }

    private static String resolveExtraTreesDoorName(String languageCode, int metadata) {
        String woodName = resolveWoodManagerPlankName(languageCode, metadata & 0xFF);
        if (woodName == null || woodName.isEmpty()) {
            return null;
        }

        Object doorType = invokeStatic(BLOCK_ET_DOOR_CLASS_NAME, "getDoorType", new Class<?>[] { int.class },
            Integer.valueOf(metadata));
        String typeName = resolveDoorTypeName(languageCode, doorType);
        if (typeName == null || typeName.isEmpty()) {
            return format(languageCode, "extratrees.block.door.name", woodName);
        }

        return format(languageCode, "extratrees.block.door.name.adv", woodName, typeName);
    }

    private static String resolveDoorTypeName(String languageCode, Object doorType) {
        if (doorType == null) {
            return null;
        }

        String currentTypeName = extractCurrentLocalizedName(doorType);
        if (currentTypeName == null || currentTypeName.isEmpty()) {
            return null;
        }

        String translationKey = LanguageCache.findCurrentLanguageTranslationKey(
            currentTypeName,
            "extratrees.block.door.type.");
        if (translationKey == null || translationKey.trim().isEmpty()) {
            return currentTypeName;
        }

        String translated = LanguageCache.translate(languageCode, translationKey);
        return translated == null || translated.trim().isEmpty() ? currentTypeName : translated.trim();
    }

    private static String resolveExtraTreePlankName(String languageCode, int metadata) {
        Object plankType = getValueArrayEntry(EXTRA_TREE_PLANK_VALUES_CLASS_NAME, metadata);
        return resolvePlankTypeName(languageCode, plankType);
    }

    private static String resolveExtraTreeLogName(String languageCode, int metadata) {
        Object logType = getValueArrayEntry(EXTRA_TREE_LOG_VALUES_CLASS_NAME, metadata);
        String translated = translateByKey(languageCode, buildLogTranslationKey(logType));
        if (translated != null && !translated.isEmpty()) {
            return translated;
        }

        return resolveTranslatedLocalizedName(languageCode, logType, "extratrees.block.planks.");
    }

    private static Object getValueArrayEntry(String className, int metadata) {
        try {
            Class<?> valuesClass = Class.forName(className);
            Object values = resolveEnumValues(valuesClass);
            if (!(values instanceof Object[])) {
                return null;
            }

            Object[] entries = (Object[]) values;
            if (metadata < 0 || metadata >= entries.length) {
                return null;
            }

            return entries[metadata];
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object resolveEnumValues(Class<?> valuesClass) {
        if (valuesClass == null) {
            return null;
        }

        try {
            Field valuesField = valuesClass.getField("VALUES");
            return valuesField.get(null);
        } catch (Exception ignored) {
            // Some shipped Binnie builds do not expose a public VALUES field.
        }

        try {
            Method valuesMethod = valuesClass.getMethod("values");
            return valuesMethod.invoke(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String resolveWoodManagerPlankName(String languageCode, int metadata) {
        Object plankType = invokeStatic(WOOD_MANAGER_CLASS_NAME, "getPlankType", new Class<?>[] { int.class },
            Integer.valueOf(metadata));
        return resolvePlankTypeName(languageCode, plankType);
    }

    private static String resolvePlankTypeName(String languageCode, Object plankType) {
        String translated = translateByKey(languageCode, buildPlankTranslationKey(plankType));
        if (translated != null && !translated.isEmpty()) {
            return translated;
        }

        return resolveTranslatedLocalizedName(languageCode, plankType, "extratrees.block.planks.");
    }

    private static String extractCurrentLocalizedName(Object target) {
        Object value = invokeNoArg(target, "getName");
        return value instanceof String && !((String) value).trim().isEmpty() ? ((String) value).trim() : null;
    }

    private static String resolveTranslatedLocalizedName(String languageCode, Object target, String keyPrefix) {
        return translateCurrentLocalizedValue(languageCode, extractCurrentLocalizedName(target), keyPrefix);
    }

    private static String translateCurrentLocalizedValue(String languageCode, String currentLocalizedValue,
        String... keyPrefixes) {
        if (currentLocalizedValue == null || currentLocalizedValue.trim().isEmpty()) {
            return null;
        }

        String trimmed = currentLocalizedValue.trim();
        if (keyPrefixes != null) {
            for (String keyPrefix : keyPrefixes) {
                String translationKey = LanguageCache.findCurrentLanguageTranslationKey(trimmed, keyPrefix);
                if (translationKey == null || translationKey.trim().isEmpty()) {
                    continue;
                }

                String translated = LanguageCache.translate(languageCode, translationKey.trim());
                if (translated != null && !translated.trim().isEmpty()) {
                    return translated.trim();
                }
            }
        }

        return trimmed;
    }

    private static String buildPlankTranslationKey(Object plankType) {
        if (!(plankType instanceof Enum<?>)) {
            return null;
        }

        Enum<?> enumValue = (Enum<?>) plankType;
        String ownerClassName = enumValue.getDeclaringClass().getName();
        String enumName = normalizeEnumConstantName(enumValue.name());
        if (enumName == null || enumName.isEmpty()) {
            return null;
        }

        if (EXTRA_TREE_PLANK_VALUES_CLASS_NAME.equals(ownerClassName)) {
            return "extratrees.block.planks." + enumName;
        }
        if (VANILLA_PLANK_CLASS_NAME.equals(ownerClassName)) {
            return "extratrees.block.planks.vanilla." + enumName;
        }
        if (FORESTRY_PLANK_CLASS_NAME.equals(ownerClassName)) {
            return "extratrees.block.planks.forestry." + enumName;
        }
        if (EXTRA_BIOMES_PLANK_CLASS_NAME.equals(ownerClassName)) {
            return "extratrees.block.planks.ebxl." + enumName;
        }

        return null;
    }

    private static String buildLogTranslationKey(Object logType) {
        if (logType == null || !EXTRA_TREE_LOG_VALUES_CLASS_NAME.equals(logType.getClass().getName())) {
            return null;
        }

        try {
            Field nameField = logType.getClass().getDeclaredField("name");
            nameField.setAccessible(true);
            Object rawName = nameField.get(logType);
            if (rawName instanceof String && !((String) rawName).trim().isEmpty()) {
                return "extratrees.block.planks." + ((String) rawName).trim().toLowerCase();
            }
        } catch (Exception ignored) {
            // Fall back to current-language reverse lookup.
        }

        return null;
    }

    private static String normalizeEnumConstantName(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return null;
        }

        return rawName.trim().toLowerCase().replace("_", "");
    }

    private static String translateByKey(String languageCode, String translationKey) {
        if (translationKey == null || translationKey.trim().isEmpty()) {
            return null;
        }

        String translated = LanguageCache.translate(languageCode, translationKey.trim());
        return translated == null || translated.trim().isEmpty() ? null : translated.trim();
    }

    private static Object resolveCarpentryDesignBlock(Block block, int metadata) {
        Object designSystem = invokeNoArg(block, "getDesignSystem");
        if (designSystem == null) {
            return null;
        }

        try {
            Class<?> owner = Class.forName(MODULE_CARPENTRY_CLASS_NAME);
            Class<?> designSystemClass = Class.forName(DESIGN_SYSTEM_CLASS_NAME);
            Method method = owner.getMethod("getDesignBlock", designSystemClass, int.class);
            return method.invoke(null, designSystem, Integer.valueOf(metadata));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String buildMachineTranslationKey(Object machinePackage) {
        if (machinePackage == null) {
            return null;
        }

        String packageUid = extractString(machinePackage, "getUID");
        Object group = invokeNoArg(machinePackage, "getGroup");
        String groupUid = extractString(group, "getShortUID");
        Object mod = invokeNoArg(group, "getMod");
        String modId = extractString(mod, "getModID");
        if (packageUid == null || groupUid == null || modId == null) {
            return null;
        }

        return modId + ".machine." + groupUid + "." + packageUid;
    }

    private static String extractString(Object target, String methodName) {
        Object value = invokeNoArg(target, methodName);
        return value instanceof String && !((String) value).trim().isEmpty() ? ((String) value).trim() : null;
    }

    private static String format(String languageCode, String key, Object... args) {
        if (args == null || args.length == 0) {
            return null;
        }

        for (Object arg : args) {
            if (!(arg instanceof String) || ((String) arg).trim().isEmpty()) {
                return null;
            }
        }

        String translated = LanguageCache.format(languageCode, key, args);
        return translated == null || translated.trim().isEmpty() ? null : translated.trim();
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

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return null;
        }

        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String methodName) {
        return invoke(target, methodName, new Class<?>[0]);
    }

    private static int getMetadata(ItemStack stack) {
        if (stack == null) {
            return 0;
        }

        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(ITEM_META_KEY)) {
            return stack.getTagCompound().getInteger(ITEM_META_KEY);
        }

        return stack.getItemDamage();
    }

    private static boolean isBinnieClassName(String className) {
        return className != null && className.startsWith("binnie.");
    }
}
