package com.starskyxiii.polyglottooltip.i18n;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

public final class GregTechMaterialTranslationResolver {

    private static final Pattern CABLE_OR_WIRE_KEY_PATTERN =
        Pattern.compile("^gt\\.blockmachines\\.(?:cable|wire)\\.([^.]+)\\.[^.]+\\.name$");
    private static final Pattern FRAME_KEY_PATTERN =
        Pattern.compile("^gt\\.blockmachines\\.gt_frame_([^.]+)\\.name$");
    private static final Pattern PIPE_KEY_PATTERN =
        Pattern.compile("^gt\\.blockmachines\\.gt_pipe_(?:restrictive_)?(.+?)"
            + "(?:_(?:huge|large|small|tiny|nonuple|quadruple))?\\.name$");
    private static final Pattern BW_ITEMTYPE_PATTERN =
        Pattern.compile("^bw\\.itemtype\\.[^.]+$");
    private static final String[] ORE_DICTIONARY_PREFIXES = new String[] {
        "plateSuperdense", "plateDouble", "plateDense", "gearGtSmall", "springSmall",
        "stickLong", "wireFine", "itemCasing", "frameGt", "gearGt", "rawOre",
        "cellMolten", "spring", "rotor", "round", "screw", "stick", "nugget",
        "ingot", "plate", "bolt", "foil", "ring", "gear", "wire", "dust",
        "cell", "ore"
    };

    private static Class<?> materialsClass;
    private static Field materialNameField;
    private static Field generatedMaterialsField;
    private static boolean materialsReflectionInitialized;

    private static Field itemBlockField;
    private static Class<?> blockMetalClass;
    private static Class<?> blockSheetMetalClass;
    private static Field blockMetalMaterialsField;
    private static Field blockSheetMetalMaterialsField;
    private static Method int2ObjectGetMethod;
    private static Method oreMaterialInternalNameMethod;
    private static boolean storageBlockReflectionInitialized;

    private GregTechMaterialTranslationResolver() {}

    public static String resolveMaterialTranslation(String languageCode, String translationKey, ItemStack stack) {
        if (languageCode == null || languageCode.trim().isEmpty()) {
            return null;
        }
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        MaterialResolution resolution = resolveMaterial(translationKey, stack, languageCode.trim(), null);
        if (resolution.translationValue == null || resolution.translationValue.isEmpty()) {
            return null;
        }

        return resolution.translationValue;
    }

    static String resolveMaterialTranslation(Map<String, String> targetTranslations, String translationKey, ItemStack stack) {
        if (targetTranslations == null || targetTranslations.isEmpty()) {
            return null;
        }
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        if (!usesGregTechMaterialFormatting(translationKey)) {
            return null;
        }

        MaterialResolution resolution = resolveMaterial(translationKey, stack, null, targetTranslations);
        if (resolution.translationValue == null || resolution.translationValue.isEmpty()) {
            return null;
        }

        return resolution.translationValue;
    }

    public static List<String> collectOreDictionaryNames(ItemStack stack) {
        ArrayList<String> names = new ArrayList<String>();
        if (stack == null || stack.getItem() == null) {
            return names;
        }

        int[] oreIds = OreDictionary.getOreIDs(stack);
        if (oreIds == null || oreIds.length == 0) {
            return names;
        }

        Set<String> dedupedNames = new LinkedHashSet<String>();
        for (int oreId : oreIds) {
            if (oreId < 0) {
                continue;
            }

            String oreName = OreDictionary.getOreName(oreId);
            if (oreName != null && !oreName.trim().isEmpty()) {
                dedupedNames.add(oreName.trim());
            }
        }

        names.addAll(dedupedNames);
        return names;
    }

    public static String describeMaterialResolution(String languageCode, String translationKey, ItemStack stack) {
        MaterialResolution resolution = resolveMaterial(translationKey, stack, languageCode, null);
        StringBuilder builder = new StringBuilder();
        builder.append("source=").append(resolution.source);
        builder.append(";materialName=").append(safeValue(resolution.materialName));
        builder.append(";materialKey=").append(safeValue(resolution.translationKey));
        builder.append(";materialValue=").append(safeValue(resolution.translationValue));
        builder.append(";matchedOreDict=").append(safeValue(resolution.matchedOreDictionaryName));
        return builder.toString();
    }

    private static boolean usesGregTechMaterialFormatting(String translationKey) {
        if (translationKey == null || translationKey.isEmpty()) {
            return false;
        }

        return translationKey.startsWith("gt.")
            || translationKey.startsWith("gtplusplus.")
            || translationKey.startsWith("bw.");
    }

