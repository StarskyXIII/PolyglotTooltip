package com.starskyxiii.polyglottooltip;

import codechicken.nei.api.API;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.Loader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraftforge.common.MinecraftForge;

import com.starskyxiii.polyglottooltip.integration.nei.NeiSearchProvider;

public class ClientProxy extends CommonProxy {

    private static boolean tooltipHandlerRegistered;
    private static boolean neiSearchProviderRegistered;
    private static boolean resourceReloadListenerRegistered;

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        LanguageCache.preloadConfiguredLanguages();
        registerResourceReloadListener();

        if (!tooltipHandlerRegistered) {
            MinecraftForge.EVENT_BUS.register(new TooltipHandler());
            tooltipHandlerRegistered = true;
        }

        if (Loader.isModLoaded("NotEnoughItems") && !neiSearchProviderRegistered) {
            API.addSearchProvider(new NeiSearchProvider());
            neiSearchProviderRegistered = true;
            PolyglotTooltip.LOG.info("Registered NEI multilingual search provider.");
        }

        PolyglotTooltip.LOG.info("Client bootstrap ready for tooltip and NEI search integration.");
    }

    private static void registerResourceReloadListener() {
        if (resourceReloadListenerRegistered) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || !(minecraft.getResourceManager() instanceof IReloadableResourceManager)) {
            return;
        }

        IReloadableResourceManager resourceManager =
            (IReloadableResourceManager) minecraft.getResourceManager();
        resourceManager.registerReloadListener(new LanguageCacheReloadListener());
        resourceReloadListenerRegistered = true;
    }
}
