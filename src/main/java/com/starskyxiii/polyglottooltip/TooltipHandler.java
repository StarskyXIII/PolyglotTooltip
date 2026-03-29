package com.starskyxiii.polyglottooltip;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

public class TooltipHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemTooltip(ItemTooltipEvent event) {
        if (event == null || event.itemStack == null) {
            return;
        }

        SecondaryTooltipUtil.insertSecondaryNames(event.toolTip, event.itemStack);
        EnchantmentTooltipUtil.insertSecondaryEnchantments(event.toolTip, event.itemStack);
    }
}