    private static MaterialResolution resolveMaterial(String translationKey, ItemStack stack, String languageCode,
        Map<String, String> targetTranslations) {
        MaterialResolution resolution = new MaterialResolution();
        String materialName = getMaterialNameFromTranslationKey(translationKey);
        if (materialName != null && !materialName.isEmpty()) {
            resolution.source = "translation_key";
            resolution.materialName = materialName;
            populateTranslation(resolution, languageCode, targetTranslations);
            return resolution;
        }

        materialName = getMaterialNameFromStorageBlock(stack);
        if (materialName != null && !materialName.isEmpty()) {
            resolution.source = "storage_block";
            resolution.materialName = materialName;
            populateTranslation(resolution, languageCode, targetTranslations);
            return resolution;
        }

        OreDictionaryMatch oreDictionaryMatch = getMaterialNameFromOreDictionary(stack, languageCode, targetTranslations);
        if (oreDictionaryMatch != null && oreDictionaryMatch.materialName != null && !oreDictionaryMatch.materialName.isEmpty()) {
            resolution.source = "oredict";
            resolution.materialName = oreDictionaryMatch.materialName;
            resolution.matchedOreDictionaryName = oreDictionaryMatch.oreDictionaryName;
            populateTranslation(resolution, languageCode, targetTranslations);
            return resolution;
        }

        materialName = getMaterialNameFromIndexedMetadata(stack);
        if (materialName != null && !materialName.isEmpty()) {
            resolution.source = "metadata";
            resolution.materialName = materialName;
            populateTranslation(resolution, languageCode, targetTranslations);
            return resolution;
        }

        return resolution;
    }

    private static String getMaterialNameFromTranslationKey(String translationKey) {
        if (translationKey == null || translationKey.isEmpty()) {
            return null;
        }

        Matcher matcher = CABLE_OR_WIRE_KEY_PATTERN.matcher(translationKey);
        if (matcher.matches()) {
            return matcher.group(1);
        }

        matcher = FRAME_KEY_PATTERN.matcher(translationKey);
        if (matcher.matches()) {
            return matcher.group(1);
        }

        matcher = PIPE_KEY_PATTERN.matcher(translationKey);
        if (matcher.matches()) {
            return normalizePipeMaterialSlug(matcher.group(1));
        }

        if (BW_ITEMTYPE_PATTERN.matcher(translationKey).matches()) {
            return null;
        }

        return null;
    }

    private static String normalizePipeMaterialSlug(String materialSlug) {
        if (materialSlug == null) {
            return null;
        }

        String normalized = materialSlug.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        if ("supercoolant".equals(normalized)) {
            return "stainlesssteel";
        }

        return normalized;
    }

    private static String getMaterialNameFromStorageBlock(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        initializeStorageBlockReflection();
        if (itemBlockField == null) {
            return null;
        }

        try {
            Object block = itemBlockField.get(stack.getItem());
            if (block == null) {
                return null;
            }

            int metadata = stack.getItemDamage();
            if (blockMetalClass != null && blockMetalClass.isInstance(block) && blockMetalMaterialsField != null) {
                Object materials = blockMetalMaterialsField.get(block);
                Object material = getArrayElement(materials, metadata);
                return extractMaterialName(material);
            }

            if (blockSheetMetalClass != null
                && blockSheetMetalClass.isInstance(block)
                && blockSheetMetalMaterialsField != null
                && int2ObjectGetMethod != null) {
                Object materials = blockSheetMetalMaterialsField.get(block);
                if (materials == null) {
                    return null;
                }

                Object material = int2ObjectGetMethod.invoke(materials, metadata);
                return extractMaterialName(material);
            }
        } catch (Exception ignored) {
            return null;
        }

        return null;
    }

    private static OreDictionaryMatch getMaterialNameFromOreDictionary(ItemStack stack, String languageCode,
        Map<String, String> targetTranslations) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        int[] oreIds = OreDictionary.getOreIDs(stack);
        if (oreIds == null || oreIds.length == 0) {
            return null;
        }

        for (int oreId : oreIds) {
            if (oreId < 0) {
                continue;
            }

            String oreName = OreDictionary.getOreName(oreId);
            String materialName = extractMaterialNameFromOreName(oreName, languageCode, targetTranslations);
            if (materialName != null && !materialName.isEmpty()) {
                return new OreDictionaryMatch(oreName, materialName);
            }
        }

