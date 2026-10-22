package com.starskyxiii.polyglottooltip.legacy.minecraft;

import java.util.List;

import com.starskyxiii.polyglottooltip.legacy.Config;
import com.starskyxiii.polyglottooltip.legacy.Settings;

import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class TooltipLines {
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        try {
            List<String> lines = event.getToolTip();
            Settings settings = Config.settings();
            SecondaryNames names = ItemNames.of(stack);
            int index = Math.min(1, lines.size());
            for (String name : names.names()) {
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
