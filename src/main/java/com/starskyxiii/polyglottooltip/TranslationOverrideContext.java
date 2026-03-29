package com.starskyxiii.polyglottooltip;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

public final class TranslationOverrideContext {

    private static final ThreadLocal<Deque<Map<String, String>>> TRANSLATIONS =
        new ThreadLocal<Deque<Map<String, String>>>();

    private TranslationOverrideContext() {}

    public static void push(Map<String, String> translations) {
        if (translations == null || translations.isEmpty()) {
            return;
        }

        Deque<Map<String, String>> stack = TRANSLATIONS.get();
        if (stack == null) {
            stack = new ArrayDeque<Map<String, String>>();
            TRANSLATIONS.set(stack);
        }

        stack.addLast(translations);
    }

    public static void pop() {
        Deque<Map<String, String>> stack = TRANSLATIONS.get();
        if (stack == null || stack.isEmpty()) {
            TRANSLATIONS.remove();
            return;
        }

        stack.removeLast();
        if (stack.isEmpty()) {
            TRANSLATIONS.remove();
        }
    }

    public static String translate(String key) {
        Map<String, String> translations = peek();
        if (translations == null || key == null || key.isEmpty()) {
            return null;
        }

        return translations.get(key);
    }

    public static String format(String key, Object... args) {
        String translated = translate(key);
        if (translated == null) {
            return null;
        }

        if (args == null || args.length == 0) {
            return translated;
        }

        try {
            return String.format(translated, args);
        } catch (Exception ignored) {
            return translated;
        }
    }

    public static boolean contains(String key) {
        Map<String, String> translations = peek();
        return translations != null && key != null && translations.containsKey(key);
    }

    private static Map<String, String> peek() {
        Deque<Map<String, String>> stack = TRANSLATIONS.get();
        return stack == null || stack.isEmpty() ? null : stack.peekLast();
    }
}
