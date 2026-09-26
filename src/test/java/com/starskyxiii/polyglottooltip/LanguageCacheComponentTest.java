package com.starskyxiii.polyglottooltip;

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
        Component component = Component.translatable(
                "outer",
                Component.translatable("inner"),
                Component.literal("!"))
                .append(Component.literal(" tail"));

        assertEquals(List.of("Nested / ! tail"), resolveAll(component, List.of(language)));
    }

    @Test
    void usesExplicitFallbackAndOmitsUntranslatedComponents() {
        ClientLanguage language = language(Map.of());

        //? if >1.19.2 {
        assertEquals(List.of("Fallback"), resolveAll(
                Component.translatableWithFallback("missing", "Fallback"), List.of(language)));
        //?}
        assertTrue(resolveAll(Component.translatable("missing"), List.of(language)).isEmpty());
        assertEquals(List.of("literal"), resolveAll(Component.literal("literal"), List.of(language)));
    }

    @Test
    void preservesLanguageOrderAndDeduplicatesResolvedNames() {
        Component component = Component.translatable("name");
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
        Component missingChildren = Component.translatable(
                "outer",
                Component.translatable("missing.argument"))
                .append(Component.translatable("missing.sibling"))
                .append(Component.literal(" tail"));

        assertEquals(List.of("[] tail"), resolveAll(missingChildren, List.of(language)));
        assertEquals(List.of("%q"), resolveAll(
                Component.translatable("malformed", "unused"), List.of(language)));
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
