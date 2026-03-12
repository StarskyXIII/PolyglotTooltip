package com.starskyxiii.polyglottooltip;

import com.starskyxiii.polyglottooltip.integration.arsnouveau.ArsNouveauNameHelper;
import com.starskyxiii.polyglottooltip.integration.industrialforegoing.IndustrialForegoingNameHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = PolyglotTooltip.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class PolyglotTooltipClient {

    public static void init() {
        // Triggers class loading, which registers this class with the mod event bus
        // via @Mod.EventBusSubscriber. Called from PolyglotTooltip via DistExecutor.
        LanguageCache.registerSpecialNameResolver(ArsNouveauNameHelper::tryResolveSpecialName);
        LanguageCache.registerSpecialNameResolver(IndustrialForegoingNameHelper::tryResolveSpecialName);
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(LanguageCache.getInstance());
    }

    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == Config.SPEC) {
            LanguageCache.getInstance().reloadImmediate();
        }
    }
}
