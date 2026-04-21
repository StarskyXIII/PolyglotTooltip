package com.starskyxiii.polyglottooltip.integration.apothicenchanting;

import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.enchantment.Enchantment;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ApothicEnchantmentLibraryUtil {

    private static final Map<Class<?>, Optional<Method>> HOVERED_SLOT_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Method>> SLOT_ENCHANTMENT_METHOD_CACHE = new ConcurrentHashMap<>();

    private ApothicEnchantmentLibraryUtil() {
    }

    public static boolean matchesSearch(String query, Component enchantmentName) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty() || enchantmentName == null) {
            return true;
        }

        List<String> candidates = new ArrayList<>();
        candidates.add(enchantmentName.getString());
        candidates.addAll(LanguageCache.getInstance().resolveComponentsForAll(enchantmentName));
        return ChineseScriptSearchMatcher.containsMatch(normalizedQuery, candidates);
    }

    public static List<FormattedText> insertSecondaryNameLines(List<FormattedText> original,
                                                               Component enchantmentName,
                                                               int wrapWidth) {
        if (original == null || original.isEmpty() || enchantmentName == null) {
            return original;
        }

        List<Component> secondaryLines = SecondaryTooltipUtil.getSecondaryNameLines(enchantmentName);
        if (secondaryLines.isEmpty()) {
            return original;
        }

        Font font = Minecraft.getInstance() == null ? null : Minecraft.getInstance().font;
        List<FormattedText> updated = new ArrayList<>(original.size() + secondaryLines.size());
        updated.add(original.get(0));

        for (Component secondary : secondaryLines) {
            if (font != null && wrapWidth > 0) {
                updated.addAll(font.getSplitter().splitLines(secondary, wrapWidth, secondary.getStyle()));
            } else {
                updated.add(secondary);
            }
        }

        updated.addAll(original.subList(1, original.size()));
        return updated;
    }

    public static Optional<Component> getHoveredEnchantmentName(Object screen, int mouseX, int mouseY) {
        if (screen == null) {
            return Optional.empty();
        }

        return invokeHoveredSlot(screen, mouseX, mouseY)
                .flatMap(ApothicEnchantmentLibraryUtil::getEnchantmentName);
    }

    public static Optional<Component> getEnchantmentName(Object slot) {
        if (slot == null) {
            return Optional.empty();
        }

        return invokeSlotEnchantment(slot)
                .map(Holder::value)
                .map(ApothicEnchantmentLibraryUtil::getEnchantmentDisplayName);
    }

    private static Optional<Object> invokeHoveredSlot(Object screen, int mouseX, int mouseY) {
        Optional<Method> method = HOVERED_SLOT_METHOD_CACHE.computeIfAbsent(
                screen.getClass(),
                ApothicEnchantmentLibraryUtil::findHoveredSlotMethod
        );
        if (method.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(method.get().invoke(screen, mouseX, mouseY));
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static Optional<Holder<Enchantment>> invokeSlotEnchantment(Object slot) {
        Optional<Method> method = SLOT_ENCHANTMENT_METHOD_CACHE.computeIfAbsent(
                slot.getClass(),
                ApothicEnchantmentLibraryUtil::findSlotEnchantmentMethod
        );
        if (method.isEmpty()) {
            return Optional.empty();
        }

        try {
            Object result = method.get().invoke(slot);
            if (result instanceof Holder<?> holder && holder.value() instanceof Enchantment) {
                return Optional.of((Holder<Enchantment>) holder);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return Optional.empty();
    }

    private static Optional<Method> findHoveredSlotMethod(Class<?> screenClass) {
        try {
            Method method = screenClass.getMethod("getHoveredSlot", int.class, int.class);
            method.setAccessible(true);
            return Optional.of(method);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Method> findSlotEnchantmentMethod(Class<?> slotClass) {
        try {
            Method method = slotClass.getDeclaredMethod("ench");
            method.setAccessible(true);
            return Optional.of(method);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return Optional.empty();
        }
    }

    public static Component getEnchantmentDisplayName(Enchantment enchantment) {
        return Component.translatable(enchantment.getDescriptionId());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
