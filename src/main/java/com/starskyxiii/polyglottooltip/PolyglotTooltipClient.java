package com.starskyxiii.polyglottooltip;
//~ forge_neoforge_package

import com.starskyxiii.polyglottooltip.integration.arsnouveau.ArsNouveauNameHelper;
import com.starskyxiii.polyglottooltip.integration.industrialforegoing.IndustrialForegoingNameHelper;
import com.starskyxiii.polyglottooltip.integration.jei.JeiSearchRefreshBridge;
import com.starskyxiii.polyglottooltip.integration.occultism.OccultismSearchUtil;
import com.starskyxiii.polyglottooltip.integration.productivebees.ProductiveBeesNameHelper;
import com.starskyxiii.polyglottooltip.integration.storagedrawers.StorageDrawersNameHelper;
import com.starskyxiii.polyglottooltip.minecraft.MinecraftLanguageAdapter;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
//? if >=26 {
/*import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
*///?} else {
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
//?}
import net.neoforged.bus.api.SubscribeEvent;
//? if <=1.20.1 {
/*import net.neoforged.fml.common.Mod;
*///?} else {
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
//?}
import net.neoforged.fml.event.config.ModConfigEvent;

//? if <=1.20.1 {
/*@Mod.EventBusSubscriber(modid = PolyglotTooltip.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
*///?} else {
@Mod(value = PolyglotTooltip.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = PolyglotTooltip.MODID, value = Dist.CLIENT)
//?}
public class PolyglotTooltipClient {

    //? if <=1.20.1 {
    /*public static void init() {
        initializeLanguageSupport();
    }
    *///?} else {
    public PolyglotTooltipClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        initializeLanguageSupport();
    }
    //?}

    private static void initializeLanguageSupport() {
        LanguageCache.registerSpecialNameResolver(ArsNouveauNameHelper::tryResolveSpecialName);
        LanguageCache.registerSpecialNameResolver(IndustrialForegoingNameHelper::tryResolveSpecialName);
        LanguageCache.registerSpecialNameResolver(ProductiveBeesNameHelper::tryResolveSpecialName);
        LanguageCache.registerSpecialNameResolver(StorageDrawersNameHelper::tryResolveSpecialName);

        MinecraftLanguageAdapter languageAdapter = MinecraftLanguageAdapter.getInstance();
        languageAdapter.configureLanguages(Config.DISPLAY_LANGUAGE::get);
        languageAdapter.registerDependentCacheInvalidator(OccultismSearchUtil::clearTooltipCache);
        languageAdapter.registerDependentCacheInvalidator(ChineseScriptSearchMatcher::clearCaches);
    }

    @SubscribeEvent
    //? if >=26 {
    /*static void onRegisterReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(PolyglotTooltip.MODID, "language_cache"),
                MinecraftLanguageAdapter.getInstance());
    *///?} else {
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(MinecraftLanguageAdapter.getInstance());
    //?}
    }

    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == Config.SPEC) {
            Minecraft.getInstance().execute(() -> {
                LanguageCache.getInstance().reloadImmediate();
                JeiSearchRefreshBridge.rebuildSearchIndex();
            });
        }
    }
}
