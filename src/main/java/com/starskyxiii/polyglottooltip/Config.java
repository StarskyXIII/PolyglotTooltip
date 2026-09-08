package com.starskyxiii.polyglottooltip;
//~ forge_neoforge_package

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final String CONFIG_TRANSLATION_PREFIX = "polyglottooltip.configuration.";

    private static String translationKey(String path) {
        return CONFIG_TRANSLATION_PREFIX + path;
    }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISPLAY_LANGUAGE = defineDisplayLanguage();

    public static final ModConfigSpec.BooleanValue ALWAYS_SHOW = BUILDER
            .comment("If true, show the extra language even when it matches the current game language.")
            .translation(translationKey("alwaysShow"))
            .define("alwaysShow", false);

    public static final ModConfigSpec.BooleanValue ENABLE_CHINESE_SCRIPT_MATCHING = defineChineseScriptMatching();

    public static final ModConfigSpec.ConfigValue<String> TOOLTIP_SECONDARY_NAME_FORMAT = BUILDER
            .comment("Legacy format codes applied to secondary-language names shown in normal tooltips.",
                     "Examples: \"&7\", \"&7&l\", \"&e&o\". Use an empty string for no extra formatting.")
            .translation(translationKey("tooltipSecondaryNameFormat"))
            .define("tooltipSecondaryNameFormat", "&7", LegacyFormatStyleUtil::isValidLegacyFormatString);

    public static final ModConfigSpec.ConfigValue<String> JADE_SECONDARY_NAME_FORMAT = BUILDER
            .comment("Legacy format codes applied to secondary-language names shown in Jade tooltips.",
                     "Examples: \"&7\", \"&7&l\", \"&e&o\". Use an empty string for no extra formatting.")
            .translation(translationKey("jadeSecondaryNameFormat"))
            .define("jadeSecondaryNameFormat", "&7", LegacyFormatStyleUtil::isValidLegacyFormatString);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static ModConfigSpec.ConfigValue<List<? extends String>> defineDisplayLanguage() {
        var builder = BUILDER
                .comment("List of extra language codes to show in item tooltips (e.g. [\"en_us\", \"zh_cn\"]).",
                        "Each language adds one extra name line to the tooltip.")
                .translation(translationKey("displayLanguage"));
        //? if <=1.20.1 {
        /*return builder.defineListAllowEmpty("displayLanguage", List.of("en_us"), String.class::isInstance);
        *///?} else {
        return builder.defineListAllowEmpty("displayLanguage", List.of("en_us"), () -> "en_us", String.class::isInstance);
        //?}
    }

    private static ModConfigSpec.BooleanValue defineChineseScriptMatching() {
        var builder = BUILDER
                .comment("If true, search treats simplified and traditional Chinese as interchangeable.",
                        "Disabling this turns off PolyglotTooltip's script-variant expansion for JEI, AE2, RS2, and related integrations.")
                .translation(translationKey("enableChineseScriptMatching"));
        //? if <=1.20.1 {
        /*return builder.define("enableChineseScriptMatching", true);
        *///?} else {
        return builder.define("enableChineseScriptMatching", false);
        //?}
    }
}
