package com.starskyxiii.polyglottooltip;
//~ minecraft_116_packages

import com.starskyxiii.polyglottooltip.integration.jei.JeiSearchRefreshBridge;
import com.starskyxiii.polyglottooltip.integration.jei.OptionalEmiSearchRefresh;
import com.starskyxiii.polyglottooltip.integration.storagedrawers.StorageDrawersNameHelper;
import com.starskyxiii.polyglottooltip.minecraft.MinecraftLanguageAdapter;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
//? if >=26 {
/*import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
*///?} else {
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
//?}
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.PackType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FabricPolyglotTooltipClient implements ClientModInitializer {
    private static final long CONFIG_POLL_INTERVAL_NANOS = 1_000_000_000L;
    private Path configPath;
    private long configModified = Long.MIN_VALUE;
    private long nextConfigPoll;

    @Override
    public void onInitializeClient() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("polyglottooltip.properties");
        FabricConfigStore.LoadResult initialLoad = Config.load(configPath);
        if (initialLoad.successful()) {
            configModified = lastModified(configPath);
        }

        if (FabricLoader.getInstance().isModLoaded("emi")) {
            OptionalEmiSearchRefresh.register();
        }

        LanguageCache.registerSpecialNameResolver(StorageDrawersNameHelper::tryResolveSpecialName);
        MinecraftLanguageAdapter languageAdapter = MinecraftLanguageAdapter.getInstance();
        languageAdapter.configureLanguages(Config.DISPLAY_LANGUAGE::get);
        languageAdapter.registerDependentCacheInvalidator(ChineseScriptSearchMatcher::clearCaches);
        //? if >=26 {
        /*ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                Identifier.fromNamespaceAndPath(PolyglotTooltip.MODID, "language_cache"),
                languageAdapter);
        *///?} else {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(languageAdapter);
        //?}

        //? if <=1.20.1 {
        /*ItemTooltipCallback.EVENT.register((stack, tooltipFlag, lines) -> ItemTooltipAppender.append(stack, lines));
        *///?} else {
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipFlag, lines) -> ItemTooltipAppender.append(stack, lines));
        //?}
        ClientTickEvents.END_CLIENT_TICK.register(client -> applyExternalConfigChange());
    }

    private void applyExternalConfigChange() {
        long now = System.nanoTime();
        if (now < nextConfigPoll) {
            return;
        }
        nextConfigPoll = now + CONFIG_POLL_INTERVAL_NANOS;
        long modified = lastModified(configPath);
        if (modified == configModified) {
            return;
        }
        FabricConfigStore.LoadResult loadResult = Config.load(configPath);
        if (!loadResult.successful()) {
            return;
        }
        configModified = modified;
        if (loadResult.changed()) {
            MinecraftLanguageAdapter.getInstance().reloadImmediate();
            JeiSearchRefreshBridge.rebuildSearchIndex();
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return -1L;
        }
    }
}
