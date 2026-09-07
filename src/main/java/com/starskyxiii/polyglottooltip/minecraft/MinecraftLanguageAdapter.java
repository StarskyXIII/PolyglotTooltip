package com.starskyxiii.polyglottooltip.minecraft;

import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import com.starskyxiii.polyglottooltip.core.LanguageCodePolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
//? if <26 {
import net.minecraft.util.profiling.ProfilerFiller;
//?}

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class MinecraftLanguageAdapter implements PreparableReloadListener {
    private static final MinecraftLanguageAdapter INSTANCE = new MinecraftLanguageAdapter();

    private final List<Runnable> dependentCacheInvalidators = new ArrayList<>();
    private Supplier<? extends List<? extends String>> configuredLanguages = List::of;

    private MinecraftLanguageAdapter() {
    }

    public static MinecraftLanguageAdapter getInstance() {
        return INSTANCE;
    }

    public void configureLanguages(Supplier<? extends List<? extends String>> configuredLanguages) {
        this.configuredLanguages = Objects.requireNonNull(configuredLanguages);
    }

    public void registerDependentCacheInvalidator(Runnable invalidator) {
        dependentCacheInvalidators.add(Objects.requireNonNull(invalidator));
    }

    private List<ClientLanguage> loadLanguages(ResourceManager resourceManager) {
        List<ClientLanguage> result = new ArrayList<>();
        for (String languageCode : configuredLanguages.get()) {
            try {
                ClientLanguage language = ClientLanguage.loadFrom(
                        resourceManager,
                        LanguageCodePolicy.fallbackChain(languageCode),
                        false);
                PolyglotTooltip.LOGGER.info("[PolyglotTooltip] Loaded secondary language: {}", languageCode);
                result.add(language);
            } catch (Exception exception) {
                PolyglotTooltip.LOGGER.error(
                        "[PolyglotTooltip] Failed to load language '{}': {}",
                        languageCode,
                        exception.getMessage());
            }
        }
        return result;
    }

    private void applyLanguages(List<ClientLanguage> languages) {
        LanguageCache.getInstance().replaceLanguages(languages);
        for (Runnable invalidator : dependentCacheInvalidators) {
            invalidator.run();
        }
    }

    @Override
    //? if >=26 {
    /*public CompletableFuture<Void> reload(SharedState currentReload,
                                          Executor preparationExecutor,
                                          PreparationBarrier preparationBarrier,
                                          Executor applyExecutor) {
        ResourceManager resourceManager = currentReload.resourceManager();
        return CompletableFuture
                .supplyAsync(() -> loadLanguages(resourceManager), preparationExecutor)
                .thenCompose(preparationBarrier::wait)
                .thenAcceptAsync(this::applyLanguages, applyExecutor);
    *///?} else {
    public CompletableFuture<Void> reload(PreparationBarrier preparationBarrier,
                                          ResourceManager resourceManager,
                                          ProfilerFiller preparationProfiler,
                                          ProfilerFiller applyProfiler,
                                          Executor preparationExecutor,
                                          Executor applyExecutor) {
        return CompletableFuture
                .supplyAsync(() -> loadLanguages(resourceManager), preparationExecutor)
                .thenCompose(preparationBarrier::wait)
                .thenAcceptAsync(this::applyLanguages, applyExecutor);
    //?}
    }

    public void reloadImmediate() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        ResourceManager resourceManager = minecraft.getResourceManager();
        applyLanguages(loadLanguages(resourceManager));
    }
}
