package com.starskyxiii.polyglottooltip.client.config;

import net.minecraft.client.gui.GuiScreen;

import com.starskyxiii.polyglottooltip.config.Config;
import com.starskyxiii.polyglottooltip.PolyglotTooltip;

import cpw.mods.fml.client.config.GuiConfig;

public class PolyglotTooltipConfigGui extends GuiConfig {

    public PolyglotTooltipConfigGui(GuiScreen parentScreen) {
        super(
            parentScreen,
            Config.getConfigElements(),
            PolyglotTooltip.MODID,
            false,
            false,
            GuiConfig.getAbridgedConfigPath(String.valueOf(Config.getConfiguration())));
    }
}
