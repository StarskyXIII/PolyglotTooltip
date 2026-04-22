package com.starskyxiii.polyglottooltip.name;

import java.lang.reflect.Method;

import com.starskyxiii.polyglottooltip.i18n.LanguageCache;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.oredict.OreDictionary;

final class Ae2DisplayNameResolver {

    private static final String AE2_MULTIPART_CLASS_NAME = "appeng.items.parts.ItemMultiPart";
    private static final String AE2_PAINT_BALL_CLASS_NAME = "appeng.items.misc.ItemPaintBall";
    private static final String AE2_COLOR_APPLICATOR_CLASS_NAME = "appeng.items.tools.powered.ToolColorApplicator";
    private static final String AE2FC_FLUID_PART_CLASS_NAME = "com.glodblock.github.common.item.ItemBasicFluidStoragePart";
    private static final String AE2FC_FLUID_STORAGE_CELL_CLASS_NAME = "com.glodblock.github.common.item.ItemBasicFluidStorageCell";
    private static final String AE2FC_MULTI_FLUID_STORAGE_CELL_CLASS_NAME =
        "com.glodblock.github.common.item.ItemMultiFluidStorageCell";
    private static final String AE2_ITEM_PART_PREFIX = "item.appliedenergistics2.ItemPart.";

    private static final String GUI_TEXT_ROOT = "gui.appliedenergistics2.";
    private static final String[] AE_COLOR_KEYS = {
        "White",
        "Orange",
        "Magenta",
        "LightBlue",
        "Yellow",
        "Lime",
        "Pink",
        "Gray",
        "LightGray",
        "Cyan",
        "Purple",
        "Blue",
        "Brown",
        "Green",
        "Red",
        "Black",
        "Fluix"
    };
    private static final String[] DYE_ORE_NAMES = {
        "dyeWhite",
        "dyeOrange",
        "dyeMagenta",
        "dyeLightBlue",
        "dyeYellow",
        "dyeLime",
        "dyePink",
        "dyeGray",
        "dyeLightGray",
        "dyeCyan",
        "dyePurple",
        "dyeBlue",
        "dyeBrown",
        "dyeGreen",
        "dyeRed",
        "dyeBlack"
    };

    private static final long[] AE2FC_CAPACITIES = { 1L, 4L, 16L, 64L, 256L, 1024L, 4096L, 16384L };
    private static final EnumChatFormatting[] AE2FC_CAPACITY_COLORS = {
        EnumChatFormatting.GOLD,
        EnumChatFormatting.YELLOW,
        EnumChatFormatting.GREEN,
        EnumChatFormatting.AQUA,
        EnumChatFormatting.BLUE,
        EnumChatFormatting.LIGHT_PURPLE,
        EnumChatFormatting.RED,
        EnumChatFormatting.DARK_PURPLE
    };
    private static final int INVALID_DAMAGE = Integer.MIN_VALUE;

    private Ae2DisplayNameResolver() {}

    static String tryResolveDisplayName(ItemStack stack, String languageCode) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        String itemClassName = stack.getItem().getClass().getName();

        if (AE2_MULTIPART_CLASS_NAME.equals(itemClassName)) {
            return resolveMultiPartDisplayName(stack, languageCode);
        }

        if (AE2_PAINT_BALL_CLASS_NAME.equals(itemClassName)) {
            return resolvePaintBallDisplayName(stack, languageCode);
        }

        if (AE2_COLOR_APPLICATOR_CLASS_NAME.equals(itemClassName)) {
            return resolveColorApplicatorDisplayName(stack, languageCode);
        }

        if (AE2FC_FLUID_PART_CLASS_NAME.equals(itemClassName)) {
            return resolveAe2FluidCraftPartDisplayName(stack, languageCode);
        }

        if (AE2FC_FLUID_STORAGE_CELL_CLASS_NAME.equals(itemClassName)) {
            return resolveAe2FluidCraftCellDisplayName(stack, languageCode, "item.fluid_storage.");
        }

        if (AE2FC_MULTI_FLUID_STORAGE_CELL_CLASS_NAME.equals(itemClassName)) {
            return resolveAe2FluidCraftCellDisplayName(stack, languageCode, "item.multi_fluid_storage.");
        }

