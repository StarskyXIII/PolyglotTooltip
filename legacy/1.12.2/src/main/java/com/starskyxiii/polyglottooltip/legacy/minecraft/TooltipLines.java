package com.starskyxiii.polyglottooltip.legacy.minecraft;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import com.starskyxiii.polyglottooltip.legacy.Config;
import com.starskyxiii.polyglottooltip.legacy.Settings;

import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class TooltipLines {
    private static final ThreadLocal<Boolean> SKIPPED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final Function<ItemStack, SecondaryNames> names;

    public TooltipLines() {
        this(ItemNames::of);
    }

    TooltipLines(Function<ItemStack, SecondaryNames> names) {
        this.names = names;
    }

    public static <T> T withoutSecondaryLines(Supplier<T> action) {
        Boolean previous = SKIPPED.get();
        SKIPPED.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            SKIPPED.set(previous);
        }
    }

    static boolean skipped() {
        return SKIPPED.get();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onTooltip(ItemTooltipEvent event) {
        if (skipped()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        try {
            List<String> lines = event.getToolTip();
            Settings settings = Config.settings();
            int index = Math.min(1, lines.size());
            for (String name : names.apply(stack).names()) {
                lines.add(index++, settings.tooltipPrefix() + name);
            }
            if (stack != null && !stack.func_190926_b()) {
                EnchantmentLines.apply(stack, lines, settings, SecondaryLanguages.dictionaries(),
                        SecondaryLanguages.currentLanguage());
            }
        } catch (RuntimeException error) {
            Object item = stack == null ? null : stack.func_77973_b();
            FailureLog.debug("tooltip|" + FailureLog.failureKey(item, error),
                    "Could not add secondary names for " + (item == null ? "null" : item.getClass().getName()), error);
        }
    }
}
