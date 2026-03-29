package com.starskyxiii.polyglottooltip;

import cpw.mods.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class ConfigChangeHandler {

    @SubscribeEvent
    public void onConfigChanged(OnConfigChangedEvent event) {
        if (event == null || !PolyglotTooltip.MODID.equals(event.modID)) {
            return;
        }

        Config.synchronizeConfiguration();
    }
}
