package com.starskyxiii.polyglottooltip.name;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import com.starskyxiii.polyglottooltip.i18n.GregTechMaterialTranslationResolver;
import com.starskyxiii.polyglottooltip.i18n.LanguageCache;

import net.minecraft.item.ItemStack;

final class GtPlusPlusDisplayNameResolver {

    private static final String ITEM_BOILER_CHASSIS_CLASS_NAME =
        "gtPlusPlus.core.item.general.chassis.ItemBoilerChassis";
    private static final String ITEM_DEHYDRATOR_COIL_WIRE_CLASS_NAME =
        "gtPlusPlus.core.item.general.chassis.ItemDehydratorCoilWire";
    private static final String ITEM_DEHYDRATOR_COIL_CLASS_NAME =
        "gtPlusPlus.core.item.general.chassis.ItemDehydratorCoil";
    private static final String META_ITEM_COVER_CASINGS_CLASS_NAME =
        "gtPlusPlus.xmod.gregtech.common.items.covers.MetaItemCoverCasings";
    private static final String ITEM_CUSTOM_SPAWN_EGG_CLASS_NAME =
        "gtPlusPlus.core.item.general.spawn.ItemCustomSpawnEgg";
    private static final String ITEM_GREGTECH_PUMP_CLASS_NAME =
        "gtPlusPlus.core.item.tool.misc.ItemGregtechPump";
    private static final String GTPP_COMB_CLASS_NAME =
        "gtPlusPlus.xmod.forestry.bees.items.output.GTPPComb";
    private static final String GTPP_DROP_CLASS_NAME =
        "gtPlusPlus.xmod.forestry.bees.items.output.GTPPDrop";
    private static final String GTPP_PROPOLIS_CLASS_NAME =
        "gtPlusPlus.xmod.forestry.bees.items.output.GTPPPropolis";
    private static final String GTPP_POLLEN_CLASS_NAME =
        "gtPlusPlus.xmod.forestry.bees.items.output.GTPPPollen";

    private static final String GTPP_COMB_ENUM_CLASS_NAME =
        "gtPlusPlus.xmod.forestry.bees.handler.GTPPCombType";
    private static final String GTPP_DROP_ENUM_CLASS_NAME =
        "gtPlusPlus.xmod.forestry.bees.handler.GTPPDropType";
    private static final String GTPP_PROPOLIS_ENUM_CLASS_NAME =
        "gtPlusPlus.xmod.forestry.bees.handler.GTPPPropolisType";
    private static final String GTPP_POLLEN_ENUM_CLASS_NAME =
        "gtPlusPlus.xmod.forestry.bees.handler.GTPPPollenType";
    private static final String ITEM_GREGTECH_PUMP_UNLOCALIZED_NAME = "item.MU-metatool.01";

    private static final String[] DEHYDRATOR_TIER_SHORT_NAMES = new String[] { "EV", "IV", "LuV", "ZPM" };
    private static final String[] GREGTECH_PUMP_FALLBACK_NAMES = new String[] {
        "Simple Hand Pump",
        "Advanced Hand Pump",
        "Super Hand Pump",
        "Ultimate Hand Pump",
        "Expandable Hand Pump"
    };
    private static final String[] COMPONENT_ORE_PREFIXES = new String[] {
        "plateSuperdense", "plateDense", "plateDouble", "gearGtSmall", "stickLong",
        "springSmall", "itemCasing", "gearGt", "spring", "rotor", "screw", "stick",
        "nugget", "plate", "ring", "gear", "bolt", "dustSmall", "dustTiny", "dust",
        "cellPlasma", "cell"
    };

    private static final String[] VOLTAGE_NAMES = new String[] {
        "Ultra Low Voltage", "Low Voltage", "Medium Voltage", "High Voltage",
        "Extreme Voltage", "Insane Voltage", "Ludicrous Voltage", "ZPM Voltage",
        "Ultimate Voltage", "Ultimate High Voltage", "Ultimate Extreme Voltage",
        "Ultimate Insane Voltage", "Ultimate Mega Voltage", "Ultimate Extended Mega Voltage",
        "Maximum Voltage"
    };

    private static Field spawnEggEntityNameMapField;
    private static Field spawnEggEntityFullNameMapField;
    private static boolean spawnEggReflectionInitialized;

    private static Field enumLocalizedSuffixField;
    private static Field enumDisplayNameField;
    private static Class<?> cachedEnumClass;

    private GtPlusPlusDisplayNameResolver() {}

