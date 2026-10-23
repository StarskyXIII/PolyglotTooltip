package com.starskyxiii.polyglottooltip.legacy;

import java.util.List;

import org.apache.logging.log4j.LogManager;

import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryLanguages;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class ConfigChanges {
    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (!PolyglotTooltipMod.MOD_ID.equals(event.getModID())) {
            return;
        }
        Settings before = Config.settings();
        Config.saveChanges();
        if (!before.equals(Config.settings())) {
            SecondaryLanguages.rebuild();
        }
        List<String> restart = Config.integrationsNeedingRestart();
        if (restart.isEmpty()) {
            return;
        }
        String ids = String.join(", ", restart);
        LogManager.getLogger(PolyglotTooltipMod.MOD_ID).debug("Integration changes take effect after a restart: {}", ids);
        EntityPlayerSP player = Minecraft.func_71410_x().field_71439_g;
        if (player != null) {
            player.func_145747_a(new TextComponentTranslation("polyglottooltip.config.restartRequired", ids));
        }
    }
}
