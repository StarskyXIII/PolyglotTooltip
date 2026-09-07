package com.starskyxiii.polyglottooltip;

import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageCacheComponentTest {

    @AfterEach
    void clearLoadedLanguages() {
        setLoadedLanguages(List.of());
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

        assertEquals(List.of("Fallback"), resolveAll(
                Component.translatableWithFallback("missing", "Fallback"), List.of(language)));
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

    private static ClientLanguage language(Map<String, String> translations) {
        try {
            Constructor<ClientLanguage> constructor = ClientLanguage.class.getDeclaredConstructor(
                    Map.class, boolean.class, Map.class);
            constructor.setAccessible(true);
            return constructor.newInstance(translations, false, Map.of());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("ClientLanguage could not be constructed", exception);
        }
    }

    private static List<String> resolveAll(Component component, List<ClientLanguage> languages) {
        setLoadedLanguages(languages);
        return LanguageCache.getInstance().resolveComponentsForAll(component);
    }

    private static void setLoadedLanguages(List<ClientLanguage> languages) {
        try {
            Field field = LanguageCache.class.getDeclaredField("loadedLanguages");
            field.setAccessible(true);
            field.set(LanguageCache.getInstance(), List.copyOf(languages));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("LanguageCache languages could not be configured", exception);
        }
    }
}
