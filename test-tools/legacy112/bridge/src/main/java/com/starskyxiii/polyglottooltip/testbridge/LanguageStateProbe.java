package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Loader;

final class LanguageStateProbe {
    private static final String[] KEYS = {"tile.stone.stone.name", "item.paper.name", "fluid.water"};
    private static final Class<?> IMMUTABLE_MAP = Collections.unmodifiableMap(new LinkedHashMap<>()).getClass();
    private static boolean active;

    private LanguageStateProbe() { }

    static boolean active() { return active; }

    static JsonObject enable() throws Exception {
        Minecraft client = Minecraft.func_71410_x();
        if (client.field_71439_g == null || client.field_71441_e == null)
            throw new IllegalStateException("Language evidence requires an initialized world");
        active = true;
        return capture();
    }

    static JsonObject capture() throws Exception {
        if (!active) throw new IllegalStateException("Language evidence is not enabled");
        Minecraft client = Minecraft.func_71410_x();
        Object manager = client.func_135016_M();
        JsonObject result = new JsonObject();
        result.addProperty("selectedLanguage", (String) NativeAccess.get(manager, "field_135048_c"));
        Object locale = NativeAccess.get(manager, "field_135049_a");
        result.addProperty("localeIdentity", System.identityHashCode(locale));
        result.add("client", entries(NativeAccess.get(locale, "field_135032_a"), false));
        ClassLoader loader = client.getClass().getClassLoader();
        Class<?> legacy = Class.forName("net.minecraft.util.text.translation.I18n", false, loader);
        Object languageMap = NativeAccess.get(legacy, "field_74839_a");
        result.addProperty("legacyIdentity", System.identityHashCode(languageMap));
        result.add("legacy", entries(NativeAccess.get(languageMap, "field_74816_c"), false));
        if (Loader.isModLoaded("polyglottooltip")) {
            Class<?> adapter = Class.forName("com.starskyxiii.polyglottooltip.legacy.minecraft.MinecraftLanguageAdapter", false, loader);
            Object snapshot = NativeAccess.get(adapter, "current");
            result.addProperty("snapshotIdentity", System.identityHashCode(snapshot));
            result.addProperty("generation", (Number) NativeAccess.get(snapshot, "generation"));
            Object primary = NativeAccess.get(snapshot, "primaryTable");
            if (primary != null) result.add("primary", table(primary));
            try {
                Object legacyPrimary = NativeAccess.get(snapshot, "legacyPrimaryTable");
                if (legacyPrimary != null) result.add("legacyPrimary", table(legacyPrimary));
                result.addProperty("settingsRequest", (Number) NativeAccess.get(snapshot, "settingsRequest"));
                for (String name : new String[]{"NativeLanguageHooks", "NativeLanguageAdmission", "NativeLookupAdmission"}) {
                    Class<?> source = Class.forName("com.starskyxiii.polyglottooltip.legacy.bootstrap." + name, false, loader);
                    JsonObject values = new JsonObject();
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) source.getMethod("diagnostics").invoke(null)).entrySet())
                        values.addProperty((String) entry.getKey(), (String) entry.getValue());
                    result.add(name, values);
                }
            } catch (NoSuchFieldException absent) { }
            JsonArray secondary = new JsonArray();
            for (Object table : (Iterable<?>) NativeAccess.get(snapshot, "tables")) secondary.add(table(table));
            result.add("secondary", secondary);
        }
        return result;
    }

    static JsonObject reentry() throws Exception {
        if (!Loader.isModLoaded("polyglottooltip")) throw new IllegalStateException("Product required");
        Class<?> adapter = Class.forName("com.starskyxiii.polyglottooltip.legacy.minecraft.MinecraftLanguageAdapter", false,
                Minecraft.func_71410_x().getClass().getClassLoader());
        Object before = NativeAccess.call(adapter, "snapshot");
        if (!(Boolean) NativeAccess.call(before, "ready")) throw new IllegalStateException("Applied language required");
        Object value = NativeAccess.get(adapter, "listeners");
        if (value.getClass() != java.util.concurrent.CopyOnWriteArrayList.class) throw new IllegalStateException("Listener registry");
        java.util.concurrent.CopyOnWriteArrayList<?> listeners = (java.util.concurrent.CopyOnWriteArrayList<?>) value;
        java.util.concurrent.atomic.AtomicBoolean armed = new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        JsonArray first = new JsonArray(), second = new JsonArray();
        Runnable requesting = () -> {
            try {
                Object current = NativeAccess.call(adapter, "snapshot");
                first.add((Number) NativeAccess.get(current, "generation"));
                if (armed.getAndSet(false)) NativeAccess.call(adapter, "requestReload");
            } catch (Throwable error) { failure.set(error); }
        };
        Runnable observing = () -> {
            try { second.add((Number) NativeAccess.get(NativeAccess.call(adapter, "snapshot"), "generation")); }
            catch (Throwable error) { failure.set(error); }
        };
        JsonObject result = new JsonObject();
        result.addProperty("beforeGeneration", (Number) NativeAccess.get(before, "generation"));
        result.addProperty("beforeRequest", (Number) NativeAccess.get(before, "settingsRequest"));
        try {
            NativeAccess.call(adapter, "onReload", requesting);
            NativeAccess.call(adapter, "onReload", observing);
            NativeAccess.call(adapter, "requestReload");
            if (failure.get() != null) throw new IllegalStateException("Reentry callback failed", failure.get());
            Object after = NativeAccess.call(adapter, "snapshot");
            result.addProperty("afterGeneration", (Number) NativeAccess.get(after, "generation"));
            result.addProperty("afterRequest", (Number) NativeAccess.get(after, "settingsRequest"));
            result.addProperty("ready", (Boolean) NativeAccess.call(after, "ready"));
            result.addProperty("sameNativeState", NativeAccess.get(before, "nativeState") == NativeAccess.get(after, "nativeState"));
            result.addProperty("sameSettings", NativeAccess.get(before, "settings") == NativeAccess.get(after, "settings"));
            result.addProperty("sameNativeNameEpoch", NativeAccess.get(before, "nativeNameEpoch").equals(NativeAccess.get(after, "nativeNameEpoch")));
            result.add("requestingListenerGenerations", first);
            result.add("followingListenerGenerations", second);
        } finally {
            listeners.remove(requesting);
            listeners.remove(observing);
        }
        return result;
    }

    private static JsonObject table(Object table) throws Exception {
        if (!table.getClass().getName().equals("com.starskyxiii.polyglottooltip.legacy.minecraft.LanguageTable"))
            throw new IllegalStateException("Unknown product language table");
        JsonObject result = entries(NativeAccess.get(table, "entries"), true);
        result.addProperty("code", (String) NativeAccess.get(table, "code"));
        return result;
    }

    static JsonObject entries(Object value, boolean product) {
        JsonObject result = new JsonObject();
        if (value == null) { result.addProperty("observed", false); return result; }
        Class<?> type = value.getClass();
        result.addProperty("class", type.getName());
        result.addProperty("identity", System.identityHashCode(value));
        boolean known = type == HashMap.class || type == LinkedHashMap.class || product && type == IMMUTABLE_MAP;
        result.addProperty("observed", known);
        if (!known) return result;
        Map<?, ?> map = (Map<?, ?>) value;
        result.addProperty("size", map.size());
        JsonObject keys = new JsonObject();
        for (String key : KEYS) {
            boolean present = false;
            Object selected = null;
            if (type == LinkedHashMap.class) {
                for (Map.Entry<?, ?> entry : map.entrySet()) if (key.equals(entry.getKey())) {
                    present = true;
                    selected = entry.getValue();
                    break;
                }
            } else {
                present = map.containsKey(key);
                selected = map.get(key);
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("present", present);
            entry.addProperty("string", selected instanceof String);
            if (selected instanceof String) entry.addProperty("value", (String) selected);
            keys.add(key, entry);
        }
        result.add("keys", keys);
        return result;
    }
}