    static String tryResolveDisplayName(ItemStack stack, String languageCode) {
        if (stack == null || stack.getItem() == null || languageCode == null || languageCode.trim().isEmpty()) {
            return null;
        }

        String className = stack.getItem().getClass().getName();

        String beeDisplayName = tryResolveBeeDisplayName(stack, languageCode, className);
        if (beeDisplayName != null && !beeDisplayName.isEmpty()) {
            return beeDisplayName;
        }

        if (ITEM_BOILER_CHASSIS_CLASS_NAME.equals(className)) {
            return formatWithEnglishFallback(
                languageCode,
                "item.itemBoilerChassis.name",
                "Advanced Boiler Chassis [Tier %d]",
                Integer.valueOf(stack.getItemDamage()));
        }

        if (ITEM_DEHYDRATOR_COIL_WIRE_CLASS_NAME.equals(className)) {
            return formatWithEnglishFallback(
                languageCode,
                "item.itemDehydratorCoilWire.name",
                "Coil Wire [%s]",
                getDehydratorTierName(stack.getItemDamage()));
        }

        if (ITEM_DEHYDRATOR_COIL_CLASS_NAME.equals(className)) {
            return formatWithEnglishFallback(
                languageCode,
                "item.itemDehydratorCoil.name",
                "Dehydrator Coil [%s]",
                getDehydratorTierName(stack.getItemDamage()));
        }

        if (META_ITEM_COVER_CASINGS_CLASS_NAME.equals(className)) {
            return formatWithEnglishFallback(
                languageCode,
                "item.itemCustomMetaCover.miscutils.GtMachineCasings",
                "%s Machine Plate Cover",
                getVoltageName(stack.getItemDamage()));
        }

        if (ITEM_CUSTOM_SPAWN_EGG_CLASS_NAME.equals(className)) {
            return tryResolveSpawnEggDisplayName(stack, languageCode);
        }

        if (ITEM_GREGTECH_PUMP_CLASS_NAME.equals(className)) {
            return tryResolvePumpDisplayName(stack, languageCode);
        }

        String componentDisplayName = tryResolveComponentDisplayName(stack, languageCode, className);
        if (componentDisplayName != null && !componentDisplayName.isEmpty()) {
            return componentDisplayName;
        }

        return null;
    }

    private static String tryResolveBeeDisplayName(ItemStack stack, String languageCode, String className) {
        if (GTPP_COMB_CLASS_NAME.equals(className)) {
            return translateBeeType(stack, languageCode, GTPP_COMB_ENUM_CLASS_NAME, "gtplusplus.comb.", " Comb");
        }

        if (GTPP_DROP_CLASS_NAME.equals(className)) {
            return translateBeeType(stack, languageCode, GTPP_DROP_ENUM_CLASS_NAME, "gtplusplus.drop.", " Drop");
        }

        if (GTPP_PROPOLIS_CLASS_NAME.equals(className)) {
            return translateBeeType(stack, languageCode, GTPP_PROPOLIS_ENUM_CLASS_NAME, "gtplusplus.propolis.", " Propolis");
        }

        if (GTPP_POLLEN_CLASS_NAME.equals(className)) {
            return translateBeeType(stack, languageCode, GTPP_POLLEN_ENUM_CLASS_NAME, "gtplusplus.pollen.", " Pollen");
        }

        return null;
    }

    private static String translateBeeType(ItemStack stack, String languageCode, String enumClassName,
        String translationKeyPrefix, String englishSuffix) {
        Object typeInstance = getTypeInstance(enumClassName, stack.getItemDamage());
        if (typeInstance == null) {
            return null;
        }

        String localizedSuffix = getTypeLocalizedSuffix(typeInstance);
        if (localizedSuffix != null && !localizedSuffix.isEmpty()) {
            String translated = LanguageCache.translate(languageCode, translationKeyPrefix + localizedSuffix);
            if (translated != null && !translated.trim().isEmpty()) {
                return translated.trim();
            }
        }

        String displayName = getTypeDisplayName(typeInstance);
        if (displayName == null || displayName.isEmpty()) {
            return null;
        }

        if ("en_US".equalsIgnoreCase(languageCode.trim())) {
            String trimmedDisplayName = displayName.trim();
            String trimmedSuffix = englishSuffix == null ? "" : englishSuffix.trim();
            if (trimmedSuffix.isEmpty() || trimmedDisplayName.endsWith(trimmedSuffix)) {
                return trimmedDisplayName;
            }
            return trimmedDisplayName + englishSuffix;
        }

        return null;
    }

