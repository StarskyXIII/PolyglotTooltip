package com.starskyxiii.polyglottooltip;
//~ minecraft_identifier

import com.starskyxiii.polyglottooltip.core.LanguageCodePolicy;
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

        ClientLanguage english = ClientLanguage.loadFrom(
                resources,
                LanguageCodePolicy.fallbackChain("en_us"),
                false);
        ClientLanguage secondary = ClientLanguage.loadFrom(
                resources,
                LanguageCodePolicy.fallbackChain("zh_tw"),
                false);

        assertEquals("English High", english.getOrDefault("contract.shared", null));
        assertEquals("English Low Unique", english.getOrDefault("contract.low_unique", null));
        assertEquals("Secondary High", secondary.getOrDefault("contract.shared", null));
        assertEquals("Secondary Low Unique", secondary.getOrDefault("contract.low_unique", null));
        assertEquals("English One", secondary.getOrDefault("contract.english_one", null));
        assertEquals("English Two", secondary.getOrDefault("contract.english_two", null));
    }

    private static ResourceLocation languageFile(String languageCode) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, "lang/" + languageCode + ".json");
    }

    private static Resource resource(String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return new Resource((PackResources) null, () -> new ByteArrayInputStream(bytes));
    }

    private record InMemoryResourceManager(Map<ResourceLocation, List<Resource>> stacks)
            implements ResourceManager {
        @Override
        public Set<String> getNamespaces() {
            return Set.of(NAMESPACE);
        }

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

        @Override
        public Stream<PackResources> listPacks() {
            return Stream.empty();
        }
    }
}
