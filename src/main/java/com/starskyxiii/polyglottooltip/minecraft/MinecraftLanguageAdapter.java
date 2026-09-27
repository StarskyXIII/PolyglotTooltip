package com.starskyxiii.polyglottooltip.minecraft;
//~ minecraft_116_packages minecraft_116_language_map

import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import com.starskyxiii.polyglottooltip.core.LanguageCodePolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
//? if <=1.16.5 {
/*import net.minecraft.client.resources.Language;
*///?} else if <=1.19.2 {
/*import net.minecraft.client.resources.language.LanguageInfo;
*///?}
import net.minecraft.server.packs.resources.PreparableReloadListener;
//? if <=1.19.2 {
/*import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
*///?}
import net.minecraft.server.packs.resources.ResourceManager;
//? if fabric && <26 {
/*import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
*///?}
//? if <26 {
import net.minecraft.util.profiling.ProfilerFiller;
//?}

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
//? if <=1.19.2 {
/*import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Properties;
*///?}
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class MinecraftLanguageAdapter implements PreparableReloadListener
        //? if fabric && <26
        //, IdentifiableResourceReloadListener
{
    private static final MinecraftLanguageAdapter INSTANCE = new MinecraftLanguageAdapter();

    private final List<Runnable> dependentCacheInvalidators = new ArrayList<>();
    private Supplier<? extends List<? extends String>> configuredLanguages = Collections::emptyList;

    private MinecraftLanguageAdapter() {
    }

    public static MinecraftLanguageAdapter getInstance() {
        return INSTANCE;
    }

    //? if fabric && <26 {
    /*@Override
    public ResourceLocation getFabricId() {
        //? if <=1.20.1 {
        /^return new ResourceLocation(PolyglotTooltip.MODID, "language_cache");
        ^///?} else {
        return ResourceLocation.fromNamespaceAndPath(PolyglotTooltip.MODID, "language_cache");
        //?}
    }
    *///?}

    public void configureLanguages(Supplier<? extends List<? extends String>> configuredLanguages) {
        this.configuredLanguages = Objects.requireNonNull(configuredLanguages);
    }

    public void registerDependentCacheInvalidator(Runnable invalidator) {
        dependentCacheInvalidators.add(Objects.requireNonNull(invalidator));
    }

    private LoadedLanguages loadLanguages(ResourceManager resourceManager) {
        List<String> requestedLanguages = new ArrayList<>(configuredLanguages.get());
        List<ClientLanguage> result = new ArrayList<>();
        List<Map<String, String>> legacyEntries = new ArrayList<>();
        for (String languageCode : requestedLanguages) {
            try {
                ClientLanguage language = loadLanguage(resourceManager, languageCode);
                Map<String, String> legacyLanguage = Collections.emptyMap();
                //? if <=1.19.2 {
                /*legacyLanguage = loadStorageDrawersLegacyLanguage(resourceManager, languageCode);
                *///?}
                PolyglotTooltip.LOGGER.info("[PolyglotTooltip] Loaded secondary language: {}", languageCode);
                result.add(language);
                legacyEntries.add(legacyLanguage);
            } catch (Exception exception) {
                PolyglotTooltip.LOGGER.error(
                        "[PolyglotTooltip] Failed to load language '{}': {}",
                        languageCode,
                        exception.getMessage());
            }
        }
        ClientLanguage english = null;
        //? if <=1.19.2 {
        /*try {
            english = loadLanguage(resourceManager, "en_us");
        } catch (RuntimeException exception) {
            PolyglotTooltip.LOGGER.error(
                    "[PolyglotTooltip] Failed to load English fallback: {}",
                    exception.getMessage());
        }
        *///?}
        return new LoadedLanguages(requestedLanguages, result, english, legacyEntries);
    }

    static ClientLanguage loadLanguage(ResourceManager resourceManager, String languageCode) {
        //? if <=1.19.2 {
        /*//? if <=1.16.5 {
        List<Language> languageInfos = LanguageCodePolicy.fallbackChain(languageCode).stream()
                .map(code -> new Language(code, "", code, false))
        //?} else {
        List<LanguageInfo> languageInfos = LanguageCodePolicy.fallbackChain(languageCode).stream()
                .map(code -> new LanguageInfo(code, "", code, false))
        //?}
                .collect(Collectors.toList());
        return ClientLanguage.loadFrom(resourceManager, languageInfos);
        *///?} else {
        return ClientLanguage.loadFrom(
                resourceManager,
                LanguageCodePolicy.fallbackChain(languageCode),
                false);
        //?}
    }

    //? if <=1.18.2 {
    /*static Map<String, String> loadStorageDrawersLegacyLanguage(ResourceManager resourceManager,
                                                                 String languageCode) {
        if ("en_us".equalsIgnoreCase(languageCode)) {
            return Collections.emptyMap();
        }
        Map<String, String> translations = new LinkedHashMap<>();
        ResourceLocation location = new ResourceLocation(
                "storagedrawers", "lang/" + languageCode.toLowerCase(java.util.Locale.ROOT) + ".lang");
        List<Resource> resources;
        try {
            resources = resourceManager.getResources(location);
        } catch (FileNotFoundException exception) {
            return Collections.emptyMap();
        } catch (IOException exception) {
            PolyglotTooltip.LOGGER.warn(
                    "[PolyglotTooltip] Failed to list Storage Drawers legacy language '{}': {}",
                    languageCode,
                    exception.getMessage());
            return Collections.emptyMap();
        }
        for (Resource resource : resources) {
            Properties properties = new Properties();
            try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException | IllegalArgumentException exception) {
                PolyglotTooltip.LOGGER.warn(
                        "[PolyglotTooltip] Failed to load Storage Drawers legacy language '{}': {}",
                        languageCode,
                        exception.getMessage());
                continue;
            }
            for (String key : properties.stringPropertyNames()) {
                String normalizedKey = key.startsWith("\uFEFF") ? key.substring(1) : key;
                translations.put(normalizedKey, properties.getProperty(key));
            }
        }
        return Collections.unmodifiableMap(translations);
    }
    *///?} else if <=1.19.2 {
    /*static Map<String, String> loadStorageDrawersLegacyLanguage(ResourceManager resourceManager,
                                                                 String languageCode) {
        if ("en_us".equalsIgnoreCase(languageCode)) {
            return Collections.emptyMap();
        }
        Map<String, String> translations = new LinkedHashMap<>();
        ResourceLocation location = new ResourceLocation(
                "storagedrawers", "lang/" + languageCode.toLowerCase(java.util.Locale.ROOT) + ".lang");
        for (Resource resource : resourceManager.getResourceStack(location)) {
            Properties properties = new Properties();
            try (InputStreamReader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException | IllegalArgumentException exception) {
                PolyglotTooltip.LOGGER.warn(
                        "[PolyglotTooltip] Failed to load Storage Drawers legacy language '{}': {}",
                        languageCode,
                        exception.getMessage());
                continue;
            }
            for (String key : properties.stringPropertyNames()) {
                String normalizedKey = key.startsWith("\uFEFF") ? key.substring(1) : key;
                translations.put(normalizedKey, properties.getProperty(key));
            }
        }
        return Collections.unmodifiableMap(translations);
    }
    *///?}

    private void applyLanguages(ResourceManager resourceManager, LoadedLanguages languages) {
        if (!languages.requestedLanguages().equals(configuredLanguages.get())) {
            languages = loadLanguages(resourceManager);
        }
        LanguageCache.getInstance().replaceLanguages(
                languages.secondary(), languages.english(), languages.legacyEntries());
        invalidateDependentCaches();
    }

    public void invalidateDependentCaches() {
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
                .thenAcceptAsync(languages -> applyLanguages(resourceManager, languages), applyExecutor);
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
                .thenAcceptAsync(languages -> applyLanguages(resourceManager, languages), applyExecutor);
    //?}
    }

    public void reloadImmediate() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        ResourceManager resourceManager = minecraft.getResourceManager();
        applyLanguages(resourceManager, loadLanguages(resourceManager));
    }

    private static final class LoadedLanguages {
        private final List<String> requestedLanguages;
        private final List<ClientLanguage> secondary;
        private final ClientLanguage english;
        private final List<Map<String, String>> legacyEntries;

        private LoadedLanguages(List<String> requestedLanguages,
                                List<ClientLanguage> secondary,
                                ClientLanguage english,
                                List<Map<String, String>> legacyEntries) {
            this.requestedLanguages = Collections.unmodifiableList(new ArrayList<>(requestedLanguages));
            this.secondary = secondary;
            this.english = english;
            this.legacyEntries = legacyEntries;
        }

        private List<String> requestedLanguages() { return requestedLanguages; }
        private List<ClientLanguage> secondary() { return secondary; }
        private ClientLanguage english() { return english; }
        private List<Map<String, String>> legacyEntries() { return legacyEntries; }
    }
}
