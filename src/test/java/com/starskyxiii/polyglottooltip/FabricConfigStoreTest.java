package com.starskyxiii.polyglottooltip;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricConfigStoreTest {
    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void restoreDefaults() {
        Config.DISPLAY_LANGUAGE.set(List.of("en_us"));
        Config.ALWAYS_SHOW.set(false);
        Config.ENABLE_CHINESE_SCRIPT_MATCHING.set(true);
        Config.TOOLTIP_SECONDARY_NAME_FORMAT.set("&7");
        Config.JADE_SECONDARY_NAME_FORMAT.set("&7");
    }

    @Test
    void createsNewFabricConfigWithFabricDefaults() throws IOException {
        Path path = temporaryDirectory.resolve("polyglottooltip.properties");

        FabricConfigStore.LoadResult result = Config.load(path);

        assertTrue(result.successful());
        assertTrue(Files.isRegularFile(path));
        assertTrue(Config.ENABLE_CHINESE_SCRIPT_MATCHING.get());
        Properties persisted = new Properties();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            persisted.load(reader);
        }
        assertEquals(5, persisted.size());
        assertEquals("en_us", persisted.getProperty("displayLanguage"));
        assertEquals("false", persisted.getProperty("alwaysShow"));
        assertEquals("true", persisted.getProperty("enableChineseScriptMatching"));
        assertEquals("&7", persisted.getProperty("tooltipSecondaryNameFormat"));
        assertEquals("&7", persisted.getProperty("jadeSecondaryNameFormat"));
    }

    @Test
    void savesAndReloadsOrderedLanguagesExplicitFalseAndEmptyList() {
        Path path = temporaryDirectory.resolve("polyglottooltip.properties");
        Config.DISPLAY_LANGUAGE.set(List.of("ja_jp", "en_us"));
        Config.ALWAYS_SHOW.set(true);
        Config.ENABLE_CHINESE_SCRIPT_MATCHING.set(false);
        Config.TOOLTIP_SECONDARY_NAME_FORMAT.set("&e&l");
        Config.JADE_SECONDARY_NAME_FORMAT.set("");
        Config.save(path);

        restoreDefaults();
        FabricConfigStore.LoadResult loaded = Config.load(path);

        assertTrue(loaded.successful());
        assertTrue(loaded.changed());
        assertEquals(List.of("ja_jp", "en_us"), Config.DISPLAY_LANGUAGE.get());
        assertTrue(Config.ALWAYS_SHOW.get());
        assertFalse(Config.ENABLE_CHINESE_SCRIPT_MATCHING.get());
        assertEquals("&e&l", Config.TOOLTIP_SECONDARY_NAME_FORMAT.get());
        assertEquals("", Config.JADE_SECONDARY_NAME_FORMAT.get());
        assertFalse(Config.load(path).changed());

        Config.DISPLAY_LANGUAGE.set(List.of());
        Config.save(path);
        Config.DISPLAY_LANGUAGE.set(List.of("en_us"));
        assertTrue(Config.load(path).successful());
        assertEquals(List.of(), Config.DISPLAY_LANGUAGE.get());
    }

    @Test
    void missingAndInvalidValuesUseFabricDefaults() throws IOException {
        Path path = temporaryDirectory.resolve("polyglottooltip.properties");
        Config.DISPLAY_LANGUAGE.set(List.of("ja_jp"));
        Config.ALWAYS_SHOW.set(true);
        Config.ENABLE_CHINESE_SCRIPT_MATCHING.set(false);
        Config.TOOLTIP_SECONDARY_NAME_FORMAT.set("&e");
        Config.JADE_SECONDARY_NAME_FORMAT.set("&a");
        Files.writeString(path,
                "alwaysShow=invalid\nenableChineseScriptMatching=invalid\n" +
                        "tooltipSecondaryNameFormat=&z\njadeSecondaryNameFormat=text\n",
                StandardCharsets.UTF_8);

        FabricConfigStore.LoadResult result = Config.load(path);

        assertTrue(result.successful());
        assertEquals(List.of("en_us"), Config.DISPLAY_LANGUAGE.get());
        assertFalse(Config.ALWAYS_SHOW.get());
        assertTrue(Config.ENABLE_CHINESE_SCRIPT_MATCHING.get());
        assertEquals("&7", Config.TOOLTIP_SECONDARY_NAME_FORMAT.get());
        assertEquals("&7", Config.JADE_SECONDARY_NAME_FORMAT.get());
    }

    @Test
    void preservesAcceptedValuesWhenReadFailsAndAcceptsRetry() throws IOException {
        Path path = temporaryDirectory.resolve("polyglottooltip.properties");
        Config.DISPLAY_LANGUAGE.set(List.of("ja_jp", "en_us"));
        Config.ALWAYS_SHOW.set(true);
        Files.writeString(path,
                "displayLanguage=zh_cn\nbroken=" + "\\" + "uZZZZ\n",
                StandardCharsets.UTF_8);

        FabricConfigStore.LoadResult failed = Config.load(path);

        assertFalse(failed.successful());
        assertEquals(List.of("ja_jp", "en_us"), Config.DISPLAY_LANGUAGE.get());
        assertTrue(Config.ALWAYS_SHOW.get());

        Files.writeString(path,
                "displayLanguage=zh_cn,en_us\nalwaysShow=false\nenableChineseScriptMatching=false\n" +
                        "tooltipSecondaryNameFormat=&e\njadeSecondaryNameFormat=&a\n",
                StandardCharsets.UTF_8);
        FabricConfigStore.LoadResult retried = Config.load(path);

        assertTrue(retried.successful());
        assertTrue(retried.changed());
        assertEquals(List.of("zh_cn", "en_us"), Config.DISPLAY_LANGUAGE.get());
        assertFalse(Config.ALWAYS_SHOW.get());
        assertFalse(Config.ENABLE_CHINESE_SCRIPT_MATCHING.get());
    }

    @Test
    void directoryAtConfigPathIsAReadFailure() throws IOException {
        Path path = temporaryDirectory.resolve("polyglottooltip.properties");
        Files.createDirectory(path);
        Config.DISPLAY_LANGUAGE.set(List.of("ja_jp"));

        FabricConfigStore.LoadResult result = Config.load(path);

        assertFalse(result.successful());
        assertEquals(List.of("ja_jp"), Config.DISPLAY_LANGUAGE.get());
    }

    @Test
    void failedFirstSaveDoesNotApplyDefaultsOverAcceptedValues() throws IOException {
        Path parentFile = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(parentFile, "occupied", StandardCharsets.UTF_8);
        Path path = parentFile.resolve("polyglottooltip.properties");
        Config.DISPLAY_LANGUAGE.set(List.of("ja_jp"));
        Config.ALWAYS_SHOW.set(true);
        Config.ENABLE_CHINESE_SCRIPT_MATCHING.set(false);

        FabricConfigStore.LoadResult result = Config.load(path);

        assertFalse(result.successful());
        assertEquals(List.of("ja_jp"), Config.DISPLAY_LANGUAGE.get());
        assertTrue(Config.ALWAYS_SHOW.get());
        assertFalse(Config.ENABLE_CHINESE_SCRIPT_MATCHING.get());
    }
}
