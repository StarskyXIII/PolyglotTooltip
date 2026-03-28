package com.starskyxiii.polyglottooltip;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());

        PolyglotTooltip.LOG.info("Loaded {} {}", PolyglotTooltip.MOD_NAME, Tags.VERSION);
        PolyglotTooltip.LOG.info("Configured display languages: {}", Config.displayLanguages);
    }

    public void init(FMLInitializationEvent event) {
        PolyglotTooltip.LOG.info("NEI loaded: {}", Loader.isModLoaded("NotEnoughItems"));
        PolyglotTooltip.LOG.info("AE2 loaded: {}", Loader.isModLoaded("appliedenergistics2"));
    }

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {}
}