    private static Object getTypeInstance(String enumClassName, int metadata) {
        try {
            Class<?> enumClass = Class.forName(enumClassName);
            Method getMethod = enumClass.getMethod("get", int.class);
            Object resolved = getMethod.invoke(null, metadata);
            if (resolved != null) {
                return resolved;
            }

            Object values = enumClass.getMethod("values")
                .invoke(null);
            if (values instanceof Object[]) {
                Object[] entries = (Object[]) values;
                if (metadata >= 0 && metadata < entries.length) {
                    return entries[metadata];
                }
            }
        } catch (Exception ignored) {
            // Fall through to null.
        }

        return null;
    }

    private static synchronized String getTypeLocalizedSuffix(Object typeInstance) {
        if (typeInstance == null) {
            return null;
        }

        Class<?> enumClass = typeInstance.getClass();
        initializeEnumReflection(enumClass);
        if (enumLocalizedSuffixField == null || cachedEnumClass == null || !cachedEnumClass.isAssignableFrom(enumClass)) {
            return null;
        }

        try {
            Object value = enumLocalizedSuffixField.get(typeInstance);
            if (value instanceof String && !((String) value).trim().isEmpty()) {
                return ((String) value).trim();
            }
        } catch (Exception ignored) {
            // Fall through to method-based lookup.
        }

        Object localizedName = invokeNoArg(typeInstance, "getLocalizedName");
        if (localizedName instanceof String && !((String) localizedName).trim().isEmpty()) {
            String translationKey = LanguageCache.findCurrentLanguageTranslationKey(
                ((String) localizedName).trim(),
                "gtplusplus.");
            if (translationKey != null && !translationKey.trim().isEmpty()) {
                int separatorIndex = translationKey.lastIndexOf('.');
                if (separatorIndex > 0 && separatorIndex + 1 < translationKey.length()) {
                    return translationKey.substring(separatorIndex + 1).trim();
                }
            }
        }

        return null;
    }

    private static synchronized String getTypeDisplayName(Object typeInstance) {
        if (typeInstance == null) {
            return null;
        }

        Class<?> enumClass = typeInstance.getClass();
        initializeEnumReflection(enumClass);
        if (enumDisplayNameField == null || cachedEnumClass == null || !cachedEnumClass.isAssignableFrom(enumClass)) {
            return null;
        }

        try {
            Object value = enumDisplayNameField.get(typeInstance);
            if (value instanceof String && !((String) value).trim().isEmpty()) {
                return ((String) value).trim();
            }
        } catch (Exception ignored) {
            // Fall through to method-based lookup.
        }

        Object value = invokeNoArg(typeInstance, "getName");
        if (value instanceof String && !((String) value).trim().isEmpty()) {
            return ((String) value).trim();
        }

        value = invokeNoArg(typeInstance, "getLocalizedName");
        return value instanceof String && !((String) value).trim().isEmpty() ? ((String) value).trim() : null;
    }

    private static synchronized void initializeEnumReflection(Class<?> enumClass) {
        if (enumClass == null) {
            return;
        }

        if (cachedEnumClass == enumClass) {
            return;
        }

        cachedEnumClass = enumClass;
        enumLocalizedSuffixField = findField(enumClass, "mNameUnlocal");
        enumDisplayNameField = findField(enumClass, "mName");
    }

    private static String tryResolveSpawnEggDisplayName(ItemStack stack, String languageCode) {
        String entityName = getSpawnEggEntityName(stack.getItemDamage());
        String entityFullName = getSpawnEggEntityFullName(stack.getItemDamage());
        String translatedEntity = tryResolveSpawnEggEntityName(languageCode, entityName, entityFullName);
        if (translatedEntity == null || translatedEntity.trim().isEmpty()) {
            translatedEntity = prettifyEntityName(entityName != null ? entityName : entityFullName);
        }
        if (translatedEntity == null || translatedEntity.trim().isEmpty()) {
            return null;
        }

        return formatWithEnglishFallback(
            languageCode,
            "item.ItemCustomSpawnEgg.name",
            "Spawn %s",
            translatedEntity.trim());
    }

