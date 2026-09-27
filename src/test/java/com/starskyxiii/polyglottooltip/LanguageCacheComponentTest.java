package com.starskyxiii.polyglottooltip;
//~ minecraft_116_packages minecraft_116_language_map minecraft_legacy_components minecraft_118_factories

import com.starskyxiii.polyglottooltip.minecraft.ComponentFactory;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageCacheComponentTest {

    @AfterEach
    void clearLoadedLanguages() {
        LanguageCache.getInstance().replaceLanguages(List.of());
    }

    @Test
    void resolvesNestedArgumentsAndSiblingsRecursively() {
        ClientLanguage language = language(Map.of(
                "outer", "%s / %s",
                "inner", "Nested"
        ));
        Component component = ComponentFactory.translatable(
                "outer",
                ComponentFactory.translatable("inner"),
                ComponentFactory.literal("!"))
                .append(ComponentFactory.literal(" tail"));

        assertEquals(List.of("Nested / ! tail"), resolveAll(component, List.of(language)));
    }

    @Test
    void usesExplicitFallbackAndOmitsUntranslatedComponents() {
        ClientLanguage language = language(Map.of());

        //? if >1.19.2 {
        assertEquals(List.of("Fallback"), resolveAll(
                Component.translatableWithFallback("missing", "Fallback"), List.of(language)));
        //?}
        assertTrue(resolveAll(ComponentFactory.translatable("missing"), List.of(language)).isEmpty());
        assertEquals(List.of("literal"), resolveAll(ComponentFactory.literal("literal"), List.of(language)));
    }

    @Test
    void preservesLanguageOrderAndDeduplicatesResolvedNames() {
        Component component = ComponentFactory.translatable("name");
        ClientLanguage first = language(Map.of("name", "First"));
        ClientLanguage duplicate = language(Map.of("name", "First"));
        ClientLanguage second = language(Map.of("name", "Second"));

        assertEquals(List.of("First", "Second"),
                resolveAll(component, List.of(first, duplicate, second)));
    }

    @Test
    void preservesMissingChildAndMalformedFormatFallbacks() {
        ClientLanguage language = language(Map.of(
                "outer", "[%s]",
                "malformed", "%q"
        ));
        Component missingChildren = ComponentFactory.translatable(
                "outer",
                ComponentFactory.translatable("missing.argument"))
                .append(ComponentFactory.translatable("missing.sibling"))
                .append(ComponentFactory.literal(" tail"));

        assertEquals(List.of("[] tail"), resolveAll(missingChildren, List.of(language)));
        assertEquals(List.of("%q"), resolveAll(
                ComponentFactory.translatable("malformed", "unused"), List.of(language)));
    }

    private static ClientLanguage language(Map<String, String> translations) {
        try {
            Constructor<ClientLanguage> constructor;
            try {
                constructor = ClientLanguage.class.getDeclaredConstructor(
                    Map.class, boolean.class);
            } catch (NoSuchMethodException exception) {
                constructor = ClientLanguage.class.getDeclaredConstructor(
                        Map.class, boolean.class, Map.class);
            }
            constructor.setAccessible(true);
            return constructor.getParameterCount() == 2
                    ? constructor.newInstance(translations, false)
                    : constructor.newInstance(translations, false, Map.of());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("ClientLanguage could not be constructed", exception);
        }
    }

    private static List<String> resolveAll(Component component, List<ClientLanguage> languages) {
        LanguageCache.getInstance().replaceLanguages(languages);
        return LanguageCache.getInstance().resolveComponentsForAll(component);
    }
}
