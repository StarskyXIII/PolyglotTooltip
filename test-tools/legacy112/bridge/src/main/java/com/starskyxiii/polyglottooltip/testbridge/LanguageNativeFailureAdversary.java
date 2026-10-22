package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.Language;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraftforge.client.resource.SelectiveReloadStateHandler;
import net.minecraftforge.fml.common.ProgressManager;
import net.minecraftforge.fml.common.ProgressManager.ProgressBar;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

final class LanguageNativeFailureAdversary {
    private static final List<String> ACTIONS = Collections.unmodifiableList(Arrays.asList(
            "native-before-lm-runtime", "native-before-lm-linkage", "native-before-lm-both-runtime",
            "native-later-both-runtime", "native-later-linkage", "legacy-entryset-runtime",
            "legacy-entryset-linkage", "legacy-second-next-runtime", "legacy-second-next-linkage"));
    private static final String CATCH_MESSAGE = "Caught error stitching, removing all assigned resourcepacks";
    private final LanguagePreparationAdversary.Suite suite;
    private final LanguagePreparationAdversary.Evidence evidence;
    private final String action;
    private final int sequence;
    private final Thread thread = Thread.currentThread();
    private final boolean legacy, later, secondNext, linkage, both;
    private final Throwable[] sentinels;
    private final JsonObject result = new JsonObject();
    private final JsonArray checks = new JsonArray(), events = new JsonArray(), publications = new JsonArray();
    private final IdentityHashMap<Object, Pass> passIndex = new IdentityHashMap<>();
    private final List<Pass> passes = new ArrayList<>();
    private final IdentityHashMap<Object, Boolean> languages = new IdentityHashMap<>(), locales = new IdentityHashMap<>();
    private final IdentityHashMap<Object, Boolean> ignoredFrames = new IdentityHashMap<>(), ignoredOwners = new IdentityHashMap<>();
    private final Runnable nativeListener = () -> publication("native-notification");
    private final Runnable ordinaryListener = () -> publication("general-notification");
    private final OwnedListener laterListener = new OwnedListener();
    private boolean recording, armed, passed = true, setupComplete, operationStarted;
    private boolean laterRegistrationAttempted;
    private int refreshKind, legacyKind, faults, domainsCalls, readStreams, setupCallbacks, lateCalls;
    private int mapSizes, mapEntrySets, mapIterators, mapHasNext, mapNext, mapReturned;
    private Object manager, languageManager, activeLocale, activeLegacy, clientMap, legacyMap;
    private Object retained, sourceBefore, refreshScope;
    private long legacyTimestamp;
    private JsonObject baselineMaps;
    private byte[] configBytes;
    private Language selected;
    private List<IResourceManagerReloadListener> listeners, originalListeners;
    private List<Runnable> originalProductNative, originalProductOrdinary;
    private Selection selection;
    private Progress progress;
    private CatchObserver logger;
    private Throwable thrown;
    private volatile Throwable instrumentationFailure;
    private String phase = "setup";

    private LanguageNativeFailureAdversary(LanguagePreparationAdversary.Suite suite, String action) {
        this.suite = suite;
        this.action = action;
        sequence = ++suite.sequence;
        evidence = new LanguagePreparationAdversary.Evidence(suite);
        legacy = action.startsWith("legacy-");
        later = action.startsWith("native-later-");
        secondNext = action.startsWith("legacy-second-next-");
        linkage = action.endsWith("linkage");
        both = action.contains("both-runtime");
        sentinels = new Throwable[both ? 2 : 1];
        for (int index = 0; index < sentinels.length; index++) sentinels[index] = linkage
                ? new LinkageError("owned native language failure " + sequence + ":" + index)
                : new IllegalStateException("owned native language failure " + sequence + ":" + index);
        result.addProperty("schema", 1);
        result.addProperty("action", action);
        result.addProperty("sequence", sequence);
        result.addProperty("fixtureNonce", suite.nonce);
        result.addProperty("gameDirectory", suite.directory);
        result.addProperty("javaVersion", System.getProperty("java.version"));
        result.addProperty("javaVendor", System.getProperty("java.vendor"));
        result.addProperty("javaVm", System.getProperty("java.vm.name"));
        result.addProperty("formalOperation", legacy ? "LanguageMap.replaceWith(ownedInput)" : "Minecraft.refreshResources()");
        result.addProperty("counterMeaning", "Only domains scopes count requested/fallback passes; streams witness scopes at first reads, not every native invocation. Setup, cleanup and recovery are excluded.");
        JsonArray plan = new JsonArray();
        for (int index = 0; index < sentinels.length; index++) {
            JsonObject item = error(sentinels[index]);
            item.addProperty("ordinal", index + 1);
            item.addProperty("role", legacy ? "direct" : index == 0 ? "requested" : "fallback");
            plan.add(item);
        }
        result.add("sentinelPlan", plan);
        if (secondNext) {
            result.addProperty("partialLegacyKey", legacyKey());
            result.addProperty("partialLegacyValue", legacyValue());
        }
    }

    static boolean supports(String action) { return ACTIONS.contains(action); }

    static JsonObject run(LanguagePreparationAdversary.Suite suite, String action) throws Exception {
        if (!supports(action)) throw new IllegalArgumentException("Unknown native failure action");
        suite.verifyOwnership();
        if (suite.active != null || suite.activeNative != null || suite.nativeCleanup != null)
            throw new IllegalStateException("Another fixture operation or cleanup is active");
        return new LanguageNativeFailureAdversary(suite, action).execute();
    }

