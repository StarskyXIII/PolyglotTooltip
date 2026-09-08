package com.starskyxiii.polyglottooltip;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

final class FabricConfigStore {
    record LoadResult(boolean successful, boolean changed) {
        private static final LoadResult FAILED = new LoadResult(false, false);
    }

    private static final String DISPLAY_LANGUAGE = "displayLanguage";
    private static final String ALWAYS_SHOW = "alwaysShow";
    private static final String ENABLE_CHINESE_SCRIPT_MATCHING = "enableChineseScriptMatching";
    private static final String TOOLTIP_SECONDARY_NAME_FORMAT = "tooltipSecondaryNameFormat";
    private static final String JADE_SECONDARY_NAME_FORMAT = "jadeSecondaryNameFormat";
    private static Path lastFailedPath;
    private static String lastFailure;

    private FabricConfigStore() {
    }

    static synchronized LoadResult load(Path path) {
        Properties properties = new Properties();
        boolean missing = Files.notExists(path);
        if (!missing) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException | IllegalArgumentException exception) {
                logReadFailureOnce(path, exception);
                return LoadResult.FAILED;
            }
        }

        List<? extends String> languages = parseLanguages(properties.getProperty(DISPLAY_LANGUAGE));
        boolean alwaysShow = parseBoolean(properties.getProperty(ALWAYS_SHOW), false);
        boolean scriptMatching = parseBoolean(properties.getProperty(ENABLE_CHINESE_SCRIPT_MATCHING), true);
        String tooltipFormat = parseFormat(properties.getProperty(TOOLTIP_SECONDARY_NAME_FORMAT));
        String jadeFormat = parseFormat(properties.getProperty(JADE_SECONDARY_NAME_FORMAT));

        if (missing && !save(path, languages, alwaysShow, scriptMatching, tooltipFormat, jadeFormat)) {
            return LoadResult.FAILED;
        }

        boolean changed = !Config.DISPLAY_LANGUAGE.get().equals(languages)
                || Config.ALWAYS_SHOW.get() != alwaysShow
                || Config.ENABLE_CHINESE_SCRIPT_MATCHING.get() != scriptMatching
                || !Config.TOOLTIP_SECONDARY_NAME_FORMAT.get().equals(tooltipFormat)
                || !Config.JADE_SECONDARY_NAME_FORMAT.get().equals(jadeFormat);

        Config.DISPLAY_LANGUAGE.set(languages);
        Config.ALWAYS_SHOW.set(alwaysShow);
        Config.ENABLE_CHINESE_SCRIPT_MATCHING.set(scriptMatching);
        Config.TOOLTIP_SECONDARY_NAME_FORMAT.set(tooltipFormat);
        Config.JADE_SECONDARY_NAME_FORMAT.set(jadeFormat);

        lastFailedPath = null;
        lastFailure = null;
        return new LoadResult(true, changed);
    }

    static synchronized boolean save(Path path) {
        return save(
                path,
                Config.DISPLAY_LANGUAGE.get(),
                Config.ALWAYS_SHOW.get(),
                Config.ENABLE_CHINESE_SCRIPT_MATCHING.get(),
                Config.TOOLTIP_SECONDARY_NAME_FORMAT.get(),
                Config.JADE_SECONDARY_NAME_FORMAT.get());
    }

    private static boolean save(Path path,
                                List<? extends String> languages,
                                boolean alwaysShow,
                                boolean scriptMatching,
                                String tooltipFormat,
                                String jadeFormat) {
        Properties properties = new Properties();
        properties.setProperty(DISPLAY_LANGUAGE, String.join(",", languages));
        properties.setProperty(ALWAYS_SHOW, Boolean.toString(alwaysShow));
        properties.setProperty(ENABLE_CHINESE_SCRIPT_MATCHING, Boolean.toString(scriptMatching));
        properties.setProperty(TOOLTIP_SECONDARY_NAME_FORMAT, tooltipFormat);
        properties.setProperty(JADE_SECONDARY_NAME_FORMAT, jadeFormat);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                properties.store(writer, "Polyglot Tooltip client configuration");
            }
            return true;
        } catch (IOException exception) {
            PolyglotTooltip.LOGGER.warn("Unable to write Fabric configuration {}", path, exception);
            return false;
        }
    }

    private static void logReadFailureOnce(Path path, Exception exception) {
        String failure = exception.getClass().getName() + ":" + exception.getMessage();
        if (!path.equals(lastFailedPath) || !failure.equals(lastFailure)) {
            PolyglotTooltip.LOGGER.warn("Unable to read Fabric configuration {}", path, exception);
            lastFailedPath = path;
            lastFailure = failure;
        }
    }

    private static List<? extends String> parseLanguages(String value) {
        if (value == null) {
            return List.of("en_us");
        }
        List<String> result = new ArrayList<>();
        for (String part : value.split(",", -1)) {
            String language = part.trim();
            if (!language.isEmpty()) {
                result.add(language);
            }
        }
        return List.copyOf(result);
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return fallback;
    }

    private static String parseFormat(String value) {
        return value != null && LegacyFormatStyleUtil.isValidLegacyFormatString(value) ? value : "&7";
    }
}
