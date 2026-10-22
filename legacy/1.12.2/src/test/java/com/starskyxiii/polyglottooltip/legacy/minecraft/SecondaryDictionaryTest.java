package com.starskyxiii.polyglottooltip.legacy.minecraft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class SecondaryDictionaryTest {
    private static Map<String, String> parse(String text) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        SecondaryDictionary.parse(new StringReader(text), entries::put);
        return entries;
    }

    @Test
    public void skipsBlankAndCommentLines() throws IOException {
        Map<String, String> entries = parse("# comment\n\nitem.a.name=A\n  # not a comment=x\nno separator\n");
        assertEquals(2, entries.size());
        assertEquals("A", entries.get("item.a.name"));
        assertEquals("x", entries.get("  # not a comment"));
    }

    @Test
    public void splitsOnFirstEquals() throws IOException {
        Map<String, String> entries = parse("key=a=b\nempty=\n=noKey\n");
        assertEquals("a=b", entries.get("key"));
        assertEquals("", entries.get("empty"));
        assertEquals("noKey", entries.get(""));
    }

    @Test
    public void convertsNumericFormatsToStrings() throws IOException {
        Map<String, String> entries = parse("a=%d items\nb=%1$d of %2$d\nc=%.2f%%\nd=%5d and %s\n");
        assertEquals("%s items", entries.get("a"));
        assertEquals("%1$s of %2$s", entries.get("b"));
        assertEquals("%s%%", entries.get("c"));
        assertEquals("%s and %s", entries.get("d"));
    }

    @Test
    public void ownLanguageOverridesEnglishFallback() {
        Map<String, List<String>> files = new HashMap<>();
        files.put("en_us", list("a=English A\nb=English B\n", "b=Pack B\n"));
        files.put("zh_tw", list("a=中文 A\n", "a=資源包 A\nc=中文 C\n"));
        List<String> order = new ArrayList<>();
        SecondaryDictionary dictionary = SecondaryDictionary.load("zh_tw", source(files, order));

        assertEquals(Arrays.asList("en_us", "zh_tw"), order);
        assertEquals("資源包 A", dictionary.get("a"));
        assertEquals("Pack B", dictionary.get("b"));
        assertEquals("中文 C", dictionary.get("c"));
        assertTrue(dictionary.hasOwnKey("a"));
        assertFalse(dictionary.hasOwnKey("b"));
        assertEquals(2, dictionary.ownKeys().size());
        assertNull(dictionary.get("missing"));
    }

    @Test
    public void englishReadsOnlyItsOwnFiles() {
        Map<String, List<String>> files = new HashMap<>();
        files.put("en_us", list("a=A\n"));
        List<String> order = new ArrayList<>();
        SecondaryDictionary dictionary = SecondaryDictionary.load("en_us", source(files, order));

        assertEquals(Collections.singletonList("en_us"), order);
        assertTrue(dictionary.hasOwnKey("a"));
        assertEquals(1, dictionary.size());
    }

    private static List<String> list(String... texts) {
        return Arrays.asList(texts);
    }

    private static SecondaryDictionary.LangSource source(Map<String, List<String>> files, List<String> order) {
        return (language, sink) -> {
            order.add(language);
            for (String text : files.getOrDefault(language, Collections.<String>emptyList())) {
                try {
                    SecondaryDictionary.parse(new StringReader(text), sink);
                } catch (IOException error) {
                    throw new UncheckedIOException(error);
                }
            }
        };
    }
}
