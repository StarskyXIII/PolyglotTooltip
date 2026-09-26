package com.starskyxiii.polyglottooltip.minecraft;
//~ minecraft_identifier

import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageResourceLoadingTest {
    private static final String NAMESPACE = "fixture";

    @Test
    void loadsPackStacksAndFallsBackAcrossTheLanguageChain() {
        ResourceManager resources = new InMemoryResourceManager(Map.of(
                languageFile("en_us"), List.of(
                        resource("""
                                {
                                  "contract.shared": "English Low",
                                  "contract.low_unique": "English Low Unique",
                                  "contract.english_one": "English One",
                                  "contract.english_two": "English Two"
                                }
                                """),
                        resource("""
                                {
                                  "contract.shared": "English High"
                                }
                                """)),
                languageFile("zh_tw"), List.of(
                        resource("""
                                {
                                  "contract.shared": "Secondary Low",
                                  "contract.low_unique": "Secondary Low Unique"
                                }
                                """),
                        resource("""
                                {
                                  "contract.shared": "Secondary High"
                                }
                                """))
        ));

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
        ResourceManager resources = new InMemoryResourceManager(Map.of(
                legacyLanguageFile("zh_cn"), List.of(
                        resource("\uFEFFtile.storagedrawers.basicDrawers.fulldrawers1.name=基础抽屉\n"),
                        resource("tile.storagedrawers.basicDrawers.fulldrawers1.name=资源包抽屉\n"
                                + "tile.storagedrawers.trim.oak.name=橡木桥接方块\n")),
                legacyLanguageFile("en_us"), List.of(
                        resource("tile.storagedrawers.basicDrawers.fulldrawers1.name=Basic Drawers\n"))
        ));

        Map<String, String> translations =
                MinecraftLanguageAdapter.loadStorageDrawersLegacyLanguage(resources, "ZH_CN");

        assertEquals("资源包抽屉",
                translations.get("tile.storagedrawers.basicDrawers.fulldrawers1.name"));
        assertEquals("橡木桥接方块", translations.get("tile.storagedrawers.trim.oak.name"));
        assertEquals(Map.of(),
                MinecraftLanguageAdapter.loadStorageDrawersLegacyLanguage(resources, "en_us"));
    }

    @Test
    void allowsStorageDrawersLanguageResourcesToBeAbsent() {
        ResourceManager resources = new InMemoryResourceManager(Map.of());

        assertEquals(Map.of(),
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

            @Override
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

    private record InMemoryResourceManager(Map<ResourceLocation, List<Resource>> stacks)
            implements ResourceManager {
        @Override
        public Set<String> getNamespaces() {
            return Set.of(NAMESPACE);
        }

        //? if <=1.18.2 {
        /*@Override
        public Resource getResource(ResourceLocation location) throws java.io.IOException {
            List<Resource> resources = getResources(location);
            return resources.get(resources.size() - 1);
        }

        @Override
        public boolean hasResource(ResourceLocation location) {
            return !stacks.getOrDefault(location, List.of()).isEmpty();
        }

        @Override
        public List<Resource> getResources(ResourceLocation location) throws java.io.IOException {
            List<Resource> resources = stacks.getOrDefault(location, List.of());
            if (resources.isEmpty()) {
                throw new java.io.FileNotFoundException(location.toString());
            }
            return resources;
        }

        @Override
        public java.util.Collection<ResourceLocation> listResources(String path, Predicate<String> filter) {
            return List.of();
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
            return stacks.getOrDefault(location, List.of());
        }

        @Override
        public Map<ResourceLocation, Resource> listResources(
                String path,
                Predicate<ResourceLocation> filter) {
            return Map.of();
        }

        @Override
        public Map<ResourceLocation, List<Resource>> listResourceStacks(
                String path,
                Predicate<ResourceLocation> filter) {
            return Map.of();
        }
        //?}

        @Override
        public Stream<PackResources> listPacks() {
            return Stream.empty();
        }
    }
}
