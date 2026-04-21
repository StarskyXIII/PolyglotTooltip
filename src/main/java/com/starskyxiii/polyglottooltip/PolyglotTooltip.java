package com.starskyxiii.polyglottooltip;

import com.mojang.logging.LogUtils;
import com.starskyxiii.polyglottooltip.integration.arsnouveau.ArsNouveauNameHelper;
import com.starskyxiii.polyglottooltip.integration.industrialforegoing.IndustrialForegoingNameHelper;
import com.starskyxiii.polyglottooltip.integration.productivebees.ProductiveBeesNameHelper;
import com.starskyxiii.polyglottooltip.integration.storagedrawers.StorageDrawersNameHelper;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;

@Mod(PolyglotTooltip.MODID)
public class PolyglotTooltip {
    public static final String MODID = "polyglottooltip";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PolyglotTooltip(IEventBus modEventBus, ModContainer modContainer, Dist dist) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);

        if (dist.isClient()) {
            modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

            // Keep integration-specific naming rules decoupled from LanguageCache.
            LanguageCache.registerSpecialNameResolver(ArsNouveauNameHelper::tryResolveSpecialName);
            LanguageCache.registerSpecialNameResolver(IndustrialForegoingNameHelper::tryResolveSpecialName);
            LanguageCache.registerSpecialNameResolver(ProductiveBeesNameHelper::tryResolveSpecialName);
            LanguageCache.registerSpecialNameResolver(StorageDrawersNameHelper::tryResolveSpecialName);

            modEventBus.addListener(this::onRegisterReloadListeners);
            modEventBus.addListener(this::onConfigReloading);
        }
    }

    private void onRegisterReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(MODID, "language_cache"),
                LanguageCache.getInstance()
        );
    }

    private void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == Config.SPEC) {
            LanguageCache.getInstance().reloadImmediate();
        }
    }
}