        return null;
    }

    private static String extractMaterialNameFromOreName(String oreName, String languageCode,
        Map<String, String> targetTranslations) {
        if (oreName == null || oreName.isEmpty()) {
            return null;
        }

        for (String prefix : ORE_DICTIONARY_PREFIXES) {
            if (!oreName.startsWith(prefix) || oreName.length() <= prefix.length()) {
                continue;
            }

            String candidate = oreName.substring(prefix.length()).trim();
            if (candidate.isEmpty()) {
                continue;
            }

            if (hasMaterialTranslation(candidate, languageCode, targetTranslations)) {
                return candidate;
            }
        }

        for (int i = 0; i < oreName.length(); i++) {
            char current = oreName.charAt(i);
            if (!Character.isUpperCase(current)) {
                continue;
            }

            String candidate = oreName.substring(i).trim();
            if (candidate.isEmpty()) {
                continue;
            }

            if (hasMaterialTranslation(candidate, languageCode, targetTranslations)) {
                return candidate;
            }
        }

        return null;
    }

    private static String getMaterialNameFromIndexedMetadata(ItemStack stack) {
        if (stack == null || stack.getItem() == null || !isIndexedMaterialItem(stack)) {
            return null;
        }

        initializeMaterialsReflection();
        if (materialsClass == null || generatedMaterialsField == null) {
            return null;
        }

        int materialIndex = stack.getItemDamage() % 1000;
        if (materialIndex < 0) {
            return null;
        }

        try {
            Object generatedMaterials = generatedMaterialsField.get(null);
            Object material = getArrayElement(generatedMaterials, materialIndex);
            return extractMaterialName(material);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isIndexedMaterialItem(ItemStack stack) {
        Object item = stack.getItem();
        if (item == null) {
            return false;
        }

        String className = item.getClass().getName();
        return "gregtech.common.blocks.ItemOres".equals(className)
            || "gregtech.common.blocks.ItemFrames".equals(className)
            || className.startsWith("gregtech.common.items.MetaGeneratedItem");
    }

    private static Object getArrayElement(Object array, int index) {
        if (array == null || !array.getClass().isArray() || index < 0) {
            return null;
        }

        int length = Array.getLength(array);
        if (index >= length) {
            return null;
        }

        return Array.get(array, index);
    }

    private static String extractMaterialName(Object material) {
        if (material == null) {
            return null;
        }

        initializeMaterialsReflection();
        if (materialNameField != null && materialNameField.getDeclaringClass().isInstance(material)) {
            try {
                Object value = materialNameField.get(material);
                if (value instanceof String && !((String) value).trim().isEmpty()) {
                    return ((String) value).trim();
                }
            } catch (Exception ignored) {
                // Fall through to other strategies.
            }
        }

        initializeStorageBlockReflection();
        if (oreMaterialInternalNameMethod != null
            && oreMaterialInternalNameMethod.getDeclaringClass().isInstance(material)) {
            try {
                Object value = oreMaterialInternalNameMethod.invoke(material);
                if (value instanceof String && !((String) value).trim().isEmpty()) {
                    return ((String) value).trim();
                }
            } catch (Exception ignored) {
                // Fall through to generic reflection.
            }
        }

        if (material instanceof Enum<?>) {
            return ((Enum<?>) material).name();
        }

        try {
            Method nameMethod = material.getClass()
                .getMethod("name");
            Object value = nameMethod.invoke(material);
            return value instanceof String ? ((String) value).trim() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void initializeMaterialsReflection() {
        if (materialsReflectionInitialized) {
            return;
        }

        materialsReflectionInitialized = true;
        try {
            materialsClass = Class.forName("gregtech.api.enums.Materials");
            materialNameField = materialsClass.getField("mName");
            materialNameField.setAccessible(true);

            Class<?> gregTechApiClass = Class.forName("gregtech.api.GregTechAPI");
            generatedMaterialsField = gregTechApiClass.getField("sGeneratedMaterials");
            generatedMaterialsField.setAccessible(true);
        } catch (Exception ignored) {
            materialsClass = null;
            materialNameField = null;
            generatedMaterialsField = null;
        }
    }

    private static void initializeStorageBlockReflection() {
        if (storageBlockReflectionInitialized) {
            return;
        }

        storageBlockReflectionInitialized = true;

        try {
            Class<?> itemBlockClass = Class.forName("net.minecraft.item.ItemBlock");
            itemBlockField = findField(itemBlockClass, "block", "field_150939_a");
        } catch (Exception ignored) {
            itemBlockField = null;
        }

        try {
            blockMetalClass = Class.forName("gregtech.common.blocks.BlockMetal");
            blockMetalMaterialsField = blockMetalClass.getDeclaredField("mMats");
            blockMetalMaterialsField.setAccessible(true);
        } catch (Exception ignored) {
            blockMetalClass = null;
            blockMetalMaterialsField = null;
        }

        try {
            blockSheetMetalClass = Class.forName("gregtech.common.blocks.BlockSheetMetal");
            blockSheetMetalMaterialsField = blockSheetMetalClass.getDeclaredField("materials");
            blockSheetMetalMaterialsField.setAccessible(true);
        } catch (Exception ignored) {
            blockSheetMetalClass = null;
            blockSheetMetalMaterialsField = null;
        }

        try {
            Class<?> int2ObjectFunctionClass = Class.forName("it.unimi.dsi.fastutil.ints.Int2ObjectFunction");
            int2ObjectGetMethod = int2ObjectFunctionClass.getMethod("get", int.class);
        } catch (Exception ignored) {
            int2ObjectGetMethod = null;
        }

        try {
            Class<?> oreMaterialClass = Class.forName("gregtech.api.interfaces.IOreMaterial");
            oreMaterialInternalNameMethod = oreMaterialClass.getMethod("getInternalName");
        } catch (Exception ignored) {
            oreMaterialInternalNameMethod = null;
        }
    }

    private static Field findField(Class<?> owner, String... candidateNames) {
        if (owner == null || candidateNames == null) {
            return null;
        }

        for (String candidateName : candidateNames) {
            if (candidateName == null || candidateName.isEmpty()) {
                continue;
            }

            try {
                Field field = owner.getDeclaredField(candidateName);
                field.setAccessible(true);
                return field;
            } catch (Exception ignored) {
                // Try next candidate.
            }
        }

        return null;
    }

    private static String[] getCandidateKeys(String materialName) {
        return new String[] {
            "Material." + materialName.toLowerCase(Locale.ROOT),
            "Material." + materialName,
            "gtplusplus.material." + materialName,
            "gtplusplus.material." + materialName.toLowerCase(Locale.ROOT)
        };
    }

    private static boolean hasMaterialTranslation(String materialName, String languageCode,
        Map<String, String> targetTranslations) {
        if (materialName == null || materialName.trim().isEmpty()) {
            return false;
        }

        if (targetTranslations != null) {
            return findTranslation(targetTranslations, materialName) != null;
        }

        if (languageCode != null && !languageCode.trim().isEmpty()) {
            return findTranslation(languageCode, materialName) != null;
        }

        return false;
    }

    private static String findTranslation(Map<String, String> targetTranslations, String materialName) {
        TranslationMatch match = findTranslationMatch(targetTranslations, materialName);
        return match == null ? null : match.value;
    }

    private static String findTranslation(String languageCode, String materialName) {
        TranslationMatch match = findTranslationMatch(languageCode, materialName);
        return match == null ? null : match.value;
    }

    private static TranslationMatch findTranslationMatch(Map<String, String> targetTranslations, String materialName) {
        for (String candidateKey : getCandidateKeys(materialName)) {
            String translated = targetTranslations.get(candidateKey);
            if (translated != null && !translated.trim().isEmpty()) {
                return new TranslationMatch(candidateKey, translated.trim());
            }
        }

        return null;
    }

    private static TranslationMatch findTranslationMatch(String languageCode, String materialName) {
        for (String candidateKey : getCandidateKeys(materialName)) {
            String translated = LanguageCache.translate(languageCode, candidateKey);
            if (translated != null && !translated.trim().isEmpty()) {
                return new TranslationMatch(candidateKey, translated.trim());
            }
        }

        return null;
    }

    private static void populateTranslation(MaterialResolution resolution, String languageCode,
        Map<String, String> targetTranslations) {
        if (resolution == null || resolution.materialName == null || resolution.materialName.trim().isEmpty()) {
            return;
        }

        TranslationMatch match;
        if (targetTranslations != null) {
            match = findTranslationMatch(targetTranslations, resolution.materialName);
        } else if (languageCode != null && !languageCode.trim().isEmpty()) {
            match = findTranslationMatch(languageCode.trim(), resolution.materialName);
        } else {
            match = null;
        }

        if (match == null) {
            return;
        }

        resolution.translationKey = match.key;
        resolution.translationValue = match.value;
    }

    private static String safeValue(String value) {
        return value == null ? "" : value;
    }

    private static final class OreDictionaryMatch {

        private final String oreDictionaryName;
        private final String materialName;

        private OreDictionaryMatch(String oreDictionaryName, String materialName) {
            this.oreDictionaryName = oreDictionaryName;
            this.materialName = materialName;
        }
    }

    private static final class TranslationMatch {

        private final String key;
        private final String value;

        private TranslationMatch(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    private static final class MaterialResolution {

        private String source = "none";
        private String materialName;
        private String translationKey;
        private String translationValue;
        private String matchedOreDictionaryName;
    }
}
