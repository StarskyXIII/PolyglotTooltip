package com.starskyxiii.polyglottooltip.integration.occultism;

import com.starskyxiii.polyglottooltip.search.SearchTextCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OccultismSearchUtil {

    private static final String MACHINE_REFERENCE_CLASS =
            "com.klikli_dev.occultism.api.common.data.MachineReference";

    private static Method getInsertItemStackMethod;
    private static Field customNameField;
    private static boolean reflectionInitialized;

    // Tooltip search text is expensive to build (calls getTooltipLines on every item).
    // Cache by Item since tooltip content is determined by item type, not NBT, for
    // the vast majority of Occultism storage items.
    private static final Map<Item, String> tooltipSearchCache = new HashMap<>();

    public static void clearTooltipCache() {
        tooltipSearchCache.clear();
    }

    private OccultismSearchUtil() {
    }

    public static boolean matchesItemSearch(String searchText, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        String normalized = normalize(searchText);
        if (normalized.isEmpty()) {
            return true;
        }

        if (normalized.startsWith("@") || normalized.startsWith("$")) {
            return false;
        }

        if (normalized.startsWith("#")) {
            return buildTooltipSearchText(stack).contains(normalized.substring(1));
        }

        return new SearchTextCollector()
                .addItemStack(stack)
                .build()
                .contains(normalized);
    }

    public static boolean matchesMachineSearch(String searchText, Object machine) {
        String normalized = normalize(searchText);
        if (normalized.isEmpty()) {
            return true;
        }
        if (normalized.startsWith("@") || normalized.startsWith("#") || normalized.startsWith("$")) {
            return false;
        }

        ItemStack insertStack = getInsertItemStack(machine);
        if (insertStack.isEmpty()) {
            return false;
        }

        return new SearchTextCollector()
                .addItemStack(insertStack)
                .addText(getCustomName(machine))
                .build()
                .contains(normalized);
    }

    private static String buildTooltipSearchText(ItemStack stack) {
        return tooltipSearchCache.computeIfAbsent(stack.getItem(), item -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || minecraft.player == null) {
                return "";
            }
            List<Component> tooltip = stack.getTooltipLines(
                    minecraft.player,
                    TooltipFlag.Default.NORMAL
            );
            SearchTextCollector collector = new SearchTextCollector();
            for (Component line : tooltip) {
                collector.addComponent(line);
            }
            return collector.build();
        });
    }

    private static ItemStack getInsertItemStack(Object machine) {
        if (!initializeReflection(machine)) {
            return ItemStack.EMPTY;
        }

        try {
            Object result = getInsertItemStackMethod.invoke(machine);
            return result instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        } catch (ReflectiveOperationException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static String getCustomName(Object machine) {
        if (!initializeReflection(machine)) {
            return "";
        }

        try {
            Object result = customNameField.get(machine);
            return result instanceof String name ? name : "";
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    private static boolean initializeReflection(Object machine) {
        if (reflectionInitialized) {
            return getInsertItemStackMethod != null && customNameField != null;
        }

        if (machine == null || !MACHINE_REFERENCE_CLASS.equals(machine.getClass().getName())) {
            return false;
        }

        try {
            Class<?> machineClass = machine.getClass();
            getInsertItemStackMethod = machineClass.getMethod("getInsertItemStack");
            customNameField = machineClass.getField("customName");
            reflectionInitialized = true;
            return true;
        } catch (ReflectiveOperationException ignored) {
            getInsertItemStackMethod = null;
            customNameField = null;
            reflectionInitialized = true;
            return false;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
