package com.starskyxiii.polyglottooltip;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final String CONFIG_TRANSLATION_PREFIX = "polyglottooltip.configuration.";

    private static String translationKey(String path) {
        return CONFIG_TRANSLATION_PREFIX + path;
    }

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISPLAY_LANGUAGE = BUILDER
            .comment("List of extra language codes to show in item tooltips (e.g. [\"en_us\", \"zh_cn\"]).",
                     "Each language adds one extra name line to the tooltip.")
            .translation(translationKey("displayLanguage"))
            .defineListAllowEmpty("displayLanguage", List.of("en_us"), String.class::isInstance);

    public static final ForgeConfigSpec.BooleanValue ALWAYS_SHOW = BUILDER
            .comment("If true, show the extra language even when it matches the current game language.")
            .translation(translationKey("alwaysShow"))
            .define("alwaysShow", false);

    public static final ForgeConfigSpec.BooleanValue ENABLE_CHINESE_SCRIPT_MATCHING = BUILDER
            .comment("If true, search treats simplified and traditional Chinese as interchangeable.",
                     "Disabling this turns off PolyglotTooltip's script-variant expansion for JEI, AE2, RS, and related integrations.")
            .translation(translationKey("enableChineseScriptMatching"))
            .define("enableChineseScriptMatching", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();
}
