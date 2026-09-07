package com.starskyxiii.polyglottooltip;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;

import java.lang.reflect.Constructor;
import java.nio.file.Path;

public final class TestConfigSupport {

    private TestConfigSupport() {
    }

    public static void load(boolean chineseScriptMatching) {
        load(chineseScriptMatching, "&7", "&7");
    }

    public static void load(boolean chineseScriptMatching, String tooltipFormat, String jadeFormat) {
        CommentedConfig config = CommentedConfig.inMemory();
        config.set("enableChineseScriptMatching", chineseScriptMatching);
        config.set("tooltipSecondaryNameFormat", tooltipFormat);
        config.set("jadeSecondaryNameFormat", jadeFormat);
        Config.SPEC.correct(config);
        Config.SPEC.acceptConfig(createLoadedConfig(config));
        ChineseScriptSearchMatcher.clearCaches();
    }

    public static void setChineseScriptMatching(boolean enabled) {
        Config.ENABLE_CHINESE_SCRIPT_MATCHING.set(enabled);
    }

    private static IConfigSpec.ILoadedConfig createLoadedConfig(CommentedConfig config) {
        try {
            Class<?> loadedConfigClass = Class.forName("net.neoforged.fml.config.LoadedConfig");
            Constructor<?> constructor = loadedConfigClass.getDeclaredConstructor(
                    CommentedConfig.class, Path.class, ModConfig.class);
            constructor.setAccessible(true);
            return (IConfigSpec.ILoadedConfig) constructor.newInstance(
                    config, Path.of("build/test-config.toml"), null);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("NeoForge LoadedConfig could not be constructed", exception);
        }
    }
}
