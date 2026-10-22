package com.starskyxiii.polyglottooltip.testbridge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class NameTrace {
    private static final Set<String> installed = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final ThreadLocal<Map<String, Integer>> current = new ThreadLocal<>();
    private NameTrace() { }
    public static void install(String name) { installed.add(name); }
    public static Set<String> installed() { return new java.util.TreeSet<>(installed); }
    public static void begin() { if (current.get() != null) throw new IllegalStateException("Trace already active"); current.set(new LinkedHashMap<>()); }
    public static void record(String name) {
        Map<String, Integer> values = current.get();
        if (values != null) values.put(name, values.getOrDefault(name, 0) + 1);
    }
    public static Map<String, Integer> end() { Map<String, Integer> values = current.get(); current.remove(); return values; }
}