    private JsonObject execute() {
        try {
            setup();
            phase = "operation";
            recording = true;
            armed = true;
            operationStarted = true;
            try {
                if (legacy) call(activeLegacy.getClass(), "func_135063_a", new FaultMap());
                else suite.client.func_110436_a();
                result.addProperty("nativeReturned", true);
            } catch (Throwable error) {
                thrown = unwrap(error);
                result.addProperty("nativeReturned", false);
                result.add("nativeThrown", error(thrown));
            } finally {
                recording = false;
                armed = false;
            }
            phase = "immediate";
            result.add("immediate", checkpoint());
            verifyImmediate();
        } catch (Throwable failure) {
            result.add(phase + "Failure", error(unwrap(failure)));
            passed = false;
        } finally {
            recording = false;
            armed = false;
            phase = "cleanup";
            JsonObject cleanup = cleanupOwned();
            result.add("cleanup", cleanup);
            boolean clean = cleanup.get("passed").getAsBoolean();
            if (!clean) {
                passed = false;
                suite.nativeCleanup = this;
            } else if (setupComplete && operationStarted) {
                phase = "recovery";
                try { recover(); }
                catch (Throwable failure) {
                    result.add("recoveryFailure", error(unwrap(failure)));
                    passed = false;
                    suite.nativeCleanup = this;
                }
            }
            suite.activeNative = null;
            phase = "complete";
        }
        if (instrumentationFailure != null) {
            result.add("instrumentFailure", error(instrumentationFailure));
            passed = false;
        }
        result.add("events", events);
        result.add("publications", publications);
        result.add("checks", checks);
        result.add("counts", counts());
        result.addProperty("harnessOperationCalls", operationStarted ? 1 : 0);
        result.addProperty("cleanupBlocked", suite.nativeCleanup != null);
        result.addProperty("passed", passed && suite.nativeCleanup == null);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void setup() throws Exception {
        suite.verifyOwnership();
        originalProductNative = new ArrayList<>(suite.nativeListeners);
        originalProductOrdinary = new ArrayList<>(suite.ordinaryListeners);
        require("installed pack present exactly once", suite.packAdded && occurrences(suite.packs, suite.pack) == 1);
        require("baseline product ready", suite.ready());
        require("native boundaries admitted", (Boolean) call(suite.admission, "ready"));
        require("initial raw selector absent", NativeAccess.get(SelectiveReloadStateHandler.INSTANCE, "currentPredicate") == null);
        JsonObject initial = evidence.capture(false);
        require("initial hook frames and scopes absent", !initial.get("hookFrameEntryPresent").getAsBoolean()
                && initial.get("nativeScopeDepth").getAsInt() == 0 && initial.get("active").getAsInt() == 0);
        require("initial adapter preparation idle", !initial.get("preparing").getAsBoolean());
        manager = suite.client.func_110442_L();
        languageManager = suite.client.func_135016_M();
        require("reviewed resource manager", manager.getClass().getName().equals("net.minecraft.client.resources.SimpleReloadableResourceManager"));
        require("actual manager implements reload API", manager instanceof IReloadableResourceManager);
        activeLocale = NativeAccess.get(suite.hooks, "activeLocale");
        activeLegacy = NativeAccess.get(suite.hooks, "activeLegacy");
        require("actual native owners match active bindings", NativeAccess.get(languageManager, "field_135049_a") == activeLocale
                && NativeAccess.get(activeLegacy.getClass(), "field_74817_a") == activeLegacy
                && NativeAccess.get(suite.hooks, "activeManager") == manager);
        clientMap = NativeAccess.get(activeLocale, "field_135032_a");
        legacyMap = NativeAccess.get(activeLegacy, "field_74816_c");
        require("exact legacy HashMap", legacyMap.getClass() == HashMap.class);
        Class<?> state = NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.bootstrap.NativeLanguageState");
        refreshKind = (int) number(state, "REFRESH");
        legacyKind = (int) number(state, "LEGACY_REPLACE");
        selected = suite.client.func_135016_M().func_135041_c();
        require("selected language is actual selected code", selected != null && selected.func_135034_a().equals(NativeAccess.get(languageManager, "field_135048_c")));
        selection = new Selection();
        configBytes = Files.readAllBytes(suite.configFile.toPath());
        progress = new Progress();
        suite.activeNative = this;
        suite.nativeAudit = this;
        if (!legacy) {
            logger = new CatchObserver();
            logger.attach();
        }
        if (later) {
            Object value = NativeAccess.get(manager, "field_110546_b");
            require("exact native listener ArrayList", value.getClass() == ArrayList.class);
            listeners = (List<IResourceManagerReloadListener>) value;
            originalListeners = new ArrayList<>(listeners);
            require("active LanguageManager registered once", occurrences(listeners, languageManager) == 1);
            laterRegistrationAttempted = true;
            ((IReloadableResourceManager) manager).func_110542_a(laterListener);
            require("one disarmed registration callback", setupCallbacks == 1);
            require("registration appended only owned listener", appendOnly(listeners, originalListeners, laterListener));
            require("LanguageManager precedes owned listener", identityIndex(listeners, languageManager) < identityIndex(listeners, laterListener));
        }
        require("setup observations succeeded", instrumentationFailure == null);
        require("setup progress restored", sameIdentities(progress.bars(), progress.baseline));
        call(suite.adapter, "onNativeReload", nativeListener);
        call(suite.adapter, "onReload", ordinaryListener);
        require("setup remains ready", suite.ready());
        sourceBefore = call(suite.hooks, "snapshot");
        Object stamp = NativeAccess.get(sourceBefore, "stamp");
        require("baseline resource generation applied", stamp != null
                && number(stamp, "resourceGeneration") == number(sourceBefore, "resourceGeneration"));
        require("baseline stamp binds actual manager and language manager", NativeAccess.get(stamp, "resourceManager") == manager
                && NativeAccess.get(stamp, "languageManager") == languageManager);
        retained = call(suite.adapter, "snapshot");
        baselineMaps = evidence.nativeMaps();
        require("native owned language witness", witness(baselineMaps, "client") && witness(baselineMaps, "legacy"));
        legacyTimestamp = number(activeLegacy, "field_150511_e");
        result.add("before", checkpoint());
        JsonObject profiles = new JsonObject();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) call(suite.admission, "diagnostics")).entrySet())
            profiles.addProperty((String) entry.getKey(), (String) entry.getValue());
        result.add("admission", profiles);
        result.add("setup", setupReceipt());
        setupComplete = true;
    }

    void domains(Object pack) {
        if (!recording) return;
        try {
            requireThread();
            if (pack != suite.pack) throw new IllegalStateException("Wrong owned pack callback");
            Pass pass = resourcePass(true);
            if (pass == null) throw new IllegalStateException("Owned domains outside the reviewed refresh resource boundary");
            domainsCalls++;
            pass.domains++;
            record("owned-domains", pass, null);
            if (!legacy && !later && armed && pass.ordinal == faults + 1 && faults < sentinels.length) fire(pass, "domains");
        } catch (Throwable failure) { propagateObservation(failure); }
    }

    void read(String path) {
        if (!recording) return;
        try {
            requireThread();
            readStreams++;
            List<Object> frames = suite.frameStack();
            if (frames.isEmpty()) throw new IllegalStateException("Owned stream without a language frame");
            Object top = frames.get(0);
            boolean ignored = (Boolean) NativeAccess.get(top, "ignored");
            if ((int) number(top, "kind") != suite.localeKind) throw new IllegalStateException("Owned language stream outside Locale");
            if (ignored) {
                require("secondary ignored scope absent", NativeAccess.get(top, "scope") == null);
                require("secondary owner is not active Locale", NativeAccess.get(top, "owner") != activeLocale);
                ignoredFrames.put(top, Boolean.TRUE);
                ignoredOwners.put(NativeAccess.get(top, "owner"), Boolean.TRUE);
                record("secondary-ignored-stream", null, path);
                return;
            }
            if (NativeAccess.get(top, "owner") != activeLocale || NativeAccess.get(top, "scope") == null)
                throw new IllegalStateException("Unexpected active Locale stream");
            Object language = null, resource = null;
            for (Object frame : frames) {
                int kind = (int) number(frame, "kind");
                if (!(Boolean) NativeAccess.get(frame, "ignored") && kind == suite.languageKind
                        && NativeAccess.get(frame, "owner") == languageManager) language = NativeAccess.get(frame, "scope");
                if (!(Boolean) NativeAccess.get(frame, "ignored") && kind == suite.resourceKind
                        && NativeAccess.get(frame, "owner") == manager) resource = NativeAccess.get(frame, "scope");
            }
            Pass pass = passIndex.get(resource);
            if (legacy || language == null || pass == null) throw new IllegalStateException("Unbound native language witness");
            if (NativeAccess.get(NativeAccess.get(top, "scope"), "parent") != language || NativeAccess.get(language, "parent") != resource)
                throw new IllegalStateException("Native language stream has an unexpected parent scope");
            languages.put(language, Boolean.TRUE);
            locales.put(NativeAccess.get(top, "scope"), Boolean.TRUE);
            pass.languages.put(language, Boolean.TRUE);
            pass.locales.put(NativeAccess.get(top, "scope"), Boolean.TRUE);
            record("active-native-stream", pass, path);
        } catch (Throwable failure) { propagateObservation(failure); }
    }

    private Pass resourcePass(boolean register) throws Exception {
        List<Object> frames = suite.frameStack();
        if (frames.isEmpty()) return null;
        Object top = frames.get(0);
        if ((Boolean) NativeAccess.get(top, "ignored") || (int) number(top, "kind") != suite.resourceKind
                || NativeAccess.get(top, "owner") != manager) return null;
        Object resource = NativeAccess.get(top, "scope"), refresh = null;
        for (Object frame : frames) {
            if ((Boolean) NativeAccess.get(frame, "ignored")) continue;
            int kind = (int) number(frame, "kind");
            if (kind == refreshKind && NativeAccess.get(frame, "owner") == suite.client)
                refresh = NativeAccess.get(frame, "scope");
        }
        if (resource == null || refresh == null || NativeAccess.get(resource, "parent") != refresh) return null;
        if (refreshScope == null) refreshScope = refresh;
        if (refreshScope != refresh) throw new IllegalStateException("Another refresh scope entered the owned operation");
        Pass pass = passIndex.get(resource);
        if (pass == null) {
            if (!register) throw new IllegalStateException("Late listener reached an unobserved owned domains scope");
            if (passes.size() >= 2) throw new IllegalStateException("Unexpected third primary resource pass");
            pass = new Pass(resource, refresh, passes.size() + 1);
            passIndex.put(resource, pass);
            passes.add(pass);
        }
        return pass;
    }

    private void fire(Pass pass, String seam) throws Exception {
        if (!armed || pass.fault != 0 || pass.ordinal != faults + 1 || faults >= sentinels.length)
            throw new IllegalStateException("Native fault plan was not consumed in order");
        Throwable sentinel = sentinels[faults++];
        pass.fault = faults;
        armed = faults < sentinels.length;
        progress.capture(pass, later);
        JsonObject event = record("throw-" + seam, pass, null);
        event.add("sentinel", error(sentinel));
        throwSentinel(sentinel);
    }

    private void legacyFault(String seam) {
        try {
            requireThread();
            if (!recording || !armed || !legacy || faults != 0) throw new IllegalStateException("Unexpected legacy input traversal");
            List<Object> frames = suite.frameStack();
            if (frames.size() != 1) throw new IllegalStateException("Legacy replacement has an unexpected parent");
            Object frame = frames.get(0), scope = NativeAccess.get(frame, "scope");
            if ((int) number(frame, "kind") != legacyKind || (Boolean) NativeAccess.get(frame, "ignored")
                    || NativeAccess.get(frame, "owner") != activeLegacy || scope == null || NativeAccess.get(scope, "parent") != null)
                throw new IllegalStateException("Legacy fault is not inside the active singleton replacement");
            armed = false;
            faults++;
            JsonObject event = record("throw-" + seam, null, null);
            event.addProperty("legacyScopeIdentity", evidence.identity(scope));
            event.add("sentinel", error(sentinels[0]));
            throwSentinel(sentinels[0]);
        } catch (Throwable failure) { propagateObservation(failure); }
    }

    private void publication(String name) {
        if (!recording) return;
        try {
            requireThread();
            JsonObject value = evidence.capture(false);
            value.addProperty("event", name);
            publications.add(value);
        } catch (Throwable failure) { failed(failure); }
    }

    private void verifyImmediate() throws Exception {
        check("all planned faults fired", faults == sentinels.length);
        check("old retained snapshot ineligible", !(Boolean) call(retained, "ready"));
        check("native scopes drained", number(call(suite.hooks, "snapshot"), "active") == 0);
        JsonObject current = evidence.capture(true);
        check("hook frame entry removed", !current.get("hookFrameEntryPresent").getAsBoolean());
        check("native scope stack empty", current.get("nativeScopeDepth").getAsInt() == 0);
        check("adapter preparing cleared", !current.get("preparing").getAsBoolean());
        check("boundary admission retained", (Boolean) call(suite.admission, "ready"));
        check("selector remains absent", NativeAccess.get(SelectiveReloadStateHandler.INSTANCE, "currentPredicate") == null);
        check("native owners retained", NativeAccess.get(suite.hooks, "activeLocale") == activeLocale
                && NativeAccess.get(suite.hooks, "activeLegacy") == activeLegacy);
        check("native map objects retained", NativeAccess.get(activeLocale, "field_135032_a") == clientMap
                && NativeAccess.get(activeLegacy, "field_74816_c") == legacyMap);
        check("selected language retained", selected.func_135034_a().equals(NativeAccess.get(languageManager, "field_135048_c")));
        Object source = call(suite.hooks, "snapshot"), stamp = NativeAccess.get(source, "stamp");
        boolean success = !legacy && !linkage && !both;
        if (success) {
            check("native fallback returned normally", thrown == null);
            check("successful fallback is READY", "READY".equals(current.get("phase").getAsString()) && stamp != null && suite.ready());
            check("fallback core publication observed", publications.size() > 0);
            check("fallback actual native witness", witness(evidence.nativeMaps(), "client") && witness(evidence.nativeMaps(), "legacy"));
        } else {
            check("original outer Throwable identity", thrown == sentinels[sentinels.length - 1]);
            check("failed operation is UNAVAILABLE", "UNAVAILABLE".equals(current.get("phase").getAsString()));
            check("failed operation stamp cleared", stamp == null);
            check("failed operation product ineligible", !suite.ready());
            check("failed operation no core publication", publications.size() == 0);
        }
        if (legacy) {
            check("legacy operation no primary resource pass", passes.isEmpty() && domainsCalls == 0 && readStreams == 0);
            check("legacy resource generation unchanged", number(source, "resourceGeneration") == number(sourceBefore, "resourceGeneration"));
            check("legacy timestamp unchanged", number(activeLegacy, "field_150511_e") == legacyTimestamp);
            check("client dictionary unchanged", baselineMaps.get("client").equals(evidence.nativeMaps().get("client")));
            check("exact partial legacy entry count", ((Map<?, ?>) legacyMap).size() == (secondNext ? 1 : 0));
            if (secondNext) {
                Map.Entry<?, ?> entry = ((Map<?, ?>) legacyMap).entrySet().iterator().next();
                check("exact partial legacy witness", legacyKey().equals(entry.getKey()) && legacyValue().equals(entry.getValue()));
            }
            check("constant-size input reached once", mapSizes == 1);
            check("entrySet reached once", mapEntrySets == 1);
            check("native iterator traversal counts", secondNext ? mapIterators == 1 && mapHasNext == 2 && mapNext == 2 && mapReturned == 1
                    : mapIterators == 0 && mapHasNext == 0 && mapNext == 0 && mapReturned == 0);
            check("legacy operation kept pack selection", selection.unchanged());
        } else {
            int expected = linkage ? 1 : 2;
            check("expected requested and fallback resource scopes", passes.size() == expected);
            check("one owned domains call per primary scope", domainsCalls == expected && passes.stream().allMatch(pass -> pass.domains == 1));
            check("resource generation counts the primary passes", number(source, "resourceGeneration") == number(sourceBefore, "resourceGeneration") + expected);
            check("shared actual refresh scope", refreshScope != null && passes.stream().allMatch(pass -> pass.refresh == refreshScope));
            check("native catch original Throwable identity", logger != null && logger.matches == (linkage ? 0 : 1)
                    && (linkage || logger.observed == sentinels[0]));
            check("native catch only first RuntimeException", logger != null && logger.otherSentinels == 0);
            if (later) {
                check("one owned late failure per primary pass", lateCalls == expected);
                check("language completed before every late failure", passes.stream().allMatch(pass -> pass.languages.size() == 1 && pass.locales.size() == 1));
                check("late failure keeps actual loaded witness", witness(evidence.nativeMaps(), "client") && witness(evidence.nativeMaps(), "legacy"));
            } else {
                check("requested pass failed before primary language witness", !passes.isEmpty() && passes.get(0).languages.isEmpty());
                if (both) check("fallback also failed before primary language witness", passes.size() == 2 && passes.get(1).languages.isEmpty());
                else if (!linkage) check("successful fallback language witness", passes.size() == 2 && passes.get(1).languages.size() == 1 && passes.get(1).locales.size() == 1);
                if (linkage || both) {
                    check("before-listener native maps unchanged", baselineMaps.equals(evidence.nativeMaps()));
                    check("before-listener legacy timestamp unchanged", number(activeLegacy, "field_150511_e") == legacyTimestamp);
                }
            }
            selection.verifyNativeOutcome(success, !linkage);
        }
        check("configuration bytes untouched", Arrays.equals(configBytes, Files.readAllBytes(suite.configFile.toPath())));
        check("residual progress belongs exactly to failed passes", progress.residualMatches());
        for (int index = 0; index < publications.size(); index++) {
            JsonObject publication = publications.get(index).getAsJsonObject();
            check("publication " + index + " is current ready tuple", publication.get("ready").getAsBoolean()
                    && publication.get("snapshotNativeStateIdentity").equals(publication.get("sourceIdentity"))
                    && publication.get("snapshotSettingsIdentity").equals(publication.get("settingsIdentity"))
                    && publication.get("settingsRequest").equals(publication.get("requests")));
        }
    }

    private void recover() throws Exception {
        selection.restore();
        suite.client.func_135016_M().func_135045_a(selected);
        if (!Arrays.equals(configBytes, Files.readAllBytes(suite.configFile.toPath()))) {
            Files.write(suite.configFile.toPath(), configBytes);
            call(suite.config, "reload");
        }
        long beforeGeneration = number(call(suite.hooks, "snapshot"), "resourceGeneration");
        long beforeApply = number(NativeAccess.get(suite.hooks, "state"), "applySequence");
        JsonObject value = new JsonObject();
        value.addProperty("operation", legacy ? "LanguageManager.onResourceManagerReload" : "Minecraft.refreshResources");
        value.addProperty("harnessCalls", 1);
        if (legacy) suite.client.func_135016_M().func_110549_a(suite.client.func_110442_L());
        else suite.client.func_110436_a();
        value.add("returned", checkpoint());
        result.add("recovery", value);
        Object source = call(suite.hooks, "snapshot"), stamp = NativeAccess.get(source, "stamp");
        require("recovery native and product READY", suite.ready() && stamp != null && "READY".equals(((Enum<?>) NativeAccess.get(source, "phase")).name()));
        require("recovery resource generation", number(source, "resourceGeneration") == beforeGeneration + (legacy ? 0 : 1));
        require("recovery language applied current manager", number(stamp, "resourceGeneration") == number(source, "resourceGeneration"));
        require("recovery committed one fresh native apply", number(stamp, "applySequence") == beforeApply + 1);
        require("recovery native dictionary contents restored", baselineMaps.get("client").equals(evidence.nativeMaps().get("client"))
                && baselineMaps.get("legacy").equals(evidence.nativeMaps().get("legacy")));
        require("recovery pack selection restored", selection.unchanged());
        require("recovery progress restored", sameIdentities(progress.bars(), progress.baseline));
        JsonObject state = evidence.capture(false);
        require("recovery frames and scopes drained", !state.get("hookFrameEntryPresent").getAsBoolean()
                && state.get("active").getAsInt() == 0 && state.get("nativeScopeDepth").getAsInt() == 0 && !state.get("preparing").getAsBoolean());
        require("recovery selector absent", NativeAccess.get(SelectiveReloadStateHandler.INSTANCE, "currentPredicate") == null);
    }

    private JsonObject cleanupOwned() {
        JsonObject cleanup = new JsonObject();
        boolean clean = true;
        try {
            removeIdentity(suite.nativeListeners, nativeListener);
            removeIdentity(suite.ordinaryListeners, ordinaryListener);
            cleanup.addProperty("productListenersRemoved", occurrences(suite.nativeListeners, nativeListener) == 0 && occurrences(suite.ordinaryListeners, ordinaryListener) == 0);
            boolean originals = (originalProductNative == null || sameIdentities(suite.nativeListeners, originalProductNative))
                    && (originalProductOrdinary == null || sameIdentities(suite.ordinaryListeners, originalProductOrdinary));
            cleanup.addProperty("originalProductListenersPreserved", originals);
            if (!originals) throw new IllegalStateException("Non-owned product listener order changed");
        } catch (Throwable failure) {
            cleanup.add("productListenerFailure", error(unwrap(failure)));
            cleanup.addProperty("productListenersRemoved", false);
            clean = false;
        }
        try {
            if (laterRegistrationAttempted) {
                if (NativeAccess.get(manager, "field_110546_b") != listeners || listeners.getClass() != ArrayList.class)
                    throw new IllegalStateException("Native listener registry changed");
                removeIdentity(listeners, laterListener);
                if (!sameIdentities(listeners, originalListeners)) throw new IllegalStateException("Non-owned listener order changed");
            }
            cleanup.addProperty("nativeListenerRestored", true);
        } catch (Throwable failure) { cleanup.add("nativeListenerFailure", error(unwrap(failure))); clean = false; }
        try {
            if (logger != null) cleanup.add("logger", logger.close());
            else cleanup.addProperty("loggerNotInstalled", true);
        } catch (Throwable failure) { cleanup.add("loggerFailure", error(unwrap(failure))); clean = false; }
        try {
            if (progress != null) cleanup.add("progress", progress.clean());
            else cleanup.addProperty("progressNotCaptured", true);
        } catch (Throwable failure) { cleanup.add("progressFailure", error(unwrap(failure))); clean = false; }
        clean &= cleanup.get("productListenersRemoved").getAsBoolean();
        cleanup.addProperty("passed", clean);
        return cleanup;
    }

    JsonObject cleanupForTeardown() {
        armed = false;
        recording = false;
        JsonObject cleanup = cleanupOwned();
        if (cleanup.get("passed").getAsBoolean() && selection != null) {
            try { selection.restore(); }
            catch (Throwable failure) { cleanup.add("selectionFailure", error(unwrap(failure))); cleanup.addProperty("passed", false); }
        }
        return cleanup;
    }

    JsonObject auditTeardown() throws Exception {
        JsonObject value = new JsonObject();
        JsonObject state = evidence.capture(false);
        boolean progressRestored = sameIdentities(progress.bars(), progress.baseline);
        boolean productListenersRestored = sameIdentities(suite.nativeListeners, originalProductNative)
                && sameIdentities(suite.ordinaryListeners, originalProductOrdinary);
        boolean nativeListenersRestored = !laterRegistrationAttempted || NativeAccess.get(manager, "field_110546_b") == listeners
                && sameIdentities(listeners, originalListeners);
        boolean loggerRestored = logger == null || logger.originalConfigurationPreserved() && logger.isStopped();
        boolean idle = !state.get("hookFrameEntryPresent").getAsBoolean() && state.get("nativeScopeDepth").getAsInt() == 0
                && state.get("active").getAsInt() == 0 && !state.get("preparing").getAsBoolean();
        boolean selectorAbsent = NativeAccess.get(SelectiveReloadStateHandler.INSTANCE, "currentPredicate") == null;
        value.add("progress", progress.captureBars(progress.bars()));
        value.addProperty("progressRestored", progressRestored);
        value.addProperty("productListenersRestored", productListenersRestored);
        value.addProperty("nativeListenersRestored", nativeListenersRestored);
        value.addProperty("loggerRestored", loggerRestored);
        value.addProperty("idle", idle);
        value.addProperty("selectorAbsent", selectorAbsent);
        value.addProperty("boundaryAdmissionRetained", (Boolean) call(suite.admission, "ready"));
        value.addProperty("passed", progressRestored && productListenersRestored && nativeListenersRestored && loggerRestored
                && idle && selectorAbsent && (Boolean) call(suite.admission, "ready"));
        return value;
    }

    private JsonObject checkpoint() throws Exception {
        JsonObject result = new JsonObject();
        result.addProperty("phase", phase);
        result.add("state", evidence.capture(true));
        result.addProperty("legacyTimestamp", number(activeLegacy, "field_150511_e"));
        result.add("selection", selection.capture());
        result.add("progress", progress.captureBars(progress.bars()));
        Object state = NativeAccess.get(suite.hooks, "state");
        JsonObject counters = new JsonObject();
        for (String field : new String[]{"sequence", "resourceSequence", "applySequence", "clientRevision", "legacyRevision", "invalidation"})
            counters.addProperty(field, number(state, field));
        result.add("nativeCounters", counters);
        result.add("counts", counts());
        return result;
    }

    private JsonObject setupReceipt() {
        JsonObject value = new JsonObject();
        value.addProperty("laterRegistrationCallbacks", setupCallbacks);
        value.addProperty("threadIdentity", evidence.identity(thread));
        value.addProperty("managerIdentity", evidence.identity(manager));
        value.addProperty("packIdentity", evidence.identity(suite.pack));
        value.addProperty("operationToken", evidence.identity(this));
        if (originalListeners != null) value.add("originalNativeListenerIdentities", identities(originalListeners));
        value.add("originalProductNativeListenerIdentities", identities(originalProductNative));
        value.add("originalProductOrdinaryListenerIdentities", identities(originalProductOrdinary));
        return value;
    }

    private JsonObject counts() {
        JsonObject value = new JsonObject();
        value.addProperty("domainsCalls", domainsCalls);
        value.addProperty("readStreams", readStreams);
        value.addProperty("faults", faults);
        value.addProperty("laterFailureCallbacks", lateCalls);
        value.addProperty("activeLanguageScopesWitnessedByStreams", languages.size());
        value.addProperty("activeLocaleScopesWitnessedByStreams", locales.size());
        value.addProperty("secondaryIgnoredFramesWitnessedByStreams", ignoredFrames.size());
        value.addProperty("secondaryIgnoredOwnersWitnessedByStreams", ignoredOwners.size());
        value.addProperty("refreshScopeIdentity", evidence.identity(refreshScope));
        value.addProperty("mapSizeCalls", mapSizes);
        value.addProperty("mapEntrySetCalls", mapEntrySets);
        value.addProperty("mapIteratorCalls", mapIterators);
        value.addProperty("mapHasNextCalls", mapHasNext);
        value.addProperty("mapNextCalls", mapNext);
        value.addProperty("mapReturnedEntries", mapReturned);
        JsonArray resources = new JsonArray();
        for (Pass pass : passes) resources.add(pass.capture());
        value.add("resourceScopesAtDomains", resources);
        if (logger != null) value.add("catchLogger", logger.receipt());
        return value;
    }

    private JsonObject record(String name, Pass pass, String path) throws Exception {
        JsonObject value = evidence.capture(false);
        value.addProperty("event", name);
        value.addProperty("eventIndex", events.size());
        value.addProperty("operationToken", evidence.identity(this));
        value.addProperty("threadIdentity", evidence.identity(Thread.currentThread()));
        value.addProperty("packIdentity", evidence.identity(suite.pack));
        value.addProperty("managerIdentity", evidence.identity(manager));
        if (pass != null) value.add("resourcePass", pass.capture());
        if (path != null) value.addProperty("resourcePath", path);
        events.add(value);
        return value;
    }

    private final class OwnedListener implements IResourceManagerReloadListener {
        @Override public void func_110549_a(IResourceManager actual) {
            if (!recording) {
                if (suite.activeNative == LanguageNativeFailureAdversary.this && phase.equals("setup")) {
                    try {
                        requireThread();
                        if (actual != manager || armed) throw new IllegalStateException("Unexpected registration callback");
                        setupCallbacks++;
                        JsonObject value = evidence.capture(false);
                        value.addProperty("managerIdentity", evidence.identity(actual));
                        value.addProperty("threadIdentity", evidence.identity(Thread.currentThread()));
                        result.add("registrationCallback", value);
                    } catch (Throwable failure) { failed(failure); }
                }
                return;
            }
            try {
                requireThread();
                if (actual != manager || !later) throw new IllegalStateException("Wrong later listener operation");
                Pass pass = resourcePass(false);
                if (pass == null || pass.domains != 1 || pass.languages.size() != 1 || pass.locales.size() != 1)
                    throw new IllegalStateException("Late listener lacked its native language witness");
                lateCalls++;
                record("owned-late-listener", pass, null);
                if (armed) fire(pass, "late-listener");
            } catch (Throwable failure) { propagateObservation(failure); }
        }
    }

    private final class FaultMap extends AbstractMap<String, String> {
        @Override public int size() { mapSizes++; return 2; }
        @Override public Set<Entry<String, String>> entrySet() {
            mapEntrySets++;
            if (!secondNext) legacyFault("legacy-entryset");
            return new AbstractSet<Entry<String, String>>() {
                @Override public int size() { return 2; }
                @Override public Iterator<Entry<String, String>> iterator() {
                    mapIterators++;
                    return new Iterator<Entry<String, String>>() {
                        private int next;
                        @Override public boolean hasNext() { mapHasNext++; return next < 2; }
                        @Override public Entry<String, String> next() {
                            mapNext++;
                            if (next == 0) { next++; mapReturned++; return new SimpleImmutableEntry<>(legacyKey(), legacyValue()); }
                            if (next == 1) { next++; legacyFault("legacy-second-next"); }
                            throw new NoSuchElementException();
                        }
                        @Override public void remove() { throw new UnsupportedOperationException(); }
                    };
                }
            };
        }
    }

    private final class Pass {
        final Object scope, refresh;
        final int ordinal;
        final IdentityHashMap<Object, Boolean> languages = new IdentityHashMap<>(), locales = new IdentityHashMap<>();
        int domains, fault;
        Pass(Object scope, Object refresh, int ordinal) { this.scope = scope; this.refresh = refresh; this.ordinal = ordinal; }
        JsonObject capture() {
            JsonObject value = new JsonObject();
            value.addProperty("ordinal", ordinal);
            value.addProperty("role", ordinal == 1 ? "requested" : "fallback");
            value.addProperty("scopeIdentity", evidence.identity(scope));
            value.addProperty("refreshScopeIdentity", evidence.identity(refresh));
            value.addProperty("domainsCalls", domains);
            value.addProperty("faultOrdinal", fault);
            value.addProperty("activeLanguageScopesWitnessedByStreams", languages.size());
            value.addProperty("activeLocaleScopesWitnessedByStreams", locales.size());
            if (fault > 0 && !later) {
                value.addProperty("primaryListenerDispatchReached", false);
                value.addProperty("primaryListenerDispatchEvidence", "source-derived from owned getResourceDomains before the primary resource listener loop; not an all-third-party invocation trace");
            }
            return value;
        }
    }

    private final class Selection {
        final ResourcePackRepository repository = suite.client.func_110438_M();
        final List<ResourcePackRepository.Entry> entries = new ArrayList<>(repository.func_110613_c());
        final List<String> configured = new ArrayList<>(suite.client.field_71474_y.field_151453_l);
        final List<String> incompatible = new ArrayList<>(suite.client.field_71474_y.field_183018_l);
        final Object configuredList = suite.client.field_71474_y.field_151453_l;
        final Object incompatibleList = suite.client.field_71474_y.field_183018_l;
        final Object serverPack = repository.func_148530_e();

        Selection() {
            if (configuredList.getClass() != ArrayList.class || incompatibleList.getClass() != ArrayList.class)
                throw new IllegalStateException("Unknown resource option lists");
        }

        JsonObject capture() {
            JsonObject value = new JsonObject();
            JsonArray selected = new JsonArray();
            for (ResourcePackRepository.Entry entry : repository.func_110613_c()) {
                JsonObject item = new JsonObject();
                item.addProperty("entryIdentity", evidence.identity(entry));
                item.addProperty("packIdentity", evidence.identity(entry.func_110514_c()));
                item.addProperty("name", entry.func_110515_d());
                selected.add(item);
            }
            value.add("selected", selected);
            value.add("configured", strings(suite.client.field_71474_y.field_151453_l));
            value.add("incompatible", strings(suite.client.field_71474_y.field_183018_l));
            value.addProperty("configuredListIdentity", evidence.identity(suite.client.field_71474_y.field_151453_l));
            value.addProperty("incompatibleListIdentity", evidence.identity(suite.client.field_71474_y.field_183018_l));
            value.addProperty("serverPackIdentity", evidence.identity(repository.func_148530_e()));
            return value;
        }

        boolean unchanged() {
            return sameIdentities(entries, repository.func_110613_c()) && configured.equals(suite.client.field_71474_y.field_151453_l)
                    && incompatible.equals(suite.client.field_71474_y.field_183018_l)
                    && configuredList == suite.client.field_71474_y.field_151453_l
                    && incompatibleList == suite.client.field_71474_y.field_183018_l && serverPack == repository.func_148530_e();
        }

        void verifyNativeOutcome(boolean successfulFallback, boolean hadFallback) {
            check("native selection outcome", hadFallback ? repository.func_110613_c().isEmpty() : sameIdentities(entries, repository.func_110613_c()));
            check("native option-list outcome", successfulFallback
                    ? suite.client.field_71474_y.field_151453_l.isEmpty() && suite.client.field_71474_y.field_183018_l.isEmpty()
                    : configured.equals(suite.client.field_71474_y.field_151453_l) && incompatible.equals(suite.client.field_71474_y.field_183018_l));
            check("native option list objects retained", configuredList == suite.client.field_71474_y.field_151453_l && incompatibleList == suite.client.field_71474_y.field_183018_l);
            check("server pack reference retained", serverPack == repository.func_148530_e());
        }

        void restore() {
            if (suite.client.func_110438_M() != repository || configuredList != suite.client.field_71474_y.field_151453_l
                    || incompatibleList != suite.client.field_71474_y.field_183018_l || serverPack != repository.func_148530_e())
                throw new IllegalStateException("Resource selection ownership changed");
            if (unchanged()) return;
            repository.func_148527_a(new ArrayList<>(entries));
            suite.client.field_71474_y.field_151453_l.clear();
            suite.client.field_71474_y.field_151453_l.addAll(configured);
            suite.client.field_71474_y.field_183018_l.clear();
            suite.client.field_71474_y.field_183018_l.addAll(incompatible);
            suite.client.field_71474_y.func_74303_b();
            if (!unchanged()) throw new IllegalStateException("Resource selection was not restored");
        }
    }

    private final class Progress {
        final List<ProgressBar> baseline = bars();
        final List<ProgressBar> captured = new ArrayList<>(), remaining = new ArrayList<>();
        final JsonArray captureEvents = new JsonArray();

        List<ProgressBar> bars() {
            List<ProgressBar> result = new ArrayList<>();
            Iterator<ProgressBar> iterator = ProgressManager.barIterator();
            while (iterator.hasNext()) {
                if (result.size() >= 4096) throw new IllegalStateException("Progress bar inventory exceeded bound");
                ProgressBar bar = iterator.next();
                if (bar == null || bar.getClass() != ProgressBar.class) throw new IllegalStateException("Unknown native progress bar");
                result.add(bar);
            }
            return result;
        }

        JsonArray captureBars(List<ProgressBar> bars) {
            JsonArray result = new JsonArray();
            for (ProgressBar bar : bars) {
                JsonObject item = new JsonObject();
                item.addProperty("identity", evidence.identity(bar));
                item.addProperty("title", bar.getTitle());
                item.addProperty("step", bar.getStep());
                item.addProperty("steps", bar.getSteps());
                item.addProperty("message", bar.getMessage());
                result.add(item);
            }
            return result;
        }

        void capture(Pass pass, boolean inner) {
            List<ProgressBar> current = bars(), before = expected(captured);
            int count = inner ? 2 : 1;
            if (current.size() != before.size() + count || !sameIdentities(current.subList(0, Math.min(before.size(), current.size())), before))
                throw new IllegalStateException("Ambiguous progress bars at owned fault");
            List<ProgressBar> fresh = new ArrayList<>(current.subList(before.size(), current.size()));
            if (!"Loading Resources".equals(fresh.get(0).getTitle()) || inner && !"Reloading".equals(fresh.get(1).getTitle()))
                throw new IllegalStateException("Unexpected native fault progress titles");
            for (ProgressBar bar : fresh) {
                if (identityIndex(before, bar) >= 0 || occurrences(fresh, bar) != 1)
                    throw new IllegalStateException("Fault progress bars are not distinct new objects");
                if (bar.getStep() < 0 || bar.getStep() > bar.getSteps() || bar.getSteps() > 100000)
                    throw new IllegalStateException("Unsupported native progress bounds");
            }
            captured.addAll(fresh);
            remaining.addAll(fresh);
            JsonObject value = new JsonObject();
            value.addProperty("resourceScopeIdentity", evidence.identity(pass.scope));
            value.addProperty("faultOrdinal", pass.fault);
            value.add("bars", captureBars(fresh));
            captureEvents.add(value);
        }

        boolean residualMatches() { return sameIdentities(bars(), expected(captured)); }

        JsonObject clean() {
            JsonObject result = new JsonObject();
            result.add("capturedAtFault", captureEvents);
            result.add("before", captureBars(bars()));
            if (!sameIdentities(bars(), expected(remaining))) throw new IllegalStateException("Progress cleanup does not own all residual bars");
            int steps = 0, popped = 0;
            for (int index = remaining.size() - 1; index >= 0; index--) {
                ProgressBar bar = remaining.get(index);
                int start = bar.getStep(), end = bar.getSteps();
                if (start < 0 || end < start || end > 100000) throw new IllegalStateException("Progress cleanup bound changed");
                for (int next = start; next < end; next++) {
                    if (++steps > 400000) throw new IllegalStateException("Progress cleanup step ceiling");
                    bar.step("Owned lifecycle recovery");
                    if (bar.getStep() != next + 1 || bar.getSteps() != end) throw new IllegalStateException("Progress step contract changed");
                }
                ProgressManager.pop(bar);
                remaining.remove(index);
                popped++;
                if (!sameIdentities(bars(), expected(remaining))) throw new IllegalStateException("Progress pop changed unrelated bars");
            }
            result.addProperty("publicStepCalls", steps);
            result.addProperty("publicPopCalls", popped);
            result.add("after", captureBars(bars()));
            result.addProperty("restored", sameIdentities(bars(), baseline));
            return result;
        }

        private List<ProgressBar> expected(List<ProgressBar> owned) {
            List<ProgressBar> result = new ArrayList<>(baseline);
            result.addAll(owned);
            return result;
        }
    }

    private final class CatchObserver extends AbstractAppender {
        final Logger nativeLogger;
        final LoggerContext context;
        final Configuration configuration;
        final LoggerConfig effective, rootConfig;
        final Map<String, LoggerConfig> configurations;
        final Map<String, Appender> appenders;
        final Map<String, ConfigView> views = new LinkedHashMap<>();
        final String loggerName;
        boolean attached;
        int matches, otherSentinels;
        Throwable observed;
        final JsonArray matched = new JsonArray();

        CatchObserver() throws Exception {
            super("pt112-native-fault-" + suite.nonce + "-" + sequence, null, null, true);
            Object value = NativeAccess.get(Minecraft.class, "field_147123_G");
            if (!(value instanceof Logger)) throw new IllegalStateException("Minecraft logger does not expose the reviewed effective configuration");
            nativeLogger = (Logger) value;
            loggerName = nativeLogger.getName();
            context = nativeLogger.getContext();
            configuration = context.getConfiguration();
            effective = configuration.getLoggerConfig(loggerName);
            rootConfig = configuration.getRootLogger();
            configurations = new LinkedHashMap<>(configuration.getLoggers());
            appenders = new LinkedHashMap<>(configuration.getAppenders());
            views.put("<effective>", new ConfigView(effective));
            views.put("<root>", new ConfigView(rootConfig));
            for (Map.Entry<String, LoggerConfig> entry : configurations.entrySet()) views.put("named:" + entry.getKey(), new ConfigView(entry.getValue()));
            if (!nativeLogger.isInfoEnabled()) throw new IllegalStateException("Minecraft catch log is not deliverable at its existing level");
            if (effective.getAppenders().containsKey(getName()) || appenders.containsKey(getName()))
                throw new IllegalStateException("Owned appender name collision");
        }

        void attach() {
            start();
            effective.addAppender(this, null, null);
            attached = true;
            if (effective.getAppenders().get(getName()) != this) throw new IllegalStateException("Owned catch appender was not attached");
        }

        @Override public void append(LogEvent event) {
            try {
                if (!recording || suite.activeNative != LanguageNativeFailureAdversary.this) return;
                Throwable error = event.getThrown();
                boolean sentinel = false;
                for (Throwable candidate : sentinels) if (candidate == error) sentinel = true;
                if (!sentinel || !loggerName.equals(event.getLoggerName()) || event.getMessage() == null
                        || !CATCH_MESSAGE.equals(event.getMessage().getFormattedMessage())) return;
                if (Thread.currentThread() != thread) {
                    failed(new IllegalStateException("Minecraft catch appender delivered outside the owned client thread"));
                    return;
                }
                if (error == sentinels[0]) { matches++; observed = error; }
                else otherSentinels++;
                JsonObject value = LanguageNativeFailureAdversary.this.error(error);
                value.addProperty("logger", event.getLoggerName());
                value.addProperty("catchMessage", event.getMessage().getFormattedMessage());
                value.addProperty("sameFirstSentinel", error == sentinels[0]);
                matched.add(value);
            } catch (Throwable failure) { failed(failure); }
        }

        JsonObject receipt() {
            JsonObject value = new JsonObject();
            value.addProperty("matchesFirstSentinel", matches);
            value.addProperty("matchesOtherSentinel", otherSentinels);
            value.addProperty("loggerIdentity", evidence.identity(nativeLogger));
            value.addProperty("effectiveConfigIdentity", evidence.identity(effective));
            value.addProperty("observedThrowableIdentity", evidence.identity(observed));
            value.add("events", new JsonParser().parse(matched.toString()));
            return value;
        }

        JsonObject close() throws Exception {
            try {
                Appender present = effective.getAppenders().get(getName());
                if (present == this) { effective.removeAppender(getName()); attached = false; }
                else if (attached || present != null) throw new IllegalStateException("Owned appender identity changed");
            } finally { stop(); }
            if (!originalConfigurationPreserved()) throw new IllegalStateException("Original logger configuration changed");
            JsonObject value = receipt();
            value.addProperty("ownedAppenderRemoved", !effective.getAppenders().containsKey(getName()));
            value.addProperty("originalConfigurationPreserved", true);
            value.addProperty("ownedAppenderStopped", isStopped());
            return value;
        }

        boolean originalConfigurationPreserved() throws Exception {
            if (NativeAccess.get(Minecraft.class, "field_147123_G") != nativeLogger || nativeLogger.getContext() != context
                    || context.getConfiguration() != configuration || configuration.getLoggerConfig(loggerName) != effective
                    || configuration.getRootLogger() != rootConfig || !sameValues(configurations, configuration.getLoggers())
                    || !sameValues(appenders, configuration.getAppenders())) return false;
            for (ConfigView view : views.values()) if (!view.unchanged()) return false;
            return !effective.getAppenders().containsKey(getName());
        }
    }

    private static final class ConfigView {
        final LoggerConfig config;
        final Level level;
        final Filter filter;
        final boolean additive;
        final LoggerConfig parent;
        final Map<String, Appender> appenders;
        ConfigView(LoggerConfig config) {
            this.config = config;
            level = config.getLevel();
            filter = config.getFilter();
            additive = config.isAdditive();
            parent = config.getParent();
            appenders = new LinkedHashMap<>(config.getAppenders());
        }
        boolean unchanged() { return config.getLevel() == level && config.getFilter() == filter && config.isAdditive() == additive
                && config.getParent() == parent && sameValues(appenders, config.getAppenders()); }
    }

    private static boolean sameValues(Map<String, ?> expected, Map<String, ?> actual) {
        if (!expected.keySet().equals(actual.keySet())) return false;
        for (String key : expected.keySet()) if (expected.get(key) != actual.get(key)) return false;
        return true;
    }
    private static JsonArray strings(List<String> values) { JsonArray result = new JsonArray(); for (String value : values) result.add(value); return result; }
    private String legacyKey() { return suite.pack.key + ".partial." + sequence; }
    private String legacyValue() { return "Owned partial legacy " + suite.nonce + ":" + sequence; }
    private boolean witness(JsonObject maps, String name) {
        return maps.getAsJsonObject(name).has("fixtureWitness") && !maps.getAsJsonObject(name).get("fixtureWitness").isJsonNull()
                && "Fixture base".equals(maps.getAsJsonObject(name).get("fixtureWitness").getAsString());
    }
    private void requireThread() throws Exception {
        if (Thread.currentThread() != thread || !suite.client.func_152345_ab() || suite.activeNative != this)
            throw new IllegalStateException("Native scenario owner or thread changed");
        suite.verifyOwnership();
    }
    private void check(String name, boolean pass) {
        JsonObject value = new JsonObject(); value.addProperty("name", name); value.addProperty("pass", pass); checks.add(value); passed &= pass;
    }
    private void require(String name, boolean pass) { check(name, pass); if (!pass) throw new IllegalStateException(name); }
    private void failed(Throwable failure) { if (instrumentationFailure == null) instrumentationFailure = unwrap(failure); passed = false; }
    private void propagateObservation(Throwable failure) {
        for (Throwable sentinel : sentinels) if (failure == sentinel) throwSentinel(sentinel);
        failed(failure);
        throw new IllegalStateException("Native failure observation failed", failure);
    }
    private JsonObject error(Throwable failure) {
        JsonObject value = evidence.error(failure);
        Throwable cause = failure.getCause();
        value.addProperty("causeIdentity", evidence.identity(cause));
        if (cause != null) value.addProperty("causeType", cause.getClass().getName());
        return value;
    }
    private JsonArray identities(List<?> values) { JsonArray result = new JsonArray(); for (Object value : values) result.add(evidence.identity(value)); return result; }
    private static int identityIndex(List<?> values, Object target) { for (int index = 0; index < values.size(); index++) if (values.get(index) == target) return index; return -1; }
    private static int occurrences(List<?> values, Object target) { int result = 0; for (Object value : values) if (value == target) result++; return result; }
    private static void removeIdentity(List<?> values, Object target) { for (int index = values.size() - 1; index >= 0; index--) if (values.get(index) == target) values.remove(index); }
    private static boolean sameIdentities(List<?> first, List<?> second) { if (first.size() != second.size()) return false; for (int index = 0; index < first.size(); index++) if (first.get(index) != second.get(index)) return false; return true; }
    private static boolean appendOnly(List<?> current, List<?> before, Object appended) { return current.size() == before.size() + 1 && current.get(current.size() - 1) == appended && sameIdentities(current.subList(0, before.size()), before); }
    private static long number(Object value, String field) throws Exception { return ((Number) NativeAccess.get(value, field)).longValue(); }
    private static Object call(Object target, String method, Object... args) throws Exception {
        try { return NativeAccess.call(target, method, args); }
        catch (InvocationTargetException failure) {
            Throwable error = unwrap(failure);
            if (error instanceof Error) throw (Error) error;
            if (error instanceof Exception) throw (Exception) error;
            throw new IllegalStateException(error);
        }
    }
    private static Throwable unwrap(Throwable failure) { while (failure instanceof InvocationTargetException && failure.getCause() != null) failure = failure.getCause(); return failure; }
    private static void throwSentinel(Throwable failure) { if (failure instanceof LinkageError) throw (LinkageError) failure; throw (RuntimeException) failure; }
}
