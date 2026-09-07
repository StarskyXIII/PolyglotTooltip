package com.starskyxiii.polyglottooltip.minecraft;

import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import com.starskyxiii.polyglottooltip.core.LanguageCodePolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class MinecraftLanguageAdapter extends SimplePreparableReloadListener<List<ClientLanguage>> {
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

    @Override
    protected List<ClientLanguage> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
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

    @Override
    protected void apply(List<ClientLanguage> languages,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        LanguageCache.getInstance().replaceLanguages(languages);
        for (Runnable invalidator : dependentCacheInvalidators) {
            invalidator.run();
        }
    }

    public void reloadImmediate() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        ResourceManager resourceManager = minecraft.getResourceManager();
        List<ClientLanguage> languages = prepare(resourceManager, InactiveProfiler.INSTANCE);
        apply(languages, resourceManager, InactiveProfiler.INSTANCE);
    }
}