        return null;
    }

    private static String resolveMultiPartDisplayName(ItemStack stack, String languageCode) {
        String baseName = LanguageCache.translate(languageCode, getTranslationKey(stack));
        String suffix = resolveMultiPartSuffix(stack, languageCode);
        if (suffix != null && !suffix.isEmpty()) {
            return appendSuffix(baseName, suffix);
        }

        Object partType = invokeDeclared(stack.getItem(), "getTypeByStack", new Class<?>[] { ItemStack.class }, stack);
        if (partType == null) {
            return baseName;
        }

        if (Boolean.TRUE.equals(invokeBoolean(partType, "isCable"))) {
            Integer variant = invokeInteger(
                stack.getItem(),
                "variantOf",
                new Class<?>[] { int.class },
                Integer.valueOf(stack.getItemDamage()));
            String colorName = resolveAeColorName(languageCode, variant == null ? -1 : variant.intValue());
            return appendSuffix(baseName, colorName);
        }

        Object extraName = invokeDeclared(partType, "getExtraName");
        if (extraName != null) {
            Object unlocalized = invoke(extraName, "getUnlocalized");
            if (unlocalized instanceof String) {
                String extraSuffix = LanguageCache.translate(languageCode, (String) unlocalized);
                if (extraSuffix != null && !extraSuffix.isEmpty()) {
                    return appendSuffix(baseName, extraSuffix);
                }
            }
        }

        return baseName;
    }

    private static String resolvePaintBallDisplayName(ItemStack stack, String languageCode) {
        String baseName = LanguageCache.translate(languageCode, getTranslationKey(stack));
        if (baseName == null || baseName.isEmpty()) {
            return null;
        }

        int damage = stack.getItemDamage();
        boolean isLumen = damage >= 20;
        if (isLumen) {
            damage -= 20;
        }

        String colorName = resolveAeColorName(languageCode, damage);
        if (colorName == null || colorName.isEmpty()) {
            return baseName;
        }

        StringBuilder suffix = new StringBuilder();
        if (isLumen) {
            String lumen = LanguageCache.translate(languageCode, GUI_TEXT_ROOT + "Lumen");
            if (lumen != null && !lumen.isEmpty()) {
                suffix.append(lumen.trim()).append(' ');
            }
        }
        suffix.append(colorName);

        return appendSuffix(baseName, suffix.toString());
    }

    private static String resolveColorApplicatorDisplayName(ItemStack stack, String languageCode) {
        String baseName = LanguageCache.translate(languageCode, getTranslationKey(stack));
        if (baseName == null || baseName.isEmpty()) {
            return null;
        }

        return appendSuffix(baseName, resolveColorApplicatorSuffix(stack, languageCode));
    }

    private static String resolveColorApplicatorSuffix(ItemStack stack, String languageCode) {
        ItemStack colorStack = getStoredColorStack(stack);
        if (colorStack == null || colorStack.getItem() == null) {
            return LanguageCache.translate(languageCode, GUI_TEXT_ROOT + "Empty");
        }

        if ("net.minecraft.item.ItemSnowball".equals(colorStack.getItem().getClass().getName())) {
            return LanguageCache.translate(languageCode, GUI_TEXT_ROOT + "Empty");
        }

        if (AE2_PAINT_BALL_CLASS_NAME.equals(colorStack.getItem().getClass().getName())) {
            int damage = colorStack.getItemDamage();
            if (damage >= 20) {
                damage -= 20;
            }
            return resolveAeColorName(languageCode, damage);
        }

        int[] oreIds = OreDictionary.getOreIDs(colorStack);
        for (int oreId : oreIds) {
            String oreName = OreDictionary.getOreName(oreId);
            int colorOrdinal = resolveDyeColorOrdinal(oreName);
            if (colorOrdinal >= 0) {
                return resolveAeColorName(languageCode, colorOrdinal);
            }
        }

        return null;
    }

    private static String resolveMultiPartSuffix(ItemStack stack, String languageCode) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        String unlocalizedName = stack.getUnlocalizedName();
        if (unlocalizedName == null || !unlocalizedName.startsWith(AE2_ITEM_PART_PREFIX)) {
            return null;
        }

        int damage = stack.getItemDamage();
        int cableBaseDamage = getCableBaseDamage(unlocalizedName);
        if (cableBaseDamage != INVALID_DAMAGE) {
            return resolveAeColorName(languageCode, damage - cableBaseDamage);
        }

        return resolveP2PTunnelSuffix(languageCode, damage);
    }

    private static int getCableBaseDamage(String unlocalizedName) {
        if (unlocalizedName.endsWith("CableGlass")) {
            return 0;
        }
        if (unlocalizedName.endsWith("CableCovered")) {
            return 20;
        }
        if (unlocalizedName.endsWith("CableSmart")) {
            return 40;
        }
        if (unlocalizedName.endsWith("CableDense")) {
            return 60;
        }
        if (unlocalizedName.endsWith("CableDenseCovered")) {
            return 520;
        }

        return INVALID_DAMAGE;
    }

    private static String resolveP2PTunnelSuffix(String languageCode, int damage) {
        String guiTextName;
        switch (damage) {
            case 460:
                guiTextName = "METunnel";
                break;
            case 461:
                guiTextName = "RedstoneTunnel";
                break;
            case 462:
                guiTextName = "ItemTunnel";
                break;
            case 463:
                guiTextName = "FluidTunnel";
                break;
            case 465:
                guiTextName = "EUTunnel";
                break;
            case 466:
                guiTextName = "RFTunnel";
                break;
            case 467:
                guiTextName = "LightTunnel";
                break;
            case 468:
                guiTextName = "OCTunnel";
                break;
            case 469:
                guiTextName = "PressureTunnel";
                break;
            case 470:
                guiTextName = "GTTunnel";
                break;
            case 471:
                guiTextName = "IFACETunnel";
                break;
            case 472:
                guiTextName = "SoundTunnel";
                break;
            default:
                return null;
        }

        return LanguageCache.translate(languageCode, GUI_TEXT_ROOT + guiTextName);
    }

    private static ItemStack getStoredColorStack(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return null;
        }

        NBTTagCompound root = stack.getTagCompound();
        if (root == null || !root.hasKey("color", 10)) {
            return null;
        }

        return ItemStack.loadItemStackFromNBT(root.getCompoundTag("color"));
    }

    private static String resolveAe2FluidCraftPartDisplayName(ItemStack stack, String languageCode) {
        int meta = stack.getItemDamage();
        if (meta < 0 || meta >= AE2FC_CAPACITY_COLORS.length) {
            return LanguageCache.translate(languageCode, getTranslationKey(stack));
        }

        return LanguageCache.format(
            languageCode,
            "item.fluid_part." + meta + ".name",
            AE2FC_CAPACITY_COLORS[meta],
            EnumChatFormatting.RESET);
    }

    private static String resolveAe2FluidCraftCellDisplayName(ItemStack stack, String languageCode, String keyPrefix) {
        long capacity = parseTrailingCapacity(stack.getUnlocalizedName());
        if (capacity <= 0L) {
            return LanguageCache.translate(languageCode, getTranslationKey(stack));
        }

        EnumChatFormatting color = resolveAe2FluidCraftCapacityColor(capacity);
        if (color == null) {
            return LanguageCache.translate(languageCode, keyPrefix + capacity + ".name");
        }

        return LanguageCache.format(
            languageCode,
            keyPrefix + capacity + ".name",
            color,
            EnumChatFormatting.RESET);
    }

    private static EnumChatFormatting resolveAe2FluidCraftCapacityColor(long capacity) {
        for (int i = 0; i < AE2FC_CAPACITIES.length; i++) {
            if (AE2FC_CAPACITIES[i] == capacity) {
                return AE2FC_CAPACITY_COLORS[i];
            }
        }

        return null;
    }

    private static long parseTrailingCapacity(String unlocalizedName) {
        if (unlocalizedName == null || unlocalizedName.isEmpty()) {
            return -1L;
        }

        int dot = unlocalizedName.lastIndexOf('.');
        if (dot < 0 || dot + 1 >= unlocalizedName.length()) {
            return -1L;
        }

        try {
            return Long.parseLong(unlocalizedName.substring(dot + 1));
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static String resolveAeColorName(String languageCode, int ordinal) {
        if (ordinal < 0 || ordinal >= AE_COLOR_KEYS.length) {
            return null;
        }

        return LanguageCache.translate(languageCode, GUI_TEXT_ROOT + AE_COLOR_KEYS[ordinal]);
    }

    private static int resolveDyeColorOrdinal(String oreName) {
        if (oreName == null || oreName.isEmpty()) {
            return -1;
        }

        for (int i = 0; i < DYE_ORE_NAMES.length; i++) {
            if (DYE_ORE_NAMES[i].equals(oreName)) {
                return i;
            }
        }

        return -1;
    }

    private static String appendSuffix(String baseName, String suffix) {
        String normalizedBase = baseName == null ? "" : baseName.trim();
        String normalizedSuffix = suffix == null ? "" : suffix.trim();

        if (normalizedBase.isEmpty()) {
            return normalizedSuffix.isEmpty() ? null : normalizedSuffix;
        }

        if (normalizedSuffix.isEmpty()) {
            return normalizedBase;
        }

        return normalizedBase + " - " + normalizedSuffix;
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

    private static Object invoke(Object target, String methodName) {
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

    private static Object invokeDeclared(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return null;
        }

        try {
            Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object invokeDeclared(Object target, String methodName) {
        if (target == null) {
            return null;
        }

        try {
            Method method = target.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Boolean invokeBoolean(Object target, String methodName) {
        Object result = invoke(target, methodName);
        return result instanceof Boolean ? (Boolean) result : null;
    }

    private static Integer invokeInteger(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        Object result = invoke(target, methodName, parameterTypes, args);
        return result instanceof Integer ? (Integer) result : null;
    }
}
