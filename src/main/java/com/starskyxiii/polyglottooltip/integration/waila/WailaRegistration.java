package com.starskyxiii.polyglottooltip.integration.waila;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import mcp.mobius.waila.api.IWailaRegistrar;

public final class WailaRegistration {

    public static final String CONFIG_SHOW_SECONDARY_LANGUAGE = "polyglottooltip.wailaSecondaryLanguage";
    private static final String MODULE_NAME = "PolyglotTooltip";
    private static final String CONFIG_LABEL_KEY = "option." + CONFIG_SHOW_SECONDARY_LANGUAGE;

    private WailaRegistration() {}

    public static void callbackRegister(IWailaRegistrar registrar) {
        registrar.addConfig(
            MODULE_NAME,
            CONFIG_SHOW_SECONDARY_LANGUAGE,
            CONFIG_LABEL_KEY,
            true);

        WailaTooltipProvider provider = new WailaTooltipProvider();
        registrar.registerHeadProvider(provider, Block.class);

        WailaEntityTooltipProvider entityProvider = new WailaEntityTooltipProvider();
        registrar.registerHeadProvider(entityProvider, Entity.class);
    }
}
