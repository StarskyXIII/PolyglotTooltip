package com.starskyxiii.polyglottooltip.name;

import java.lang.reflect.Field;
import java.util.Map;

import com.starskyxiii.polyglottooltip.i18n.LanguageCache;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Resolves display names for Tinkers' Construct modular tools by replicating the logic of
 * {@code ToolBuilder.defaultToolName()} using {@link LanguageCache} instead of
 * {@code StatCollector}. This makes name resolution language-independent and avoids the
 * issue where {@code StatCollector} returns the current game language rather than the
 * requested secondary language.
 *
 * <p>Detection: a stack is treated as a TConstruct tool if its NBT contains an
 * {@code InfiTool} compound with a {@code Head} integer key (the head material ID).
 *
 * <p>Naming priority mirrors TConstruct:
 * <ol>
 *   <li>Specific key {@code tool.<toolname>.<matname>} (e.g. {@code tool.pickaxe.iron})</li>
 *   <li>Format string {@code tool.nameformat} applied to material prefix + tool type name</li>
 * </ol>
 */
final class TinkerConstructDisplayNameResolver {

    private static final String REGISTRY_CLASS = "tconstruct.library.TConstructRegistry";
    private static final String TOOL_MATERIALS_FIELD = "toolMaterials";
    private static final String LOC_STRING_FIELD = "localizationString";
    private static final String MAT_NAME_FIELD = "materialName";

    private static final String KEY_NAMEFORMAT = "tool.nameformat";
    private static final String FALLBACK_NAMEFORMAT = "%s %s";

    /** Guarded by class init; set once, never reset. */
    private static volatile boolean reflectionAttempted = false;
    private static volatile boolean reflectionReady = false;
    private static Field toolMaterialsField;
    private static Field locStringField;
    private static Field matNameField;

    private TinkerConstructDisplayNameResolver() {}

    /**
     * Attempts to resolve the display name for the given stack in the requested language.
     *
     * @return the localised name (e.g. {@code "Iron Pickaxe"}), or {@code null} if the
     *         stack is not a TConstruct tool or resolution fails.
     */
    static String tryResolveDisplayName(ItemStack stack, String languageCode) {
        if (!isTinkerTool(stack)) {
            return null;
        }

        if (!ensureReflection()) {
            return null;
        }

        int headId;
        try {
            NBTTagCompound infiTool = stack.getTagCompound().getCompoundTag("InfiTool");
            headId = infiTool.getInteger("Head");
        } catch (Exception e) {
            return null;
        }

        Object material = getMaterial(headId);
        if (material == null) {
            return null;
        }

        String locStr;
        String materialName;
        try {
            locStr = (String) locStringField.get(material);
            materialName = (String) matNameField.get(material);
        } catch (Exception e) {
            return null;
        }

        if (locStr == null || materialName == null) {
            return null;
        }

        String toolName = stack.getItem().getClass().getSimpleName().toLowerCase();

        // Priority 1: specific key e.g. "tool.pickaxe.iron"
        String matSuffix = materialName.toLowerCase().replaceAll("[ _]", "");
        String specificKey = "tool." + toolName + "." + matSuffix;
        String specific = LanguageCache.translate(languageCode, specificKey);
        if (specific != null && !specific.isEmpty()) {
            return specific;
        }

        // Priority 2: nameformat — resolve material prefix and tool type name separately
        String matDisplay = LanguageCache.translate(languageCode, locStr + ".display");
        if (matDisplay == null || matDisplay.isEmpty()) {
            matDisplay = LanguageCache.translate(languageCode, locStr);
        }
        if (matDisplay == null || matDisplay.isEmpty()) {
            return null;
        }

        String toolDisplay = LanguageCache.translate(languageCode, "tool." + toolName);
        if (toolDisplay == null || toolDisplay.isEmpty()) {
            return null;
        }

        String nameFormat = LanguageCache.translate(languageCode, KEY_NAMEFORMAT);
        if (nameFormat == null || nameFormat.isEmpty()) {
            nameFormat = FALLBACK_NAMEFORMAT;
        }

        try {
            return String.format(nameFormat, matDisplay, toolDisplay).trim();
        } catch (Exception e) {
            return (matDisplay + " " + toolDisplay).trim();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isTinkerTool(ItemStack stack) {
        return stack != null
            && stack.getItem() != null
            && stack.hasTagCompound()
            && stack.getTagCompound().hasKey("InfiTool")
            && stack.getTagCompound().getCompoundTag("InfiTool").hasKey("Head");
    }

    @SuppressWarnings("unchecked")
    private static Object getMaterial(int headId) {
        try {
            Map<Integer, ?> materials = (Map<Integer, ?>) toolMaterialsField.get(null);
            return materials == null ? null : materials.get(headId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Lazily initialises reflection references to TConstruct internals. Called once;
     * subsequent calls are no-ops. Returns {@code true} if reflection is ready.
     */
    private static boolean ensureReflection() {
        if (reflectionAttempted) {
            return reflectionReady;
        }

        synchronized (TinkerConstructDisplayNameResolver.class) {
            if (reflectionAttempted) {
                return reflectionReady;
            }
            reflectionAttempted = true;

            try {
                Class<?> registryClass = Class.forName(REGISTRY_CLASS);
                Field tmField = registryClass.getField(TOOL_MATERIALS_FIELD);
                tmField.setAccessible(true);
                toolMaterialsField = tmField;

                // Grab a material instance to find the ToolMaterial field declarations.
                @SuppressWarnings("unchecked")
                Map<Integer, ?> materials = (Map<Integer, ?>) tmField.get(null);
                if (materials == null || materials.isEmpty()) {
                    return false;
                }

                Object sampleMaterial = materials.values().iterator().next();
                Class<?> matClass = sampleMaterial.getClass();

                locStringField = findField(matClass, LOC_STRING_FIELD);
                matNameField   = findField(matClass, MAT_NAME_FIELD);

                if (locStringField == null || matNameField == null) {
                    return false;
                }

                locStringField.setAccessible(true);
                matNameField.setAccessible(true);

                reflectionReady = true;

            } catch (ClassNotFoundException e) {
                // TConstruct not loaded — silently skip.
            } catch (Exception ignored) {
                // Reflection failure — resolution unavailable.
            }

            return reflectionReady;
        }
    }

    /** Searches the class hierarchy for a field with the given name. */
    private static Field findField(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // try superclass
            }
        }
        return null;
    }
}
