package com.starskyxiii.polyglottooltip.minecraft;
//~ minecraft_identifier minecraft_116_packages minecraft_116_compound_names minecraft_116_language_map minecraft_116_pack_resource

import com.starskyxiii.polyglottooltip.LanguageCache;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageResourceLoadingTest {
    private static final String NAMESPACE = "fixture";

    //? if <26 {
    @Test
    void preparedReloadDoesNotOverwriteNewerLanguageConfiguration() {
        Map<ResourceLocation, List<Resource>> stacks = new LinkedHashMap<>();
        stacks.put(languageFile("en_us"), Collections.singletonList(
                resource("{\"contract.shared\":\"English\"}")));
        stacks.put(languageFile("zh_tw"), Collections.singletonList(
                resource("{\"contract.shared\":\"Secondary\"}")));
        ResourceManager resources = new InMemoryResourceManager(stacks);
        AtomicReference<List<String>> configured = new AtomicReference<>(Collections.singletonList("en_us"));
        CompletableFuture<Void> release = new CompletableFuture<>();
        MinecraftLanguageAdapter adapter = MinecraftLanguageAdapter.getInstance();
        LanguageCache cache = LanguageCache.getInstance();
        adapter.configureLanguages(configured::get);
        try {
            CompletableFuture<Void> reload = adapter.reload(
                    //? if <=1.16.5 {
                    /*new net.minecraft.resources.IFutureReloadListener.IStage() {
                    *///?} else {
                    new net.minecraft.server.packs.resources.PreparableReloadListener.PreparationBarrier() {
                    //?}
                        @Override
                        public <T> CompletableFuture<T> wait(T prepared) {
                            return release.thenApply(ignored -> prepared);
                        }
                    }, resources, null, null, Runnable::run, Runnable::run);
            configured.set(Collections.singletonList("zh_tw"));
            cache.replaceLanguages(Collections.singletonList(
                    MinecraftLanguageAdapter.loadLanguage(resources, "zh_tw")));
            assertEquals(Collections.singletonList("Secondary"), cache.resolveComponentsForAll(
                    ComponentFactory.translatable("contract.shared")));
            release.complete(null);
            reload.join();
            assertEquals(Collections.singletonList("Secondary"), cache.resolveComponentsForAll(
                    ComponentFactory.translatable("contract.shared")));
        } finally {
            release.complete(null);
            adapter.configureLanguages(Collections::emptyList);
            cache.replaceLanguages(Collections.emptyList());
        }
    }
    //?}

    @Test
    void loadsPackStacksAndFallsBackAcrossTheLanguageChain() {
        Map<ResourceLocation, List<Resource>> stacks = new LinkedHashMap<>();
        stacks.put(languageFile("en_us"), java.util.Arrays.asList(
                resource("{\n"
                        + "  \"contract.shared\": \"English Low\",\n"
                        + "  \"contract.low_unique\": \"English Low Unique\",\n"
                        + "  \"contract.english_one\": \"English One\",\n"
                        + "  \"contract.english_two\": \"English Two\"\n"
                        + "}\n"),
                resource("{\n"
                        + "  \"contract.shared\": \"English High\"\n"
                        + "}\n")));
        stacks.put(languageFile("zh_tw"), java.util.Arrays.asList(
                resource("{\n"
                        + "  \"contract.shared\": \"Secondary Low\",\n"
                        + "  \"contract.low_unique\": \"Secondary Low Unique\"\n"
                        + "}\n"),
                resource("{\n"
                        + "  \"contract.shared\": \"Secondary High\"\n"
                        + "}\n")));
        ResourceManager resources = new InMemoryResourceManager(stacks);

        ClientLanguage english = MinecraftLanguageAdapter.loadLanguage(resources, "en_us");
        ClientLanguage secondary = MinecraftLanguageAdapter.loadLanguage(resources, "zh_tw");

        assertEquals("English High", getTranslation(english, "contract.shared"));
        assertEquals("English Low Unique", getTranslation(english, "contract.low_unique"));
        assertEquals("Secondary High", getTranslation(secondary, "contract.shared"));
        assertEquals("Secondary Low Unique", getTranslation(secondary, "contract.low_unique"));
        assertEquals("English One", getTranslation(secondary, "contract.english_one"));
        assertEquals("English Two", getTranslation(secondary, "contract.english_two"));
    }

    //? if <=1.19.2 {
    /*@Test
    void loadsStorageDrawersLegacyLanguageWithResourcePackPriority() {
        Map<ResourceLocation, List<Resource>> stacks = new LinkedHashMap<>();
        stacks.put(legacyLanguageFile("zh_cn"), java.util.Arrays.asList(
                        resource("\uFEFFtile.storagedrawers.basicDrawers.fulldrawers1.name=基础抽屉\n"),
                        resource("tile.storagedrawers.basicDrawers.fulldrawers1.name=资源包抽屉\n"
                                + "tile.storagedrawers.trim.oak.name=橡木桥接方块\n")));
        stacks.put(legacyLanguageFile("en_us"), Collections.singletonList(
                resource("tile.storagedrawers.basicDrawers.fulldrawers1.name=Basic Drawers\n")));
        ResourceManager resources = new InMemoryResourceManager(stacks);

        Map<String, String> translations =
                MinecraftLanguageAdapter.loadStorageDrawersLegacyLanguage(resources, "ZH_CN");

        assertEquals("资源包抽屉",
                translations.get("tile.storagedrawers.basicDrawers.fulldrawers1.name"));
        assertEquals("橡木桥接方块", translations.get("tile.storagedrawers.trim.oak.name"));
        assertEquals(Collections.emptyMap(),
                MinecraftLanguageAdapter.loadStorageDrawersLegacyLanguage(resources, "en_us"));
    }

    @Test
    void allowsStorageDrawersLanguageResourcesToBeAbsent() {
        ResourceManager resources = new InMemoryResourceManager(Collections.emptyMap());

        assertEquals(Collections.emptyMap(),
                MinecraftLanguageAdapter.loadStorageDrawersLegacyLanguage(resources, "zh_cn"));
    }
    *///?}

    private static String getTranslation(ClientLanguage language, String key) {
        //? if <=1.19.2 {
        /*return language.has(key) ? language.getOrDefault(key) : null;
        *///?} else {
        return language.getOrDefault(key, null);
        //?}
    }

    private static ResourceLocation languageFile(String languageCode) {
        //? if <=1.20.1 {
        /*return new ResourceLocation(NAMESPACE, "lang/" + languageCode + ".json");
        *///?} else {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, "lang/" + languageCode + ".json");
        //?}
    }

    //? if <=1.19.2 {
    /*private static ResourceLocation legacyLanguageFile(String languageCode) {
        return new ResourceLocation("storagedrawers", "lang/" + languageCode + ".lang");
    }
    *///?}

    private static Resource resource(String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        //? if <=1.18.2 {
        /*return new Resource() {
            @Override
            public ResourceLocation getLocation() {
                return languageFile("fixture");
            }

            @Override
            public java.io.InputStream getInputStream() {
                return new ByteArrayInputStream(bytes);
            }

            public boolean hasMetadata() {
                return false;
            }

            @Override
            public <T> T getMetadata(net.minecraft.server.packs.metadata.MetadataSectionSerializer<T> serializer) {
                return null;
            }

            @Override
            public String getSourceName() {
                return "fixture";
            }

            @Override
            public void close() {
            }
        };
        *///?} else if <=1.19.2 {
        /*return new Resource("fixture", () -> new ByteArrayInputStream(bytes));
        *///?} else {
        return new Resource((PackResources) null, () -> new ByteArrayInputStream(bytes));
        //?}
    }

    private static final class InMemoryResourceManager implements ResourceManager {
        private final Map<ResourceLocation, List<Resource>> stacks;

        private InMemoryResourceManager(Map<ResourceLocation, List<Resource>> stacks) {
            this.stacks = stacks;
        }

        @Override
        public Set<String> getNamespaces() {
            return Collections.singleton(NAMESPACE);
        }

        //? if <=1.18.2 {
        /*@Override
        public Resource getResource(ResourceLocation location) throws java.io.IOException {
            List<Resource> resources = getResources(location);
            return resources.get(resources.size() - 1);
        }

        @Override
        public boolean hasResource(ResourceLocation location) {
            return !stacks.getOrDefault(location, Collections.emptyList()).isEmpty();
        }

        @Override
        public List<Resource> getResources(ResourceLocation location) throws java.io.IOException {
            List<Resource> resources = stacks.getOrDefault(location, Collections.emptyList());
            if (resources.isEmpty()) {
                throw new java.io.FileNotFoundException(location.toString());
            }
            return resources;
        }

        @Override
        public java.util.Collection<ResourceLocation> listResources(String path, Predicate<String> filter) {
            return Collections.emptyList();
        }
        *///?} else {
        @Override
        public Optional<Resource> getResource(ResourceLocation location) {
            List<Resource> resources = getResourceStack(location);
            return resources.isEmpty()
                    ? Optional.empty()
                    : Optional.of(resources.get(resources.size() - 1));
        }

        @Override
        public List<Resource> getResourceStack(ResourceLocation location) {
            return stacks.getOrDefault(location, Collections.emptyList());
        }

        @Override
        public Map<ResourceLocation, Resource> listResources(
                String path,
                Predicate<ResourceLocation> filter) {
            return Collections.emptyMap();
        }

        @Override
        public Map<ResourceLocation, List<Resource>> listResourceStacks(
                String path,
                Predicate<ResourceLocation> filter) {
            return Collections.emptyMap();
        }
        //?}

        @Override
        public Stream<PackResources> listPacks() {
            return Stream.empty();
        }
    }
}
