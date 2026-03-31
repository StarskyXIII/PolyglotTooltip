package com.starskyxiii.polyglottooltip;

import com.starskyxiii.polyglottooltip.config.Config;
import com.starskyxiii.polyglottooltip.config.ConfigChangeHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    private static boolean wailaRegistrationSent;
    private static boolean configChangeHandlerRegistered;

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());

        if (!configChangeHandlerRegistered) {
            FMLCommonHandler.instance().bus().register(new ConfigChangeHandler());
            configChangeHandlerRegistered = true;
        }

        PolyglotTooltip.LOG.info("Loaded {} {}", PolyglotTooltip.MOD_NAME, Tags.VERSION);
        PolyglotTooltip.LOG.info("Configured display languages: {}", Config.displayLanguages);
    }

    public void init(FMLInitializationEvent event) {
        PolyglotTooltip.LOG.info("NEI loaded: {}", Loader.isModLoaded("NotEnoughItems"));
        PolyglotTooltip.LOG.info("AE2 loaded: {}", Loader.isModLoaded("appliedenergistics2"));
        PolyglotTooltip.LOG.info("Waila loaded: {}", Loader.isModLoaded("Waila"));

        if (Loader.isModLoaded("Waila") && !wailaRegistrationSent) {
            FMLInterModComms.sendMessage(
                "Waila",
                "register",
                "com.starskyxiii.polyglottooltip.integration.waila.WailaRegistration.callbackRegister");
            wailaRegistrationSent = true;
            PolyglotTooltip.LOG.info("Queued Waila registration callback.");
        }
    }

    public void postInit(FMLPostInitializationEvent event) {}

    public void loadComplete(FMLLoadCompleteEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {}
}