    private static String tryResolveSpawnEggEntityName(String languageCode, String entityName, String entityFullName) {
        String translatedEntity = translateEntityName(languageCode, entityName);
        if (translatedEntity != null && !translatedEntity.trim().isEmpty()) {
            return translatedEntity.trim();
        }

        translatedEntity = translateEntityName(languageCode, entityFullName);
        if (translatedEntity != null && !translatedEntity.trim().isEmpty()) {
            return translatedEntity.trim();
        }

        if (entityFullName != null) {
            int separatorIndex = entityFullName.indexOf('.');
            if (separatorIndex > 0 && separatorIndex + 1 < entityFullName.length()) {
                translatedEntity = translateEntityName(languageCode, entityFullName.substring(separatorIndex + 1));
                if (translatedEntity != null && !translatedEntity.trim().isEmpty()) {
                    return translatedEntity.trim();
                }
            }
        }

        return null;
    }

    private static String translateEntityName(String languageCode, String entityName) {
        if (entityName == null || entityName.isEmpty()) {
            return null;
        }

        String translatedEntity = LanguageCache.translate(languageCode, "entity." + entityName + ".name");
        if (translatedEntity == null || translatedEntity.trim().isEmpty()) {
            translatedEntity = LanguageCache.translate(languageCode, "entity.miscutils." + entityName + ".name");
        }
        return translatedEntity == null || translatedEntity.trim().isEmpty() ? null : translatedEntity.trim();
    }

