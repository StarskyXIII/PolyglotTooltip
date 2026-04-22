package com.starskyxiii.polyglottooltip.name;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.starskyxiii.polyglottooltip.i18n.LanguageCache;

import net.minecraft.item.ItemStack;

final class GtNeiOrePluginDisplayNameResolver {

    private static final String ITEM_DIMENSION_DISPLAY_CLASS_NAME = "gtneioreplugin.plugin.item.ItemDimensionDisplay";
    private static final String DIMENSION_HELPER_CLASS_NAME = "gtneioreplugin.util.DimensionHelper";
    private static final String WORLD_KEY_PREFIX = "gtnop.world.";
    private static final String[] DIMENSION_PREFIXES = {
        "GalacticraftCore_",
        "GalacticraftMars_",
        "GalaxySpace_",
        "GalacticraftAmunRa_"
    };

    private GtNeiOrePluginDisplayNameResolver() {}

    static String tryResolveDisplayName(ItemStack stack, String languageCode) {
        if (stack == null || stack.getItem() == null || languageCode == null || languageCode.trim().isEmpty()) {
            return null;
        }

        if (!ITEM_DIMENSION_DISPLAY_CLASS_NAME.equals(stack.getItem().getClass().getName())) {
            return null;
        }

        Object dimensionCode = invokeStatic(
            ITEM_DIMENSION_DISPLAY_CLASS_NAME,
            "getDimension",
            new Class<?>[] { ItemStack.class },
            stack);
        if (!(dimensionCode instanceof String)) {
            return null;
        }

        String resolved = resolveCondensedDimensionDisplayName(languageCode.trim(), (String) dimensionCode);
        return resolved == null || resolved.trim().isEmpty() ? null : resolved.trim();
    }

    private static String resolveCondensedDimensionDisplayName(String languageCode, String condensedDimensionCode) {
        String[] dimensionTokens = parseDimensionTokens(condensedDimensionCode);
        if (dimensionTokens.length == 0) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < dimensionTokens.length; i++) {
            String resolvedToken = resolveDimensionToken(languageCode, dimensionTokens[i]);
            if (resolvedToken == null || resolvedToken.isEmpty()) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(resolvedToken);
        }

        return builder.length() == 0 ? null : builder.toString().trim();
    }

    private static String resolveDimensionToken(String languageCode, String dimensionToken) {
        String normalizedToken = normalize(dimensionToken);
        if (normalizedToken == null) {
            return null;
        }

        String trimmedDimensionName = resolveTrimmedDimensionName(normalizedToken);
        String translatedDimensionName = translateDimensionName(languageCode, trimmedDimensionName);
        if (translatedDimensionName == null || translatedDimensionName.isEmpty()) {
            translatedDimensionName = trimmedDimensionName != null ? trimmedDimensionName : normalizedToken;
        }

        String tierPrefix = resolveTierPrefix(trimmedDimensionName);
        return tierPrefix == null ? translatedDimensionName : tierPrefix + translatedDimensionName;
    }

    private static String resolveTrimmedDimensionName(String dimensionToken) {
        String[] displayedNames = getStaticStringArray(DIMENSION_HELPER_CLASS_NAME, "DimNameDisplayed");
        String[] trimmedNames = getStaticStringArray(DIMENSION_HELPER_CLASS_NAME, "DimNameTrimmed");

        if (displayedNames != null && trimmedNames != null) {
            int length = Math.min(displayedNames.length, trimmedNames.length);
            for (int i = 0; i < length; i++) {
                if (dimensionToken.equals(normalize(displayedNames[i]))) {
                    return normalize(trimmedNames[i]);
                }
            }
        }

        Object fullName = invokeStatic(DIMENSION_HELPER_CLASS_NAME, "getFullName", new Class<?>[] { String.class }, dimensionToken);
        if (fullName instanceof String) {
            return trimDimensionPrefix((String) fullName);
        }

        return null;
    }

    private static String translateDimensionName(String languageCode, String trimmedDimensionName) {
        String normalizedName = normalize(trimmedDimensionName);
        if (normalizedName == null) {
            return null;
        }

        String translated = LanguageCache.translate(languageCode, WORLD_KEY_PREFIX + normalizedName);
        return translated == null || translated.trim().isEmpty() ? normalizedName : translated.trim();
    }

    private static String resolveTierPrefix(String trimmedDimensionName) {
        String normalizedName = normalize(trimmedDimensionName);
        if (normalizedName == null) {
            return null;
        }

        if ("Moon".equals(normalizedName)) {
            return "T1:";
        }

        if (isOneOf(normalizedName, "Deimos", "Mars", "Phobos")) {
            return "T2:";
        }

        if (isOneOf(normalizedName, "Asteroids", "Callisto", "Ceres", "Europa", "Ganymede", "Ross128b")) {
            return "T3:";
        }

        if (isOneOf(normalizedName, "Io", "Mercury", "Venus")) {
            return "T4:";
        }

        if (isOneOf(normalizedName, "Enceladus", "Miranda", "Oberon", "Titan", "Ross128ba")) {
            return "T5:";
        }

        if (isOneOf(normalizedName, "Proteus", "Triton")) {
            return "T6:";
        }

        if (isOneOf(normalizedName, "Haumea", "Kuiperbelt", "MakeMake", "Pluto")) {
            return "T7:";
        }

        if (isOneOf(normalizedName, "BarnardC", "BarnardE", "BarnardF", "CentauriA", "TcetiE", "VegaB")) {
            return "T8:";
        }

        if (isOneOf(normalizedName, "Anubis", "Horus", "Maahes", "MehenBelt", "Neper", "Seth")) {
            return "T9:";
        }

        if ("Underdark".equals(normalizedName)) {
            return "T10:";
        }

        return null;
    }

    private static String[] parseDimensionTokens(String condensedDimensionCode) {
        Object parsed = invokeStatic(
            DIMENSION_HELPER_CLASS_NAME,
            "parseDimNames",
            new Class<?>[] { String.class },
            condensedDimensionCode);
        if (parsed instanceof String[]) {
            return (String[]) parsed;
        }

        String normalizedCode = normalize(condensedDimensionCode);
        return normalizedCode == null ? new String[0] : normalizedCode.split(",");
    }

    private static String[] getStaticStringArray(String className, String fieldName) {
        try {
            Class<?> owner = Class.forName(className);
            Field field = owner.getField(fieldName);
            Object value = field.get(null);
            return value instanceof String[] ? (String[]) value : null;
        } catch (Exception ignored) {
            return null;
        }
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

    private static String trimDimensionPrefix(String dimensionName) {
        String normalizedName = normalize(dimensionName);
        if (normalizedName == null) {
            return null;
        }

        for (int i = 0; i < DIMENSION_PREFIXES.length; i++) {
            String prefix = DIMENSION_PREFIXES[i];
            if (normalizedName.startsWith(prefix)) {
                return normalize(normalizedName.substring(prefix.length()));
            }
        }

        return normalizedName;
    }

    private static boolean isOneOf(String value, String... candidates) {
        if (value == null || candidates == null) {
            return false;
        }

        for (int i = 0; i < candidates.length; i++) {
            if (value.equals(candidates[i])) {
                return true;
            }
        }

        return false;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
