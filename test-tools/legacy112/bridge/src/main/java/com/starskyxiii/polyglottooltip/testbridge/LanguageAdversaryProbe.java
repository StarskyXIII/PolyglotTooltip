package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.Language;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class LanguageAdversaryProbe {
    private static final String BASE = "com.starskyxiii.polyglottooltip.legacy.";
    private static LanguageAdversaryProbe pending;
    private final Class<?> adapter;
    private final List<Runnable> nativeListeners, ordinaryListeners;
    private final List<Runnable> addedNative = new ArrayList<>(), addedOrdinary = new ArrayList<>();
    private final JsonArray events = new JsonArray();
    private final Language selected;
    private final java.util.IdentityHashMap<Object, Integer> identities = new java.util.IdentityHashMap<>();
    private JsonObject result;
    private CompletableFuture<JsonElement> future;
    private int naturalStarts;
    private boolean active = true;
    private boolean cleaned;
    private Throwable instrumentationFailure;
    private AtomicBoolean persistentRequests;
    private int requestingCalls, callsAtStart;
    private java.nio.file.Path configFile;
    private byte[] configBytes;

    @SuppressWarnings("unchecked")
    private LanguageAdversaryProbe() throws Exception {
        if (!LanguageStateProbe.active() || !Loader.isModLoaded("polyglottooltip")
                || System.getProperty("clientdevbridge.fixtureNonce", "").isEmpty())
            throw new IllegalStateException("Owned initialized product fixture required");
        adapter = NativeAccess.type(BASE + "minecraft.MinecraftLanguageAdapter");
        nativeListeners = (List<Runnable>) NativeAccess.get(adapter, "nativeInvalidators");
        ordinaryListeners = (List<Runnable>) NativeAccess.get(adapter, "listeners");
        if (nativeListeners.getClass() != java.util.concurrent.CopyOnWriteArrayList.class
                || ordinaryListeners.getClass() != java.util.concurrent.CopyOnWriteArrayList.class)
            throw new IllegalStateException("Unexpected listener registry");
        selected = Minecraft.func_71410_x().func_135016_M().func_135041_c();
        if (!ready()) throw new IllegalStateException("Ready native language required");
    }

    static void request(JsonObject params, CompletableFuture<JsonElement> future) throws Exception {
        if (pending != null) throw new IllegalStateException("Another language adversary is pending");
        LanguageAdversaryProbe probe = new LanguageAdversaryProbe();
        JsonObject result = new JsonObject();
        probe.result = result;
        probe.future = future;
        result.add("before", LanguageStateProbe.capture());
        try {
            switch (params.get("action").getAsString()) {
                case "native-request": probe.nativeRequest(result, false); break;
                case "native-epoch": probe.nativeRequest(result, true); break;
                case "native-gate": probe.gate(result); break;
                case "listener-failures": probe.failures(); break;
                case "general-settings": probe.generalSettings(); break;
                case "nonconvergent": probe.nonconvergent(result); break;
                case "nonconvergent-next": probe.persistentRequests = new AtomicBoolean(true); probe.nonconvergent(result); break;
                case "context": probe.context(result); break;
                default: throw new IllegalArgumentException("Unknown language adversary");
            }
            result.add("events", probe.events);
            result.add("after", LanguageStateProbe.capture());
            result.add("state", probe.state());
            result.addProperty("inlineEventCount", probe.events.size());
            probe.checkInstrumentation();
            pending = probe;
            MinecraftForge.EVENT_BUS.register(probe);
        } catch (Throwable error) {
            probe.cleanup();
            throw error;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void naturalStart(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || pending != this) return;
        if (future.isDone()) { cleanup(); return; }
        try {
            naturalStarts++;
            callsAtStart = requestingCalls;
            record("natural-start");
        } catch (Throwable error) {
            cleanup();
            future.completeExceptionally(error);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void naturalEnd(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending != this || naturalStarts == 0) return;
        try {
            record("natural-end");
            if (persistentRequests != null && persistentRequests.getAndSet(false)) {
                result.add("nextRefreshPending", state());
                result.addProperty("nextRefreshCallbacks", requestingCalls - callsAtStart);
                ordinaryListeners.removeAll(addedOrdinary);
                return;
            }
            result.add("recoveredState", state());
            checkInstrumentation();
            cleanup();
            checkInstrumentation();
            result.add("restored", LanguageStateProbe.capture());
            future.complete(result);
        } catch (Throwable error) {
            cleanup();
            future.completeExceptionally(error);
        }
    }

    private void cleanup() {
        if (cleaned) return;
        cleaned = true;
        active = false;
        if (persistentRequests != null) persistentRequests.set(false);
        try { nativeListeners.removeAll(addedNative); }
        catch (Throwable error) { failedInstrumentation(error); }
        try { ordinaryListeners.removeAll(addedOrdinary); }
        catch (Throwable error) { failedInstrumentation(error); }
        try { Minecraft.func_71410_x().func_135016_M().func_135045_a(selected); }
        catch (Throwable error) { failedInstrumentation(error); }
        if (configBytes != null) {
            try {
                java.nio.file.Files.write(configFile, configBytes);
                NativeAccess.call(NativeAccess.type(BASE + "Config"), "reload");
                if (!java.util.Arrays.equals(configBytes, java.nio.file.Files.readAllBytes(configFile)))
                    throw new IllegalStateException("Configuration cleanup changed original bytes");
            } catch (Throwable error) { failedInstrumentation(error); }
        }
        try { MinecraftForge.EVENT_BUS.unregister(this); }
        catch (Throwable error) { failedInstrumentation(error); }
        if (pending == this) pending = null;
        identities.clear();
    }

    private void nativeRequest(JsonObject result, boolean epoch) throws Exception {
        AtomicBoolean once = new AtomicBoolean(true);
        add(true, () -> {
            record("native-A");
            if (once.getAndSet(false)) {
                if (epoch) apply();
                else invoke("requestReload");
            }
        });
        add(true, () -> record("native-B"));
        add(false, () -> record("ordinary"));
        apply();
        result.addProperty("ready", ready());
    }

    private void gate(JsonObject result) throws Exception {
        Minecraft client = Minecraft.func_71410_x();
        Language alternate = client.func_135016_M().func_191960_a(selected.func_135034_a().equals("en_us") ? "zh_cn" : "en_us");
        if (alternate == null) throw new IllegalStateException("Alternate language missing");
        AtomicBoolean once = new AtomicBoolean(true);
        add(true, () -> {
            record("native-A");
            if (once.getAndSet(false)) {
                client.func_135016_M().func_135045_a(alternate);
            }
        });
        add(true, () -> record("native-B"));
        add(false, () -> record("ordinary"));
        apply();
        Object paused = NativeAccess.call(adapter, "snapshot");
        result.add("paused", state());
        result.addProperty("pausedEventCount", events.size());
        client.func_135016_M().func_135045_a(selected);
        result.addProperty("sameSnapshotAfterGate", paused == NativeAccess.call(adapter, "snapshot"));
        result.addProperty("readyAfterGate", ready());
        result.add("gateRestored", state());
    }

    private void failures() {
        for (boolean nativeCallback : new boolean[]{true, false}) {
            String prefix = nativeCallback ? "native-" : "ordinary-";
            RuntimeException runtime = new IllegalStateException("fixture callback runtime");
            LinkageError linkage = new LinkageError("fixture callback linkage");
            result.addProperty(prefix + "runtimeIdentity", identity(runtime));
            result.addProperty(prefix + "linkageIdentity", identity(linkage));
            add(nativeCallback, () -> { record(prefix + "runtime"); result.addProperty(prefix + "thrownRuntimeIdentity", identity(runtime)); throw runtime; });
            add(nativeCallback, () -> { record(prefix + "linkage"); result.addProperty(prefix + "thrownLinkageIdentity", identity(linkage)); throw linkage; });
            add(nativeCallback, () -> record(prefix + "following"));
        }
        apply();
    }

    private void nonconvergent(JsonObject result) throws Exception {
        add(false, () -> {
            if (persistentRequests != null && !persistentRequests.get()) return;
            record("requesting");
            if (++requestingCalls >= (persistentRequests == null ? 24 : 64)) { result.addProperty("safetyTrip", true); return; }
            invoke("requestReload");
        });
        result.addProperty("safetyTrip", false);
        NativeAccess.call(adapter, "requestReload");
        result.addProperty("readyAtReturn", ready());
        result.addProperty("callbackCountAtReturn", events.size());
        result.add("pending", state());
        if (persistentRequests == null) ordinaryListeners.removeAll(addedOrdinary);
    }

    private void generalSettings() throws Exception {
        Class<?> config = NativeAccess.type(BASE + "Config");
        net.minecraftforge.common.config.Configuration configuration =
                (net.minecraftforge.common.config.Configuration) NativeAccess.call(config, "configuration");
        configFile = configuration.getConfigFile().toPath().toRealPath();
        if (!configFile.startsWith(Minecraft.func_71410_x().field_71412_D.toPath().toRealPath()))
            throw new IllegalStateException("Configuration is outside the owned fixture");
        configBytes = java.nio.file.Files.readAllBytes(configFile);
        String oldFormat = (String) NativeAccess.get(NativeAccess.call(config, "get"), "tooltipFormat");
        JsonObject update = new JsonObject();
        update.addProperty("tooltipSecondaryNameFormat", oldFormat.equals("\u00a73") ? "&4" : "&3");
        AtomicBoolean once = new AtomicBoolean(true);
        add(true, () -> record("native"));
        add(false, () -> {
            record("ordinary-A");
            if (!once.getAndSet(false)) return;
            try { CoreProbe.configure(update); }
            catch (Exception error) { failedInstrumentation(error); throw new IllegalStateException(error); }
        });
        add(false, () -> record("ordinary-B"));
        NativeAccess.call(adapter, "requestReload");
    }

    private void context(JsonObject result) throws Exception {
        Object original = NativeAccess.call(adapter, "snapshot");
        NativeAccess.call(adapter, "requestReload");
        Object latest = NativeAccess.call(adapter, "snapshot");
        if (original == latest) throw new IllegalStateException("Distinct snapshots required");
        Object captured = NativeAccess.get(adapter, "captured");
        if (threadValue(captured) != null) throw new IllegalStateException("Existing retained context");
        NativeAccess.call(adapter, "withContext", latest, (Supplier<Object>) () -> null);
        result.addProperty("entryAbsentBefore", !threadHas(captured));
        result.addProperty("oldSnapshotReady", (Boolean) NativeAccess.call(original, "ready"));
        RuntimeException marker = new IllegalStateException("fixture context marker");
        Supplier<Object> outer = () -> {
            result.addProperty("outerIsOriginal", invoke("context") == original);
            try {
                result.addProperty("innerSuccessIsLatest", NativeAccess.call(adapter, "withContext", latest,
                        (Supplier<Object>) () -> invoke("context")) == latest);
                result.addProperty("outerRestoredAfterSuccess", invoke("context") == original);
                NativeAccess.call(adapter, "withContext", latest, (Supplier<Object>) () -> {
                    result.addProperty("innerIsLatest", invoke("context") == latest);
                    throw marker;
                });
            } catch (InvocationTargetException error) {
                result.addProperty("sameThrowable", error.getCause() == marker);
            } catch (Exception error) { throw new IllegalStateException(error); }
            result.addProperty("outerRestored", invoke("context") == original);
            return null;
        };
        NativeAccess.call(adapter, "withContext", original, outer);
        result.addProperty("entryAbsentAfter", !threadHas(captured));
        result.addProperty("afterIsLatest", NativeAccess.call(adapter, "context") == latest);
        NativeAccess.call(adapter, "withContext", latest, (Supplier<Object>) () -> null);
        try {
            NativeAccess.call(adapter, "withContext", original, (Supplier<Object>) () -> { throw marker; });
        } catch (InvocationTargetException error) { result.addProperty("sameOuterThrowable", error.getCause() == marker); }
        result.addProperty("entryAbsentAfterOuterThrow", !threadHas(captured));
        NativeAccess.call(adapter, "withContext", original, (Supplier<Object>) () -> { invoke("requestReload"); return null; });
        Object newest = NativeAccess.call(adapter, "snapshot");
        result.addProperty("changedWithinContext", newest != latest);
        result.addProperty("entryAbsentAfterChange", !threadHas(captured));
        result.addProperty("afterChangeIsNewest", NativeAccess.call(adapter, "context") == newest);
        NativeAccess.call(adapter, "withContext", newest, (Supplier<Object>) () -> null);
    }

    private static Object threadValue(Object key) throws Exception {
        Object entry = threadEntry(key);
        return entry == null ? null : NativeAccess.get(entry, "value");
    }

    private static boolean threadHas(Object key) throws Exception { return threadEntry(key) != null; }

    private static Object threadEntry(Object key) throws Exception {
        Object map = NativeAccess.get(Thread.currentThread(), "threadLocals");
        if (map == null) return null;
        if (!map.getClass().getName().equals("java.lang.ThreadLocal$ThreadLocalMap"))
            throw new IllegalStateException("Unexpected thread local map");
        for (Object entry : (Object[]) NativeAccess.get(map, "table")) {
            if (entry == null) continue;
            if (!entry.getClass().getName().equals("java.lang.ThreadLocal$ThreadLocalMap$Entry"))
                throw new IllegalStateException("Unexpected thread local entry");
            if (((java.lang.ref.Reference<?>) entry).get() == key) return entry;
        }
        return null;
    }

    private void add(boolean nativeCallback, Runnable callback) {
        Runnable guarded = () -> { if (active) callback.run(); };
        (nativeCallback ? nativeListeners : ordinaryListeners).add(guarded);
        (nativeCallback ? addedNative : addedOrdinary).add(guarded);
    }

    private static void apply() {
        Minecraft client = Minecraft.func_71410_x();
        client.func_135016_M().func_110549_a(client.func_110442_L());
    }

    private boolean ready() throws Exception { return (Boolean) NativeAccess.call(NativeAccess.call(adapter, "snapshot"), "ready"); }

    private JsonObject state() throws Exception {
        Object snapshot = NativeAccess.call(adapter, "snapshot");
        JsonObject result = new JsonObject();
        result.addProperty("snapshotIdentity", identity(snapshot));
        for (String key : new String[]{"generation", "nativeNameEpoch", "settingsRequest"})
            result.addProperty(key, (Number) NativeAccess.get(snapshot, key));
        result.addProperty("ready", (Boolean) NativeAccess.call(snapshot, "ready"));
        result.addProperty("requests", ((java.util.concurrent.atomic.AtomicLong) NativeAccess.get(adapter, "requests")).get());
        Object cursor = NativeAccess.get(adapter, "nativeNotifications");
        result.addProperty("cursorEpoch", (Number) NativeAccess.get(cursor, "epoch"));
        result.addProperty("cursorNext", (Number) NativeAccess.get(cursor, "next"));
        result.addProperty("cursorPending", ((List<?>) NativeAccess.get(cursor, "pending")).size());
        result.addProperty("cursorDraining", (Boolean) NativeAccess.get(cursor, "draining"));
        result.addProperty("notifiedSnapshotIdentity", identity(NativeAccess.get(adapter, "notifiedSnapshot")));
        result.addProperty("preparing", (Boolean) NativeAccess.get(adapter, "preparing"));
        result.addProperty("nativeStateIdentity", identity(NativeAccess.get(snapshot, "nativeState")));
        result.addProperty("settingsIdentity", identity(NativeAccess.get(snapshot, "settings")));
        Object source = NativeAccess.call(NativeAccess.type(BASE + "bootstrap.NativeLanguageHooks"), "snapshot");
        result.addProperty("actualNativeStateIdentity", identity(source));
        result.addProperty("actualSettingsIdentity", identity(NativeAccess.call(NativeAccess.type(BASE + "Config"), "get")));
        result.addProperty("bindingsCurrent", (Boolean) NativeAccess.call(
                NativeAccess.type(BASE + "bootstrap.NativeLanguageHooks"), "bindingsCurrent", NativeAccess.get(source, "stamp")));
        result.addProperty("admissionReady", (Boolean) NativeAccess.call(NativeAccess.type(BASE + "bootstrap.NativeLanguageAdmission"), "ready"));
        result.addProperty("naturalStarts", naturalStarts);
        return result;
    }

    private int identity(Object value) {
        if (value == null) return 0;
        Integer id = identities.get(value);
        if (id == null) { id = identities.size() + 1; identities.put(value, id); }
        return id;
    }

    private void record(String event) {
        if (!active) return;
        try {
            JsonObject value = state();
            value.addProperty("event", event);
            events.add(value);
        } catch (Exception | LinkageError error) {
            failedInstrumentation(error);
            throw new IllegalStateException(error);
        }
    }

    private void checkInstrumentation() {
        if (instrumentationFailure != null) throw new IllegalStateException("Scenario instrumentation failed", instrumentationFailure);
    }

    private void failedInstrumentation(Throwable error) {
        if (instrumentationFailure == null) instrumentationFailure = error;
        else if (instrumentationFailure != error) instrumentationFailure.addSuppressed(error);
    }

    private Object invoke(String name) {
        try { return NativeAccess.call(adapter, name); }
        catch (Exception error) { failedInstrumentation(error); throw new IllegalStateException(error); }
    }
}
