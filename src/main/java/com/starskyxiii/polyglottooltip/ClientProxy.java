package com.starskyxiii.polyglottooltip;

import codechicken.nei.api.API;
import com.starskyxiii.polyglottooltip.client.command.BuildNameCacheCommand;
import com.starskyxiii.polyglottooltip.client.command.DumpSecondaryNamesCommand;
import com.starskyxiii.polyglottooltip.name.prebuilt.AutoFullNameCacheBootstrap;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheIO;
import com.starskyxiii.polyglottooltip.config.LanguageCacheReloadListener;
import com.starskyxiii.polyglottooltip.integration.nei.NeiSearchProvider;
import com.starskyxiii.polyglottooltip.tooltip.TooltipHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.Loader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {

    private static boolean tooltipHandlerRegistered;
    private static boolean neiSearchProviderRegistered;
    private static boolean resourceReloadListenerRegistered;
    private static boolean dumpCommandRegistered;
    private static boolean buildCacheCommandRegistered;
    private static boolean fullCacheLoaded;
    private static boolean autoFullCacheBootstrapRegistered;
    private static AutoFullNameCacheBootstrap autoFullCacheBootstrap;

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        registerResourceReloadListener();

        if (!tooltipHandlerRegistered) {
            MinecraftForge.EVENT_BUS.register(new TooltipHandler());
            tooltipHandlerRegistered = true;
        }

        if (!dumpCommandRegistered) {
            ClientCommandHandler.instance.registerCommand(new DumpSecondaryNamesCommand());
            dumpCommandRegistered = true;
        }

        if (!buildCacheCommandRegistered) {
            ClientCommandHandler.instance.registerCommand(new BuildNameCacheCommand());
            buildCacheCommandRegistered = true;
        }

        if (!fullCacheLoaded) {
            FullNameCacheIO.tryLoad();
            fullCacheLoaded = true;
        }

        registerAutoFullCacheBootstrap();

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

    @Override
    public void loadComplete(FMLLoadCompleteEvent event) {
        if (autoFullCacheBootstrap != null) {
            autoFullCacheBootstrap.onLoadComplete();
        }
    }

    private static void registerAutoFullCacheBootstrap() {
        if (autoFullCacheBootstrapRegistered) {
            return;
        }

        autoFullCacheBootstrap = new AutoFullNameCacheBootstrap();
        FMLCommonHandler.instance().bus().register(autoFullCacheBootstrap);
        autoFullCacheBootstrapRegistered = true;
    }
}