    @SuppressWarnings("unchecked")
    private static synchronized String getSpawnEggEntityName(int metadata) {
        initializeSpawnEggReflection();
        if (spawnEggEntityNameMapField == null) {
            return null;
        }

        try {
            Object value = spawnEggEntityNameMapField.get(null);
            if (!(value instanceof Map)) {
                return null;
            }

            Object name = ((Map<Integer, String>) value).get(Integer.valueOf(metadata));
            return name instanceof String ? ((String) name).trim() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static synchronized String getSpawnEggEntityFullName(int metadata) {
        initializeSpawnEggReflection();
        if (spawnEggEntityFullNameMapField == null) {
            return null;
        }

        try {
            Object value = spawnEggEntityFullNameMapField.get(null);
            if (!(value instanceof Map)) {
                return null;
            }

            Object name = ((Map<Integer, String>) value).get(Integer.valueOf(metadata));
            return name instanceof String ? ((String) name).trim() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static synchronized void initializeSpawnEggReflection() {
        if (spawnEggReflectionInitialized) {
            return;
        }

        spawnEggReflectionInitialized = true;
        try {
            Class<?> spawnEggClass = Class.forName(ITEM_CUSTOM_SPAWN_EGG_CLASS_NAME);
            spawnEggEntityNameMapField = findField(spawnEggClass, "mEntityNameMap");
            spawnEggEntityFullNameMapField = findField(spawnEggClass, "mEntityFullNameMap");
        } catch (Exception ignored) {
            spawnEggEntityNameMapField = null;
            spawnEggEntityFullNameMapField = null;
        }
    }

    private static String tryResolvePumpDisplayName(ItemStack stack, String languageCode) {
        int correctedMeta = Math.max(0, Math.min(4, stack.getItemDamage() - 1000));
        String unlocalizedName = stack.getUnlocalizedName();
        if (unlocalizedName == null || unlocalizedName.trim().isEmpty()) {
            return null;
        }

        String normalized = unlocalizedName.startsWith("item.") ? unlocalizedName.substring(5) : unlocalizedName;
        String translated = LanguageCache.translate(languageCode, "gtplusplus." + normalized + "." + correctedMeta + ".name");
        if (translated == null || translated.trim().isEmpty()) {
            translated = LanguageCache.translate(languageCode, "gtplusplus." + unlocalizedName + "." + correctedMeta + ".name");
        }
        if ((translated == null || translated.trim().isEmpty())
            && ITEM_GREGTECH_PUMP_UNLOCALIZED_NAME.equals(unlocalizedName)
            && correctedMeta >= 0
            && correctedMeta < GREGTECH_PUMP_FALLBACK_NAMES.length
            && "en_US".equalsIgnoreCase(languageCode.trim())) {
            translated = GREGTECH_PUMP_FALLBACK_NAMES[correctedMeta];
        }

        return translated == null || translated.trim().isEmpty() ? null : translated.trim();
    }

    private static String tryResolveComponentDisplayName(ItemStack stack, String languageCode, String className) {
        if (!className.startsWith("gtPlusPlus.core.item.base.")) {
            return null;
        }

        List<String> oreDictionaryNames = GregTechMaterialTranslationResolver.collectOreDictionaryNames(stack);
        if (oreDictionaryNames.isEmpty()) {
            return null;
        }

        String translatedMaterial = GregTechMaterialTranslationResolver.resolveMaterialTranslation(
            languageCode,
            stack.getUnlocalizedName() + ".name",
            stack);
        if (translatedMaterial == null || translatedMaterial.trim().isEmpty()) {
            return null;
        }

        for (String oreDictionaryName : oreDictionaryNames) {
            String template = getComponentTemplate(oreDictionaryName);
            if (template != null && !template.isEmpty()) {
                return String.format(template, translatedMaterial.trim());
            }
        }

        return null;
    }

    private static String getComponentTemplate(String oreDictionaryName) {
        if (oreDictionaryName == null || oreDictionaryName.isEmpty()) {
            return null;
        }

        for (String prefix : COMPONENT_ORE_PREFIXES) {
            if (!oreDictionaryName.startsWith(prefix)) {
                continue;
            }

            if ("plateSuperdense".equals(prefix)) {
                return "Superdense %s Plate";
            }
            if ("plateDense".equals(prefix)) {
                return "Dense %s Plate";
            }
            if ("plateDouble".equals(prefix)) {
                return "Double %s Plate";
            }
            if ("gearGtSmall".equals(prefix)) {
                return "Small %s Gear";
            }
            if ("stickLong".equals(prefix)) {
                return "Long %s Rod";
            }
            if ("springSmall".equals(prefix)) {
                return "Small %s Spring";
            }
            if ("itemCasing".equals(prefix)) {
                return "%s Casing";
            }
            if ("gearGt".equals(prefix)) {
                return "%s Gear";
            }
            if ("spring".equals(prefix)) {
                return "%s Spring";
            }
            if ("rotor".equals(prefix)) {
                return "%s Rotor";
            }
            if ("screw".equals(prefix)) {
                return "%s Screw";
            }
            if ("stick".equals(prefix)) {
                return "%s Rod";
            }
            if ("nugget".equals(prefix)) {
                return "%s Nugget";
            }
            if ("plate".equals(prefix)) {
                return "%s Plate";
            }
            if ("ring".equals(prefix)) {
                return "%s Ring";
            }
            if ("gear".equals(prefix)) {
                return "%s Gear";
            }
            if ("bolt".equals(prefix)) {
                return "%s Bolt";
            }
            if ("dustSmall".equals(prefix)) {
                return "Small Pile of %s Dust";
            }
            if ("dustTiny".equals(prefix)) {
                return "Tiny Pile of %s Dust";
            }
            if ("dust".equals(prefix)) {
                return "%s Dust";
            }
            if ("cellPlasma".equals(prefix)) {
                return "%s Plasma Cell";
            }
            if ("cell".equals(prefix)) {
                return "%s Cell";
            }
        }

        return null;
    }

    private static String getDehydratorTierName(int metadata) {
        if (metadata < 0 || metadata >= DEHYDRATOR_TIER_SHORT_NAMES.length) {
            return "";
        }

        return DEHYDRATOR_TIER_SHORT_NAMES[metadata];
    }

    private static String getVoltageName(int metadata) {
        if (metadata < 0) {
            return "";
        }

        metadata = Math.min(metadata, VOLTAGE_NAMES.length - 1);
        return VOLTAGE_NAMES[metadata];
    }

    private static String formatWithEnglishFallback(String languageCode, String translationKey, String englishTemplate,
        Object... args) {
        String formatted = LanguageCache.format(languageCode, translationKey, args);
        if (formatted != null && !formatted.trim().isEmpty()) {
            return formatted.trim();
        }

        if (!"en_US".equalsIgnoreCase(languageCode.trim()) || englishTemplate == null || englishTemplate.isEmpty()) {
            return null;
        }

        try {
            return String.format(englishTemplate, args).trim();
        } catch (Exception ignored) {
            return englishTemplate.trim();
        }
    }

    private static String prettifyEntityName(String entityName) {
        if (entityName == null || entityName.trim().isEmpty()) {
            return null;
        }

        String value = entityName.trim();
        int separatorIndex = value.lastIndexOf('.');
        if (separatorIndex >= 0 && separatorIndex + 1 < value.length()) {
            value = value.substring(separatorIndex + 1);
        }

        String spaced = value.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
            .replace('_', ' ')
            .trim();
        if (spaced.isEmpty()) {
            return null;
        }

        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static Field findField(Class<?> owner, String fieldName) {
        if (owner == null || fieldName == null || fieldName.isEmpty()) {
            return null;
        }

        try {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null || methodName == null || methodName.isEmpty()) {
            return null;
        }

        try {
            Method method = target.getClass()
                .getMethod(methodName);
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }
}
