package com.starskyxiii.polyglottooltip;

import com.starskyxiii.polyglottooltip.integration.arsnouveau.ArsNouveauNameHelper;
import com.starskyxiii.polyglottooltip.integration.industrialforegoing.IndustrialForegoingNameHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
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
    }

    @SubscribeEvent
    static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(LanguageCache.getInstance());
    }

    @SubscribeEvent
    static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == Config.SPEC) {
            LanguageCache.getInstance().reloadImmediate();
        }
    }
}
