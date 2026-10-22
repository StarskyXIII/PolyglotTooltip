package com.starskyxiii.polyglottooltip.legacy.minecraft;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.starskyxiii.polyglottooltip.legacy.Config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.Language;

public final class SecondaryLanguages {
    private static volatile Map<String, SecondaryDictionary> dictionaries = Collections.emptyMap();

    private SecondaryLanguages() {}

    public static void load() {
        try {
            IResourceManager manager = Minecraft.func_71410_x().func_110442_L();
            Map<String, SecondaryDictionary> loaded = new LinkedHashMap<>();
            for (String code : Config.settings().displayLanguages()) {
                loaded.put(code, SecondaryDictionary.load(manager, code));
            }
            dictionaries = Collections.unmodifiableMap(loaded);
        } catch (RuntimeException error) {
            FailureLog.warn("load|" + error.getClass().getName(), "Could not build secondary dictionaries", error);
        }
    }

    public static Map<String, SecondaryDictionary> dictionaries() {
        return dictionaries;
    }

    public static int generation() {
        return 0;
    }

    public static String currentLanguage() {
        Minecraft minecraft = Minecraft.func_71410_x();
        Language language = minecraft.func_135016_M() == null ? null : minecraft.func_135016_M().func_135041_c();
        String code = language == null ? minecraft.field_71474_y.field_74363_ab : language.func_135034_a();
        return code == null ? "" : code.toLowerCase(Locale.ROOT);
    }
}
