package com.starskyxiii.polyglottooltip;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

public class TooltipHandler {

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if (event == null || event.itemStack == null) {
            return;
        }

        SecondaryTooltipUtil.insertSecondaryNames(event.toolTip, event.itemStack);
    }
}
