package com.starskyxiii.polyglottooltip.legacy.minecraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import com.starskyxiii.polyglottooltip.core.LanguageCodePolicy;

import net.minecraft.client.resources.IResourceManager;

public final class SecondaryDictionary {
    private static final Pattern NUMERIC_FORMAT = Pattern.compile("%(\\d+\\$)?[\\d\\.]*[df]");

    public interface LangSource {
        void read(String languageCode, BiConsumer<String, String> sink);
    }

    private final String languageCode;
    private final Map<String, String> entries;
    private final Set<String> ownKeys;

    private SecondaryDictionary(String languageCode, Map<String, String> entries, Set<String> ownKeys) {
        this.languageCode = languageCode;
        this.entries = Collections.unmodifiableMap(entries);
        this.ownKeys = Collections.unmodifiableSet(ownKeys);
    }

    public static SecondaryDictionary load(IResourceManager manager, String languageCode) {
        return load(languageCode, new ResourceLangSource(manager));
    }

    public static SecondaryDictionary load(String languageCode, LangSource source) {
        Map<String, String> entries = new HashMap<>();
        Set<String> ownKeys = new HashSet<>();
        for (String language : LanguageCodePolicy.fallbackChain(languageCode)) {
            boolean own = language.equalsIgnoreCase(languageCode);
            source.read(language, (key, value) -> {
                entries.put(key, value);
                if (own) {
                    ownKeys.add(key);
                }
            });
        }
        return new SecondaryDictionary(languageCode, entries, ownKeys);
    }

    public static void parse(Reader reader, BiConsumer<String, String> sink) throws IOException {
        BufferedReader lines = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
        String line;
        while ((line = lines.readLine()) != null) {
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            int split = line.indexOf('=');
            if (split < 0) {
                continue;
            }
            String value = NUMERIC_FORMAT.matcher(line.substring(split + 1)).replaceAll("%$1s");
            sink.accept(line.substring(0, split), value);
        }
    }

    public String languageCode() {
        return languageCode;
    }

    public String get(String key) {
        return key == null ? null : entries.get(key);
    }

    public boolean hasOwnKey(String key) {
        return key != null && ownKeys.contains(key);
    }

    public Set<String> keys() {
        return entries.keySet();
    }

    public Set<String> ownKeys() {
        return ownKeys;
    }

    public int size() {
        return entries.size();
    }
}
