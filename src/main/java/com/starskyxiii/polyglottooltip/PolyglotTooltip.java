package com.starskyxiii.polyglottooltip;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(PolyglotTooltip.MODID)
public class PolyglotTooltip {
    public static final String MODID = "polyglottooltip";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PolyglotTooltip(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
    }
}
