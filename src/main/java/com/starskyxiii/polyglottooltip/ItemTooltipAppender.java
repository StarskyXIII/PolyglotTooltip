package com.starskyxiii.polyglottooltip;
//~ minecraft_116_packages minecraft_legacy_components minecraft_118_factories forge_neoforge_package

import com.starskyxiii.polyglottooltip.minecraft.ComponentFactory;
//? if (forge && 1.20.1) || (neoforge && 1.21.1) {
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.fluids.FluidStack;
//?}
//? if forge && 1.20.1 {
/*import java.lang.reflect.Method;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
//? if <=1.20.1 {
/*import net.minecraft.world.item.enchantment.EnchantmentHelper;
*///?} else {
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
//?}

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ItemTooltipAppender {
    //? if forge && 1.20.1 {
    /*private static final ClassValue<Method> GTM_TOOLTIP_BODY = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            try {
                return type.getMethod("body");
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }
    };
    *///?}

    private ItemTooltipAppender() {
    }

    public static void append(ItemStack stack, List<Component> tooltip) {
        if (stack.isEmpty() || !SecondaryTooltipUtil.shouldShowSecondaryLanguage()) {
            return;
        }
        LanguageCache cache = LanguageCache.getInstance();
        //? if (forge && 1.20.1) || (neoforge && 1.21.1) {
        if (!appendGtceuStoredFluid(stack, tooltip, cache)) {
            SecondaryTooltipUtil.insertSecondaryName(tooltip, stack);
        }
        //?} else {
        /*SecondaryTooltipUtil.insertSecondaryName(tooltip, stack);
        *///?}
        processEnchantments(stack, tooltip, cache);
    }

    //? if (forge && 1.20.1) || (neoforge && 1.21.1) {
    private static boolean appendGtceuStoredFluid(ItemStack stack, List<Component> tooltip, LanguageCache cache) {
        var itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!"gtceu".equals(itemId.getNamespace())
                || !(itemId.getPath().endsWith("_drum") || itemId.getPath().endsWith("_super_tank"))) {
            return false;
        }
        for (int i = 1; i < tooltip.size(); i++) {
            ComponentContents contents = tooltip.get(i).getContents();
            //? if forge {
            /*contents = unwrapGtceuTip(contents);
            *///?}
            if (!(contents instanceof TranslatableContents detail)
                    || !"gtceu.universal.tooltip.fluid_stored".equals(detail.getKey())
                    || detail.getArgs().length == 0
                    || !(detail.getArgs()[0] instanceof Component fluidName)) {
                continue;
            }
            //? if forge {
            /*Component emptyFluidName = FluidStack.EMPTY.getDisplayName();
            *///?} else {
            Component emptyFluidName = FluidStack.EMPTY.getHoverName();
            //?}
            if (fluidName.getContents().equals(emptyFluidName.getContents()) || fluidName.getString().isBlank()) {
                return false;
            }
            String primaryName = tooltip.get(0).getString();
            String primaryNameWithFluid = fluidTitle(primaryName, fluidName.getString());
            List<String> names = cache.resolveDisplayNamesWithDetail(stack, fluidName, ItemTooltipAppender::fluidTitle);
            tooltip.addAll(1, names.stream()
                    .filter(name -> !name.equals(primaryName) && !name.equals(primaryNameWithFluid))
                    .map(SecondaryTooltipUtil::createTooltipSecondaryLine)
                    .toList());
            return true;
        }
        return false;
    }

    private static String fluidTitle(String name, String fluid) {
        return name + fluidTitleSeparator(name, fluid) + fluid + ")";
    }

    private static String fluidTitleSeparator(String name, String fluid) {
        return name.codePoints().anyMatch(Character::isIdeographic)
                || fluid.codePoints().anyMatch(Character::isIdeographic) ? "(" : " (";
    }
    //?}

    //? if forge && 1.20.1 {
    /*private static ComponentContents unwrapGtceuTip(ComponentContents contents) {
        Class<?> type = contents.getClass();
        if (type.getName().equals("com.gregtechceu.gtceu.api.tooltip.TipContents")) {
            Method body = GTM_TOOLTIP_BODY.get(type);
            if (body != null) {
                try {
                    if (body.invoke(contents) instanceof Component component) {
                        return component.getContents();
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        return contents;
    }
    *///?}

    private static void processEnchantments(ItemStack stack, List<Component> tooltip, LanguageCache cache) {
        //? if <=1.20.1 {
        /*Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
        if (enchants.isEmpty()) return;
        *///?} else {
        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        //?}

        Map<String, String> nameMap = new HashMap<>();
        fillEnchantmentNames(enchants, nameMap, cache);
        //? if >1.20.1 {
        fillEnchantmentNames(stored, nameMap, cache);
        //?}
        if (nameMap.isEmpty()) return;

        for (int i = 0; i < tooltip.size(); i++) {
            Component originalLine = tooltip.get(i);
            String secondary = nameMap.get(originalLine.getString());
            if (secondary != null) {
                Style originalStyle = originalLine.getStyle();
                Component appendedPart = ComponentFactory.empty()
                        .append(ComponentFactory.literal("｜").withStyle(net.minecraft.ChatFormatting.GRAY))
                        .append(ComponentFactory.literal(secondary).withStyle(originalStyle));
                tooltip.set(i, originalLine.copy().append(appendedPart));
            }
        }
    }

    //? if <=1.20.1 {
    /*private static void fillEnchantmentNames(Map<Enchantment, Integer> enchants,
                                             Map<String, String> nameMap,
                                             LanguageCache cache) {
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            addEnchantmentName(entry.getKey().getFullname(entry.getValue()), nameMap, cache);
        }
    }
    *///?} else {
    private static void fillEnchantmentNames(ItemEnchantments enchants,
                                             Map<String, String> nameMap,
                                             LanguageCache cache) {
        for (var entry : enchants.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            addEnchantmentName(Enchantment.getFullname(holder, entry.getIntValue()), nameMap, cache);
        }
    }
    //?}

    private static void addEnchantmentName(Component fullName, Map<String, String> nameMap, LanguageCache cache) {
        String current = fullName.getString();
        String joined = cache.resolveComponentsForAll(fullName).stream()
                .filter(value -> !value.equals(current))
                .collect(Collectors.joining("｜"));
        if (!joined.isEmpty()) {
            nameMap.put(current, joined);
        }
    }
}
