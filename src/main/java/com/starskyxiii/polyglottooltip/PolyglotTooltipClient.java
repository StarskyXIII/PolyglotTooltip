package com.starskyxiii.polyglottooltip;

import com.starskyxiii.polyglottooltip.integration.arsnouveau.ArsNouveauNameHelper;
import com.starskyxiii.polyglottooltip.integration.industrialforegoing.IndustrialForegoingNameHelper;
import com.starskyxiii.polyglottooltip.integration.occultism.OccultismSearchUtil;
import com.starskyxiii.polyglottooltip.integration.productivebees.ProductiveBeesNameHelper;
import com.starskyxiii.polyglottooltip.integration.storagedrawers.StorageDrawersNameHelper;
import com.starskyxiii.polyglottooltip.minecraft.MinecraftLanguageAdapter;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import net.neoforged.api.distmarker.Dist;
//? if >=26 {
/*import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
*///?} else {
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
//?}
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = PolyglotTooltip.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = PolyglotTooltip.MODID, value = Dist.CLIENT)
public class PolyglotTooltipClient {

    public PolyglotTooltipClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // Keep integration-specific naming rules decoupled from LanguageCache.
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
    static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(MinecraftLanguageAdapter.getInstance());
    //?}
    }

    @SubscribeEvent
    static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == Config.SPEC) {
            LanguageCache.getInstance().reloadImmediate();
        }
    }
}
