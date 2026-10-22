package com.starskyxiii.polyglottooltip.legacy.integration.generic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;

import com.starskyxiii.polyglottooltip.legacy.PolyglotTooltipMod;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryDictionary;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryLanguages;

import net.minecraft.client.Minecraft;

public final class ReverseIndex {
    private static final Pattern STYLE = Pattern.compile("(?i)§[0-9a-fk-or]");
    private static final Comparator<String> KEY_ORDER = Comparator
            .comparing((String key) -> !key.endsWith(".name"))
            .thenComparing(Comparator.naturalOrder());
    private static ReverseIndex current;

    private final int generation;
    private final Map<String, String> keys;
    private final int ambiguous;

    ReverseIndex(Iterable<String> keys, Function<String, String> values) {
        this(-1, keys, values);
    }

    private ReverseIndex(int generation, Iterable<String> keys, Function<String, String> values) {
        List<String> ordered = new ArrayList<>();
        keys.forEach(ordered::add);
        ordered.sort(KEY_ORDER);
        Map<String, String> index = new HashMap<>();
        int duplicates = 0;
        for (String key : ordered) {
            String value = normalize(values.apply(key));
            if (value.isEmpty() || value.indexOf('%') >= 0) {
                continue;
            }
            if (index.putIfAbsent(value, key) != null) {
                duplicates++;
            }
        }
        this.generation = generation;
        this.keys = index;
        this.ambiguous = duplicates;
    }

    public static ReverseIndex of(SecondaryDictionary dictionary) {
        return new ReverseIndex(dictionary.keys(), dictionary::get);
    }

    public static synchronized ReverseIndex current() {
        int generation = SecondaryLanguages.generation();
        if (current == null || current.generation != generation) {
            SecondaryDictionary primary = primaryDictionary();
            current = new ReverseIndex(generation, primary.keys(), primary::get);
            if (current.ambiguous > 0) {
                LogManager.getLogger(PolyglotTooltipMod.MOD_ID).debug(
                        "{} {} values map to more than one key; the first key in order is used",
                        primary.languageCode(), current.ambiguous);
            }
        }
        return current;
    }

    public static synchronized void invalidate() {
        current = null;
    }

    private static SecondaryDictionary primaryDictionary() {
        String language = SecondaryLanguages.currentLanguage();
        SecondaryDictionary loaded = SecondaryLanguages.dictionaries().get(language);
        return loaded != null ? loaded
                : SecondaryDictionary.load(Minecraft.func_71410_x().func_110442_L(), language);
    }

    public static String normalize(String text) {
        return text == null ? "" : STYLE.matcher(text).replaceAll("").trim();
    }

    public String keyOf(String primaryText) {
        return keys.get(normalize(primaryText));
    }

    public String translate(String primaryText, SecondaryDictionary dictionary) {
        String key = keyOf(primaryText);
        return key == null ? null : dictionary.get(key);
    }

    public int ambiguous() {
        return ambiguous;
    }
}
