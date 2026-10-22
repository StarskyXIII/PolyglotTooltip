package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.Language;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.resource.IResourceType;
import net.minecraftforge.client.resource.ReloadRequirements;
import net.minecraftforge.client.resource.SelectiveReloadStateHandler;
import net.minecraftforge.client.resource.VanillaResourceType;
import net.minecraftforge.common.ForgeModContainer;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.Loader;

final class LanguagePreparationAdversary {
    private static final String BASE = "com.starskyxiii.polyglottooltip.legacy.";
    private static final String FIRST = "pt_fixture_a", SECOND = "pt_fixture_b", THIRD = "pt_fixture_c";
    private static Suite installed;

    private LanguagePreparationAdversary() { }

    static JsonObject run(JsonObject params) throws Exception {
        Minecraft client = Minecraft.func_71410_x();
        if (!client.func_152345_ab()) throw new IllegalStateException("Client thread required");
        String action = params.get("action").getAsString();
        if ("install".equals(action)) {
            if (installed != null) throw new IllegalStateException("Preparation fixture already installed");
            Suite suite = new Suite(params);
            try {
                suite.packs.add(suite.pack);
                suite.packAdded = true;
                client.func_110436_a();
                if (!suite.ready()) throw new IllegalStateException("Scenario pack setup did not produce a ready language");
                installed = suite;
                JsonObject result = suite.status();
                result.addProperty("action", action);
                result.addProperty("setupFullReload", true);
                return result;
            } catch (Throwable failure) {
                try {
                    JsonObject cleanup = suite.uninstall();
                    if (!cleanup.get("passed").getAsBoolean())
                        failure.addSuppressed(new IllegalStateException("Preparation setup cleanup was incomplete"));
                }
                catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
                rethrow(failure);
                throw new AssertionError();
            }
        }
        Suite suite = installed;
        if (suite == null) throw new IllegalStateException("Preparation fixture is not installed");
        suite.verifyOwnership();
        if ("status".equals(action)) return suite.status();
        if ("uninstall".equals(action)) {
            try { return suite.uninstall(); }
            finally { if (suite.nativeCleanup == null) installed = null; }
        }
        if (suite.nativeCleanup != null) throw new IllegalStateException("A native failure cleanup is unresolved");
        if (LanguageNativeFailureAdversary.supports(action)) return LanguageNativeFailureAdversary.run(suite, action);
        if (!Arrays.asList("request", "settings", "native-aba", "isolation",
                "secondary-runtime", "secondary-linkage", "native-unavailable-runtime", "native-unavailable-linkage",
                "native-return-secondary-runtime", "native-return-secondary-linkage", "partial-warm",
                "partial-reorder-subset-style", "partial-uncached", "partial-no-matching").contains(action))
            throw new IllegalArgumentException("Unknown preparation action " + action);
        return suite.scenario(action);
    }

    static final class Suite {
        final Minecraft client = Minecraft.func_71410_x();
        final Class<?> adapter, hooks, config, admission;
        final String nonce, directory;
        final File configFile;
        final byte[] configBytes;
        final Language originalLanguage;
        final List<IResourcePack> packs;
        final List<IResourcePack> originalPacks;
        final List<Runnable> nativeListeners, ordinaryListeners;
        final FixturePack pack;
        final int localeKind, languageKind, resourceKind;
        volatile Case active;
        volatile LanguageNativeFailureAdversary activeNative;
        LanguageNativeFailureAdversary nativeCleanup;
        LanguageNativeFailureAdversary nativeAudit;
        boolean packAdded;
        int sequence;

        @SuppressWarnings("unchecked")
        Suite(JsonObject params) throws Exception {
            if (!LanguageStateProbe.active() || !Loader.isModLoaded("polyglottooltip")
                    || client.field_71439_g == null || client.field_71441_e == null)
                throw new IllegalStateException("Initialized product fixture required");
            nonce = System.getProperty("clientdevbridge.fixtureNonce", "");
            directory = client.field_71412_D.getCanonicalPath();
            if (nonce.isEmpty() || !params.has("fixtureNonce") || !nonce.equals(params.get("fixtureNonce").getAsString())
                    || !params.has("gameDirectory")
                    || !directory.equals(new File(params.get("gameDirectory").getAsString()).getCanonicalPath()))
                throw new IllegalStateException("Exact owned fixture nonce and game directory required");
            adapter = NativeAccess.type(BASE + "minecraft.MinecraftLanguageAdapter");
            hooks = NativeAccess.type(BASE + "bootstrap.NativeLanguageHooks");
            admission = NativeAccess.type(BASE + "bootstrap.NativeLanguageAdmission");
            config = NativeAccess.type(BASE + "Config");
            Class<?> state = NativeAccess.type(BASE + "bootstrap.NativeLanguageState");
            localeKind = ((Number) NativeAccess.get(state, "LOCALE")).intValue();
            languageKind = ((Number) NativeAccess.get(state, "LANGUAGE")).intValue();
            resourceKind = ((Number) NativeAccess.get(state, "RESOURCES")).intValue();
            nativeListeners = (List<Runnable>) NativeAccess.get(adapter, "nativeInvalidators");
            ordinaryListeners = (List<Runnable>) NativeAccess.get(adapter, "listeners");
            if (nativeListeners.getClass() != CopyOnWriteArrayList.class || ordinaryListeners.getClass() != CopyOnWriteArrayList.class)
                throw new IllegalStateException("Unknown adapter listener registry");
            Object value = NativeAccess.get(client, "field_110449_ao");
            if (value.getClass() != ArrayList.class) throw new IllegalStateException("Unknown default resource pack list");
            packs = (List<IResourcePack>) value;
            originalPacks = new ArrayList<>(packs);
            originalLanguage = client.func_135016_M().func_135041_c();
            if (originalLanguage == null || !ready()
                    || !originalLanguage.func_135034_a().equals(NativeAccess.get(client.func_135016_M(), "field_135048_c")))
                throw new IllegalStateException("Ready applied and selected language required");
            Object configuration = invoke(config, "configuration");
            if (!(configuration instanceof Configuration)) throw new IllegalStateException("Unknown owned configuration");
            configFile = ((Configuration) configuration).getConfigFile().getCanonicalFile();
            if (!configFile.toPath().startsWith(client.field_71412_D.getCanonicalFile().toPath()))
                throw new IllegalStateException("Product configuration is outside the owned fixture");
            configBytes = Files.readAllBytes(configFile.toPath());
            if (NativeAccess.get(SelectiveReloadStateHandler.INSTANCE, "currentPredicate") != null)
                throw new IllegalStateException("Scenario pack setup requires an initially empty raw selector");
            frameStack();
            pack = new FixturePack(this, "pt112_lifecycle_" + nonce.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_"));
        }

        void verifyOwnership() throws Exception {
            if (!client.func_152345_ab() || !nonce.equals(System.getProperty("clientdevbridge.fixtureNonce", ""))
                    || !directory.equals(client.field_71412_D.getCanonicalPath())
                    || NativeAccess.get(client, "field_110449_ao") != packs
                    || !configFile.equals(configFile.getCanonicalFile()))
                throw new IllegalStateException("Owned preparation fixture changed");
        }

        boolean ready() throws Exception { return (Boolean) invoke(invoke(adapter, "snapshot"), "ready"); }

        JsonObject status() throws Exception {
            Evidence evidence = new Evidence(this);
            JsonObject result = new JsonObject();
            result.addProperty("installed", packAdded);
            result.addProperty("fixtureNonce", nonce);
            result.addProperty("gameDirectory", directory);
            result.addProperty("domain", pack.domain);
            result.addProperty("witnessKey", pack.key);
            result.addProperty("active", active != null);
            result.addProperty("nativeActive", activeNative != null);
            result.addProperty("nativeCleanupBlocked", nativeCleanup != null);
            result.add("state", evidence.capture(true));
            return result;
        }

        JsonObject scenario(String action) throws Exception {
            if (active != null || activeNative != null) throw new IllegalStateException("Preparation scenario already active");
            if (!packAdded || !containsIdentity(packs, pack) || !ready()) throw new IllegalStateException("Ready installed fixture required");
            JsonObject savedSettings = settings();
            Language selected = client.func_135016_M().func_135041_c();
            Case test = new Case(this, action, ++sequence);
            JsonObject result = test.result;
            result.add("beforeSetup", test.evidence.capture(true));
            try {
                if (test.partial()) partial(test);
                else preparation(test);
            } catch (Throwable error) {
                test.operationFailure = unwrap(error);
                result.add("operationFailure", test.evidence.error(test.operationFailure));
            } finally {
                test.armed = false;
                test.nativeFaultArmed = false;
                test.closed = true;
                active = null;
                nativeListeners.remove(test.nativeListener);
                ordinaryListeners.remove(test.ordinaryListener);
                try {
                    client.func_135016_M().func_135045_a(selected);
                    CoreProbe.configure(savedSettings);
                    if (!ready()) client.func_135016_M().func_110549_a(client.func_110442_L());
                    result.add("recovery", test.evidence.capture(true));
                    test.check("recovery ready", ready());
                    test.check("recovery no hook frames", frameStack().isEmpty());
                } catch (Throwable cleanup) {
                    result.add("recoveryFailure", test.evidence.error(unwrap(cleanup)));
                    test.passed = false;
                }
            }
            result.add("events", test.events);
            result.add("checks", test.checks);
            result.addProperty("readStreams", test.readStreams);
            result.addProperty("secondaryOwners", test.secondaryOwners.size());
            result.addProperty("observedNativeLanguageScopes", test.languageScopes.size());
            result.addProperty("observedNativeResourceScopes", test.resourceScopes.size());
            result.addProperty("armFireCount", test.fires);
            result.addProperty("nestedNativeFaultCount", test.nativeFaults);
            result.addProperty("harnessLanguageCalls", test.harnessLanguageCalls);
            result.addProperty("harnessFullReloadCalls", test.harnessFullReloadCalls);
            result.addProperty("harnessSelectiveCalls", test.harnessSelectiveCalls);
            result.addProperty("testListenersRemoved", !containsIdentity(nativeListeners, test.nativeListener)
                    && !containsIdentity(ordinaryListeners, test.ordinaryListener));
            if (test.instrumentFailure != null) result.add("instrumentFailure", test.evidence.error(test.instrumentFailure));
            result.addProperty("passed", test.passed && test.operationFailure == null && test.instrumentFailure == null);
            return result;
        }

        void baseline(Case test, String... codes) throws Exception {
            CoreProbe.configure(languages(codes));
            Object source = invoke(hooks, "snapshot"), stamp = NativeAccess.get(source, "stamp");
            if (!ready() || stamp == null || number(source, "resourceGeneration") != number(stamp, "resourceGeneration"))
                client.func_135016_M().func_110549_a(client.func_110442_L());
            if (!ready()) throw new IllegalStateException("Secondary baseline not ready");
            test.baseline = invoke(adapter, "snapshot");
            test.sourceBefore = invoke(hooks, "snapshot");
            List<?> tables = (List<?>) NativeAccess.get(test.baseline, "tables");
            if (tables.size() != codes.length) throw new IllegalStateException("Unexpected baseline table count");
            for (int index = 0; index < codes.length; index++) {
                if (!codes[index].equals(NativeAccess.get(tables.get(index), "code")) || !test.fixtureWitness(test.baseline, codes[index]))
                    throw new IllegalStateException("Fixture baseline table was not naturally loaded");
            }
            Object appliedStamp = NativeAccess.get(test.sourceBefore, "stamp");
            if (appliedStamp == null || number(test.sourceBefore, "resourceGeneration") != number(appliedStamp, "resourceGeneration"))
                throw new IllegalStateException("Baseline manager generation is not applied");
            test.beforeMaps = test.evidence.nativeMaps();
            test.result.add("setup", test.evidence.capture(true));
            active = test;
            test.register();
        }

        void preparation(Case test) throws Exception {
            if (test.nativeReturnFailure() && NativeAccess.get(SelectiveReloadStateHandler.INSTANCE, "currentPredicate") != null)
                throw new IllegalStateException("Requested full reload requires an initially empty raw selector");
            if (test.nativeReturnFailure()) baseline(test, FIRST);
            else baseline(test);
            test.armed = true;
            if (test.nativeReturnFailure()) {
                test.harnessFullReloadCalls++;
                client.func_110436_a();
                test.result.addProperty("nativeOperationReturned", true);
            } else CoreProbe.configure(languages(FIRST));
            test.armed = false;
            test.nativeFaultArmed = false;
            test.result.add("returned", test.evidence.capture(true));
            test.result.addProperty("inlineEventCount", test.events.size());
            test.assertReturned();
        }

        void partial(Case test) throws Exception {
            if (NativeAccess.get(SelectiveReloadStateHandler.INSTANCE, "currentPredicate") != null)
                throw new IllegalStateException("Partial scenario requires an initially empty raw selector");
            boolean noMatching = "partial-no-matching".equals(test.action);
            if (noMatching) baseline(test);
            else baseline(test, FIRST, SECOND);
            test.result.addProperty("processColdClaim", false);
            List<?> baselineTables = new ArrayList<>((List<?>) NativeAccess.get(test.baseline, "tables"));
            Object oldStamp = NativeAccess.get(test.sourceBefore, "stamp");
            test.result.add("selectiveOperation", selectiveTextures(test));
            Object partialSource = invoke(hooks, "snapshot"), partialStamp = NativeAccess.get(partialSource, "stamp");
            Object partialSnapshot = invoke(adapter, "snapshot");
            test.result.add("partialReturned", test.checkpoint());
            test.check("partial native source ready", "READY".equals(((Enum<?>) NativeAccess.get(partialSource, "phase")).name()));
            test.check("partial native provenance preserved", (Boolean) NativeAccess.get(partialSource, "preserved"));
            test.check("one resource generation completed", number(partialSource, "resourceGeneration") == number(test.sourceBefore, "resourceGeneration") + 1);
            test.check("partial stamp retained applied resource generation", partialStamp != null
                    && number(partialStamp, "resourceGeneration") == number(oldStamp, "resourceGeneration"));
            test.check("partial manager is newer than language stamp", partialStamp != null
                    && number(partialStamp, "resourceGeneration") < number(partialSource, "resourceGeneration"));
            test.check("partial kept manager identity", partialStamp != null
                    && NativeAccess.get(partialStamp, "resourceManager") == NativeAccess.get(oldStamp, "resourceManager"));
            test.check("partial did not commit another LM apply", partialStamp != null
                    && number(partialStamp, "applySequence") == number(oldStamp, "applySequence"));
            test.check("no LANGUAGE scope observed during partial fixture reads", test.languageScopes.isEmpty());
            test.check("partial performed no fixture language reads", test.readStreams == 0);
            test.check("partial performed no secondary Locale reads", test.secondaryOwners.isEmpty());
            test.check("partial tables retained by identity", sameIdentities((List<?>) NativeAccess.get(partialSnapshot, "tables"), baselineTables));
            test.check("partial product ready", ready());
            test.check("partial hook frames drained", threadEntry(NativeAccess.get(hooks, "frames")) == null);
            test.check("partial native scopes drained", number(partialSource, "active") == 0);
            if (!test.passed) throw new IllegalStateException("The TEXTURES operation did not establish the required no-LM partial state");

            JsonArray steps = new JsonArray();
            test.result.add("steps", steps);
            if ("partial-reorder-subset-style".equals(test.action)) {
                CoreProbe.configure(languages(SECOND, FIRST));
                partialStep(test, steps, "reorder", partialSource, Arrays.asList(baselineTables.get(1), baselineTables.get(0)));
                CoreProbe.configure(languages(SECOND));
                partialStep(test, steps, "subset", partialSource, Collections.singletonList(baselineTables.get(1)));
                Object currentSettings = invoke(config, "get");
                String targetStyle = "\u00a7a".equals(NativeAccess.get(currentSettings, "tooltipFormat")) ? "&b" : "&a";
                JsonObject style = new JsonObject();
                style.addProperty("tooltipSecondaryNameFormat", targetStyle);
                CoreProbe.configure(style);
                partialStep(test, steps, "style", partialSource, Collections.singletonList(baselineTables.get(1)));
                test.check("style setting changed", targetStyle.replace('&', '\u00a7').equals(NativeAccess.get(invoke(config, "get"), "tooltipFormat")));
            } else if (noMatching || "partial-uncached".equals(test.action)) {
                String missing = noMatching ? FIRST : THIRD;
                if (noMatching) CoreProbe.configure(languages(FIRST));
                else CoreProbe.configure(languages(FIRST, SECOND, THIRD));
                Object blocked = invoke(adapter, "snapshot");
                test.result.add("blocked", test.checkpoint());
                test.result.addProperty("blockedLanguage", missing);
                test.check("uncached request did not publish", blocked == partialSnapshot);
                test.check("uncached request is ineligible", !ready());
                test.check("uncached request remains pending", number(blocked, "settingsRequest") < test.evidence.requests());
                test.check("uncached request kept native source", invoke(hooks, "snapshot") == partialSource);
                test.check("uncached request performed no fixture reads", test.readStreams == 0);
                test.check("uncached request performed no secondary reads", test.secondaryOwners.isEmpty());
                test.check("uncached table not fabricated", !hasTable(blocked, missing));
                if (noMatching) test.check("no prior matching tables", baselineTables.isEmpty());
                test.harnessLanguageCalls++;
                client.func_135016_M().func_110549_a(client.func_110442_L());
                Object applied = invoke(hooks, "snapshot"), appliedStamp = NativeAccess.get(applied, "stamp");
                Object synchronizedSnapshot = invoke(adapter, "snapshot");
                test.result.add("nativeRecovery", test.checkpoint());
                test.check("natural LM recovery ready", ready());
                test.check("natural LM applied the newer manager generation", appliedStamp != null
                        && number(appliedStamp, "resourceGeneration") == number(partialSource, "resourceGeneration")
                        && number(applied, "resourceGeneration") == number(partialSource, "resourceGeneration"));
                test.check("natural LM recovery committed one apply", appliedStamp != null
                        && number(appliedStamp, "applySequence") == number(partialStamp, "applySequence") + 1);
                test.check("recovered table has fixture witness", test.fixtureWitness(synchronizedSnapshot, missing));
                test.check("recovery request caught up", number(synchronizedSnapshot, "settingsRequest") == test.evidence.requests());
                test.check("recovery hook frames drained", threadEntry(NativeAccess.get(hooks, "frames")) == null);
            }
            test.result.add("returned", test.evidence.capture(true));
            test.result.addProperty("inlineEventCount", test.events.size());
        }

        void partialStep(Case test, JsonArray steps, String name, Object partialSource, List<?> expected) throws Exception {
            JsonObject value = test.checkpoint();
            value.addProperty("step", name);
            steps.add(value);
            Object snapshot = invoke(adapter, "snapshot");
            test.check(name + " product ready", ready());
            test.check(name + " kept native source", invoke(hooks, "snapshot") == partialSource);
            test.check(name + " retained exact table identities and order", sameIdentities((List<?>) NativeAccess.get(snapshot, "tables"), expected));
            test.check(name + " did not read newer language resources", test.readStreams == 0 && test.secondaryOwners.isEmpty());
        }

        JsonObject selectiveTextures(Case test) throws Exception {
            if (NativeAccess.get(SelectiveReloadStateHandler.INSTANCE, "currentPredicate") != null)
                throw new IllegalStateException("A resource selector is already active");
            boolean previous = ForgeModContainer.selectiveResourceReloadEnabled;
            Predicate<IResourceType> selector = ReloadRequirements.include(VanillaResourceType.TEXTURES);
            JsonObject result = new JsonObject();
            result.addProperty("initialRawSelectorNull", true);
            result.addProperty("previousSelectiveFlag", previous);
            result.addProperty("selectorIdentity", test.evidence.identity(selector));
            Throwable failure = null;
            ForgeModContainer.selectiveResourceReloadEnabled = true;
            try {
                test.harnessSelectiveCalls++;
                FMLClientHandler.instance().refreshResources(selector);
                result.addProperty("nativeOperationReturned", true);
            } catch (Throwable error) { failure = unwrap(error); }
            finally {
                try {
                    Object remaining = NativeAccess.get(SelectiveReloadStateHandler.INSTANCE, "currentPredicate");
                    result.addProperty("rawSelectorAtReturnIdentity", test.evidence.identity(remaining));
                    if (remaining == selector) SelectiveReloadStateHandler.INSTANCE.endReload();
                    else if (remaining != null) throw new IllegalStateException("Another owner replaced the partial selector");
                } catch (Throwable error) {
                    if (failure == null) failure = unwrap(error);
                    else failure.addSuppressed(unwrap(error));
                } finally { ForgeModContainer.selectiveResourceReloadEnabled = previous; }
            }
            result.addProperty("selectiveFlagRestored", ForgeModContainer.selectiveResourceReloadEnabled == previous);
            result.addProperty("rawSelectorRestored", NativeAccess.get(SelectiveReloadStateHandler.INSTANCE, "currentPredicate") == null);
            test.result.add("selectiveOperation", result);
            if (failure != null) { result.add("failure", test.evidence.error(failure)); rethrow(failure); }
            test.check("selective flag restored", ForgeModContainer.selectiveResourceReloadEnabled == previous);
            test.check("raw selector restored", NativeAccess.get(SelectiveReloadStateHandler.INSTANCE, "currentPredicate") == null);
            return result;
        }

        JsonObject uninstall() throws Exception {
            verifyOwnership();
            JsonObject result = new JsonObject();
            result.addProperty("action", "uninstall");
            if (nativeCleanup != null) {
                JsonObject cleanup = nativeCleanup.cleanupForTeardown();
                result.add("nativeFailureCleanup", cleanup);
                if (!cleanup.get("passed").getAsBoolean()) {
                    result.addProperty("passed", false);
                    result.addProperty("ownPackRemoved", false);
                    result.addProperty("otherPackOrderUnchanged", false);
                    result.addProperty("configBytesRestored", false);
                    result.add("state", new Evidence(this).capture(true));
                    return result;
                }
                nativeCleanup = null;
            }
            if (active != null) {
                active.closed = true;
                active.armed = false;
                active.nativeFaultArmed = false;
                nativeListeners.remove(active.nativeListener);
                ordinaryListeners.remove(active.ordinaryListener);
                active = null;
            }
            pack.enabled = false;
            for (int index = packs.size() - 1; index >= 0; index--) if (packs.get(index) == pack) packs.remove(index);
            packAdded = false;
            Throwable failure = null;
            try {
                Files.write(configFile.toPath(), configBytes);
                invoke(config, "reload");
            } catch (Throwable error) { failure = unwrap(error); }
            try {
                client.func_135016_M().func_135045_a(originalLanguage);
                client.func_110436_a();
            } catch (Throwable error) {
                if (failure == null) failure = unwrap(error);
                else failure.addSuppressed(unwrap(error));
            }
            Evidence evidence = new Evidence(this);
            result.addProperty("ownPackRemoved", !containsIdentity(packs, pack));
            result.addProperty("otherPackOrderUnchanged", sameIdentities(packs, originalPacks));
            result.addProperty("configBytesRestored", Arrays.equals(configBytes, Files.readAllBytes(configFile.toPath())));
            result.add("state", evidence.capture(true));
            if (failure != null) { result.add("failure", evidence.error(failure)); result.addProperty("passed", false); }
            else result.addProperty("passed", ready() && frameStack().isEmpty() && sameIdentities(packs, originalPacks)
                    && Arrays.equals(configBytes, Files.readAllBytes(configFile.toPath())));
            if (nativeAudit != null) {
                try {
                    JsonObject audit = nativeAudit.auditTeardown();
                    result.add("nativeFailureFinalAudit", audit);
                    if (!audit.get("passed").getAsBoolean()) result.addProperty("passed", false);
                } catch (Throwable error) {
                    result.add("nativeFailureFinalAuditFailure", evidence.error(unwrap(error)));
                    result.addProperty("passed", false);
                }
            }
            return result;
        }

        JsonObject settings() throws Exception {
            Object settings = invoke(config, "get");
            JsonObject result = new JsonObject();
            JsonArray languages = new JsonArray();
            for (Object language : (List<?>) NativeAccess.get(settings, "languages")) languages.add((String) language);
            result.add("languages", languages);
            result.addProperty("alwaysShow", (Boolean) NativeAccess.get(settings, "alwaysShow"));
            result.addProperty("enableChineseScriptMatching", (Boolean) NativeAccess.get(settings, "chineseScript"));
            result.addProperty("tooltipSecondaryNameFormat", (String) NativeAccess.get(settings, "tooltipFormat"));
            result.addProperty("hudSecondaryNameFormat", (String) NativeAccess.get(settings, "hudFormat"));
            return result;
        }

        List<Object> frameStack() throws Exception {
            Object entry = threadEntry(NativeAccess.get(hooks, "frames"));
            if (entry == null) return Collections.emptyList();
            Object value = NativeAccess.get(entry, "value");
            if (value == null || value.getClass() != ArrayDeque.class) throw new IllegalStateException("Unknown hook frame stack");
            return new ArrayList<>((Deque<?>) value);
        }
    }

    private static final class Case {
        final Suite suite;
        final String action;
        final Evidence evidence;
        final JsonObject result = new JsonObject();
        final JsonArray events = new JsonArray(), checks = new JsonArray();
        final IdentityHashMap<Object, Boolean> secondaryOwners = new IdentityHashMap<>(), languageScopes = new IdentityHashMap<>(), resourceScopes = new IdentityHashMap<>();
        final Runnable nativeListener, ordinaryListener;
        final Throwable sentinel;
        boolean armed, nativeFaultArmed, closed, passed = true;
        int fires, readStreams, nativeFaults, harnessLanguageCalls, harnessFullReloadCalls, harnessSelectiveCalls;
        Object baseline, sourceBefore, sourceAtRead, settingsAtRead, sourceAfterAction, settingsAfterAction;
        JsonObject beforeMaps;
        long requestAtRead, requestAfterAction;
        volatile Throwable instrumentFailure;
        Throwable operationFailure, caughtNativeFailure;

        Case(Suite suite, String action, int sequence) {
            this.suite = suite;
            this.action = action;
            evidence = new Evidence(suite);
            result.addProperty("schema", 1);
            result.addProperty("action", action);
            result.addProperty("sequence", sequence);
            result.addProperty("fixtureNonce", suite.nonce);
            result.addProperty("gameDirectory", suite.directory);
            result.addProperty("scope", "synchronous secondary preparation; recovery recorded separately");
            result.addProperty("counterMeaning", "readStreams counts only first reads of fixture streams; observed scopes are unique admitted scopes visible at those reads, not total native invocations");
            sentinel = action.endsWith("linkage") ? new LinkageError("fixture language linkage")
                    : new IllegalStateException("fixture language runtime");
            nativeListener = () -> callback("native-notification");
            ordinaryListener = () -> callback("general-notification");
        }

        void register() throws Exception {
            invoke(suite.adapter, "onNativeReload", nativeListener);
            invoke(suite.adapter, "onReload", ordinaryListener);
        }

        boolean partial() { return action.startsWith("partial-"); }
        boolean unavailable() { return action.startsWith("native-unavailable-"); }
        boolean nativeReturnFailure() { return action.startsWith("native-return-secondary-"); }
        boolean secondaryFailure() { return action.startsWith("secondary-") || nativeReturnFailure(); }

        void callback(String event) {
            if (closed || suite.active != this) return;
            try { record(event, null); }
            catch (Throwable error) { failed(error); }
        }

        void read(String path) {
            if (closed || suite.active != this) return;
            if (!suite.client.func_152345_ab()) {
                failed(new IllegalStateException("Fixture language stream read outside the client thread"));
                return;
            }
            try {
                readStreams++;
                List<Object> frames = suite.frameStack();
                for (Object frame : frames) {
                    int kind = ((Number) NativeAccess.get(frame, "kind")).intValue();
                    Object scope = NativeAccess.get(frame, "scope");
                    if (scope != null && kind == suite.languageKind) languageScopes.put(scope, Boolean.TRUE);
                    if (scope != null && kind == suite.resourceKind) resourceScopes.put(scope, Boolean.TRUE);
                }
                Object top = frames.isEmpty() ? null : frames.get(0);
                boolean secondary = top != null && ((Number) NativeAccess.get(top, "kind")).intValue() == suite.localeKind
                        && (Boolean) NativeAccess.get(top, "ignored") && NativeAccess.get(top, "scope") == null
                        && NativeAccess.get(top, "owner") != NativeAccess.get(suite.hooks, "activeLocale");
                if (secondary) secondaryOwners.put(NativeAccess.get(top, "owner"), Boolean.TRUE);
                record(secondary ? "secondary-read" : "native-read", path);
                if (nativeFaultArmed && top != null && ((Number) NativeAccess.get(top, "kind")).intValue() == suite.localeKind
                        && !(Boolean) NativeAccess.get(top, "ignored") && NativeAccess.get(top, "scope") != null
                        && NativeAccess.get(top, "owner") == NativeAccess.get(suite.hooks, "activeLocale")
                        && (Boolean) NativeAccess.get(suite.adapter, "preparing")) {
                    nativeFaultArmed = false;
                    nativeFaults++;
                    result.add("nestedNativeSentinel", evidence.error(sentinel));
                    record("nested-native-throw", path);
                    if (sentinel instanceof LinkageError) throw (LinkageError) sentinel;
                    throw (RuntimeException) sentinel;
                }
                if (!armed || !secondary || !(Boolean) NativeAccess.get(suite.adapter, "preparing")) return;
                suite.verifyOwnership();
                armed = false;
                fires++;
                sourceAtRead = invoke(suite.hooks, "snapshot");
                settingsAtRead = invoke(suite.config, "get");
                requestAtRead = evidence.requests();
                record("armed-read-before", path);
                if ("request".equals(action)) invoke(suite.adapter, "requestReload");
                else if ("settings".equals(action)) CoreProbe.configure(languages(SECOND));
                else if ("native-aba".equals(action)) {
                    Language selected = suite.client.func_135016_M().func_135041_c();
                    Language other = suite.client.func_135016_M().func_191960_a("en_us".equals(selected.func_135034_a()) ? "zh_cn" : "en_us");
                    if (other == null) throw new IllegalStateException("Alternate native language missing");
                    try {
                        suite.client.func_135016_M().func_135045_a(other);
                        harnessLanguageCalls++;
                        suite.client.func_135016_M().func_110549_a(suite.client.func_110442_L());
                        record("nested-language-B-returned", path);
                    } finally { suite.client.func_135016_M().func_135045_a(selected); }
                    harnessLanguageCalls++;
                    suite.client.func_135016_M().func_110549_a(suite.client.func_110442_L());
                    record("nested-language-A-returned", path);
                } else if (unavailable()) {
                    nativeFaultArmed = true;
                    try {
                        harnessLanguageCalls++;
                        suite.client.func_135016_M().func_110549_a(suite.client.func_110442_L());
                    } catch (Throwable error) {
                        caughtNativeFailure = unwrap(error);
                        result.add("caughtNativeFailure", evidence.error(caughtNativeFailure));
                    } finally { nativeFaultArmed = false; }
                    record("nested-native-caught", path);
                }
                sourceAfterAction = invoke(suite.hooks, "snapshot");
                settingsAfterAction = invoke(suite.config, "get");
                requestAfterAction = evidence.requests();
                record("armed-read-after", path);
                if (secondaryFailure()) {
                    result.add("thrownSentinel", evidence.error(sentinel));
                    if (sentinel instanceof LinkageError) throw (LinkageError) sentinel;
                    throw (RuntimeException) sentinel;
                }
            } catch (Throwable error) {
                if (error == sentinel) {
                    if (error instanceof LinkageError) throw (LinkageError) error;
                    throw (RuntimeException) error;
                }
                failed(error);
                throw new IllegalStateException("Preparation probe observation failed", error);
            }
        }

        void assertReturned() throws Exception {
            check("exactly one armed secondary read", fires == 1);
            check("secondary owner observed", !secondaryOwners.isEmpty());
            check("preparing cleared at return", !(Boolean) NativeAccess.get(suite.adapter, "preparing"));
            check("hook frame entry removed at return", threadEntry(NativeAccess.get(suite.hooks, "frames")) == null);
            Object source = invoke(suite.hooks, "snapshot"), snapshot = invoke(suite.adapter, "snapshot");
            check("native scopes drained", ((Number) NativeAccess.get(source, "active")).intValue() == 0);
            check("native scope stack empty", ((Deque<?>) NativeAccess.get(NativeAccess.get(suite.hooks, "state"), "scopes")).isEmpty());
            check("old product snapshot ineligible", !(Boolean) invoke(baseline, "ready"));
            boolean failure = secondaryFailure() || unavailable();
            check("returned eligibility", suite.ready() != failure);
            if (failure) {
                check("failed draft not published", snapshot == baseline);
                if (nativeReturnFailure()) check("new native tuple remains pending", NativeAccess.get(snapshot, "nativeState") != source);
                else check("failed request remains pending", number(snapshot, "settingsRequest") < evidence.requests());
            } else {
                check("current uses latest native source", NativeAccess.get(snapshot, "nativeState") == source);
                check("current uses latest settings", NativeAccess.get(snapshot, "settings") == invoke(suite.config, "get"));
                check("current request caught up", ((Number) NativeAccess.get(snapshot, "settingsRequest")).longValue() == evidence.requests());
                check("fixture secondary witness committed", fixtureWitness(snapshot, "settings".equals(action) ? SECOND : FIRST));
            }
            if ("request".equals(action)) {
                check("request changed once within preparation", requestAfterAction == requestAtRead + 1);
                check("request kept source and settings", sourceAfterAction == sourceAtRead && settingsAfterAction == settingsAtRead);
            }
            if ("settings".equals(action)) {
                check("settings identity changed within preparation", settingsAfterAction != settingsAtRead);
                check("settings action kept native source", sourceAfterAction == sourceAtRead);
                check("settings request incremented once", requestAfterAction == requestAtRead + 1);
                check("secondary B table committed", hasTable(snapshot, SECOND));
            }
            if ("native-aba".equals(action)) {
                check("ABA native source identity changed", sourceAfterAction != sourceAtRead);
                check("ABA final source committed", NativeAccess.get(snapshot, "nativeState") == sourceAfterAction);
                check("two native LM scopes observed", languageScopes.size() == 2);
                check("stale secondary discarded and reloaded", secondaryOwners.size() >= 2);
                if (sourceAtRead != null && sourceAfterAction != null) {
                    Object beforeStamp = NativeAccess.get(sourceAtRead, "stamp"), afterStamp = NativeAccess.get(sourceAfterAction, "stamp");
                    check("ABA returned to same language text", NativeAccess.get(beforeStamp, "language").equals(NativeAccess.get(afterStamp, "language")));
                    check("ABA applied exactly two language callbacks", ((Number) NativeAccess.get(afterStamp, "applySequence")).longValue()
                            == ((Number) NativeAccess.get(beforeStamp, "applySequence")).longValue() + 2);
                }
            } else if (unavailable()) {
                check("nested active Locale threw exactly once", nativeFaults == 1);
                check("native Throwable identity preserved", caughtNativeFailure == sentinel);
                check("nested native source is unavailable", "UNAVAILABLE".equals(((Enum<?>) NativeAccess.get(source, "phase")).name()));
                check("unavailable source differs from captured draft", source != sourceAtRead);
                check("outer prepare kept the unavailable source", source == sourceAfterAction);
                check("one nested LANGUAGE scope observed", languageScopes.size() == 1);
                check("outer draft did not notify a publication", !hasEvent("general-notification") && !hasEvent("native-notification"));
            } else if (nativeReturnFailure()) {
                Object stamp = NativeAccess.get(source, "stamp"), oldStamp = NativeAccess.get(sourceBefore, "stamp");
                check("requested native operation remained ready", "READY".equals(((Enum<?>) NativeAccess.get(source, "phase")).name()));
                check("one requested resource generation, no fallback generation", number(source, "resourceGeneration") == number(sourceBefore, "resourceGeneration") + 1);
                check("requested native apply advanced once", stamp != null && number(stamp, "applySequence") == number(oldStamp, "applySequence") + 1);
                check("requested stamp matches new resource generation", stamp != null && number(stamp, "resourceGeneration") == number(source, "resourceGeneration"));
                check("one LANGUAGE scope observed during requested native read", languageScopes.size() == 1);
                check("one RESOURCES scope observed during requested native read", resourceScopes.size() == 1);
                check("secondary failure did not publish a core snapshot", !hasEvent("general-notification") && !hasEvent("native-notification"));
            } else {
                check("secondary load kept native source identity", source == sourceBefore);
                check("secondary load kept active native maps", beforeMaps.equals(evidence.nativeMaps()));
                check("secondary load did not enter native LM", languageScopes.isEmpty());
            }
        }

        void record(String event, String path) throws Exception {
            JsonObject value = evidence.capture(false);
            value.addProperty("event", event);
            value.addProperty("eventIndex", events.size());
            if (path != null) value.addProperty("resourcePath", path);
            events.add(value);
        }

        void check(String name, boolean condition) {
            JsonObject check = new JsonObject();
            check.addProperty("name", name);
            check.addProperty("pass", condition);
            checks.add(check);
            passed &= condition;
        }

        void failed(Throwable failure) { if (instrumentFailure == null) instrumentFailure = unwrap(failure); passed = false; }

        boolean hasEvent(String event) {
            for (int index = 0; index < events.size(); index++)
                if (event.equals(events.get(index).getAsJsonObject().get("event").getAsString())) return true;
            return false;
        }

        JsonObject checkpoint() throws Exception {
            JsonObject result = new JsonObject();
            result.addProperty("eventIndex", events.size());
            result.addProperty("readStreams", readStreams);
            result.addProperty("secondaryOwners", secondaryOwners.size());
            result.addProperty("observedNativeLanguageScopes", languageScopes.size());
            result.addProperty("observedNativeResourceScopes", resourceScopes.size());
            result.add("state", evidence.capture(true));
            return result;
        }

        boolean fixtureWitness(Object snapshot, String code) throws Exception {
            for (Object table : (List<?>) NativeAccess.get(snapshot, "tables")) {
                if (!code.equals(NativeAccess.get(table, "code"))) continue;
                Object value = NativeAccess.get(table, "entries");
                if (value.getClass() != Collections.unmodifiableMap(new LinkedHashMap<>()).getClass())
                    throw new IllegalStateException("Unknown immutable product table");
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    if (suite.pack.key.equals(entry.getKey()))
                        return (THIRD.equals(code) ? "Fixture C" : SECOND.equals(code) ? "Fixture B" : "Fixture A").equals(entry.getValue());
                }
            }
            return false;
        }
    }

    static final class Evidence {
        final Suite suite;
        final IdentityHashMap<Object, Integer> identities = new IdentityHashMap<>();
        Evidence(Suite suite) { this.suite = suite; }

        int identity(Object value) {
            if (value == null) return 0;
            Integer number = identities.get(value);
            if (number == null) { number = identities.size() + 1; identities.put(value, number); }
            return number;
        }

        long requests() throws Exception { return ((AtomicLong) NativeAccess.get(suite.adapter, "requests")).get(); }

        JsonObject capture(boolean maps) throws Exception {
            Object current = invoke(suite.adapter, "snapshot"), source = invoke(suite.hooks, "snapshot");
            Object settings = invoke(suite.config, "get"), stamp = NativeAccess.get(source, "stamp");
            JsonObject result = new JsonObject();
            result.addProperty("snapshotIdentity", identity(current));
            result.addProperty("sourceIdentity", identity(source));
            result.addProperty("snapshotNativeStateIdentity", identity(NativeAccess.get(current, "nativeState")));
            result.addProperty("settingsIdentity", identity(settings));
            result.addProperty("snapshotSettingsIdentity", identity(NativeAccess.get(current, "settings")));
            result.addProperty("requests", requests());
            for (String field : new String[]{"generation", "nativeNameEpoch", "settingsRequest"})
                result.addProperty(field, (Number) NativeAccess.get(current, field));
            result.addProperty("ready", (Boolean) invoke(current, "ready"));
            result.addProperty("enabled", (Boolean) invoke(current, "enabled"));
            result.addProperty("preparing", (Boolean) NativeAccess.get(suite.adapter, "preparing"));
            result.addProperty("phase", ((Enum<?>) NativeAccess.get(source, "phase")).name());
            result.addProperty("preserved", (Boolean) NativeAccess.get(source, "preserved"));
            result.addProperty("reason", (String) NativeAccess.get(source, "reason"));
            result.addProperty("selectedLanguage", (String) NativeAccess.get(suite.client.func_135016_M(), "field_135048_c"));
            result.addProperty("bindingsCurrent", (Boolean) invoke(suite.hooks, "bindingsCurrent", stamp));
            result.addProperty("admissionReady", (Boolean) invoke(suite.admission, "ready"));
            result.addProperty("selectiveResourceReloadEnabled", ForgeModContainer.selectiveResourceReloadEnabled);
            result.addProperty("rawSelectorIdentity", identity(NativeAccess.get(SelectiveReloadStateHandler.INSTANCE, "currentPredicate")));
            for (String field : new String[]{"sequence", "resourceGeneration", "invalidationSequence", "active"})
                result.addProperty(field, (Number) NativeAccess.get(source, field));
            if (stamp != null) {
                JsonObject receipt = new JsonObject();
                receipt.addProperty("identity", identity(stamp));
                for (String field : new String[]{"resourceGeneration", "applySequence", "clientRevision", "legacyRevision"})
                    receipt.addProperty(field, (Number) NativeAccess.get(stamp, field));
                for (String field : new String[]{"languageManager", "resourceManager", "locale", "legacy"})
                    receipt.addProperty(field + "Identity", identity(NativeAccess.get(stamp, field)));
                receipt.addProperty("language", (String) NativeAccess.get(stamp, "language"));
                result.add("stamp", receipt);
            }
            JsonArray frames = new JsonArray();
            for (Object frame : suite.frameStack()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("identity", identity(frame));
                entry.addProperty("kind", (Number) NativeAccess.get(frame, "kind"));
                entry.addProperty("ignored", (Boolean) NativeAccess.get(frame, "ignored"));
                entry.addProperty("ownerIdentity", identity(NativeAccess.get(frame, "owner")));
                entry.addProperty("scopeIdentity", identity(NativeAccess.get(frame, "scope")));
                frames.add(entry);
            }
            result.add("hookFrames", frames);
            result.addProperty("hookFrameEntryPresent", threadEntry(NativeAccess.get(suite.hooks, "frames")) != null);
            Object state = NativeAccess.get(suite.hooks, "state");
            Object scopes = NativeAccess.get(state, "scopes");
            if (scopes.getClass() != ArrayDeque.class) throw new IllegalStateException("Unknown native scope stack");
            result.addProperty("nativeScopeDepth", ((Deque<?>) scopes).size());
            JsonArray tables = new JsonArray();
            Object tableSource = NativeAccess.get(current, "nativeState");
            Object tableStamp = tableSource == null ? null : NativeAccess.get(tableSource, "stamp");
            for (Object table : (List<?>) NativeAccess.get(current, "tables")) {
                JsonObject entry = new JsonObject();
                entry.addProperty("identity", identity(table));
                entry.addProperty("code", (String) NativeAccess.get(table, "code"));
                entry.addProperty("entriesIdentity", identity(NativeAccess.get(table, "entries")));
                entry.addProperty("appliedResourceGeneration", tableStamp == null ? -1 : number(tableStamp, "resourceGeneration"));
                tables.add(entry);
            }
            result.add("tables", tables);
            if (maps) result.add("nativeMaps", nativeMaps());
            return result;
        }

        JsonObject nativeMaps() throws Exception {
            JsonObject result = new JsonObject();
            Object locale = NativeAccess.get(suite.hooks, "activeLocale"), legacy = NativeAccess.get(suite.hooks, "activeLegacy");
            result.addProperty("localeIdentity", identity(locale));
            result.addProperty("legacyIdentity", identity(legacy));
            result.addProperty("selectedLanguage", (String) NativeAccess.get(suite.client.func_135016_M(), "field_135048_c"));
            result.add("client", map(NativeAccess.get(locale, "field_135032_a")));
            result.add("legacy", map(NativeAccess.get(legacy, "field_74816_c")));
            return result;
        }

        JsonObject map(Object value) throws Exception {
            if (value == null || value.getClass() != HashMap.class && value.getClass() != LinkedHashMap.class)
                throw new IllegalStateException("Unknown exact native translation map");
            Map<?, ?> source = (Map<?, ?>) value;
            if (source.size() > 1048576) throw new IllegalStateException("Native translation map too large");
            TreeMap<String, String> entries = new TreeMap<>();
            long characters = 0;
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String))
                    throw new IllegalStateException("Non-string native translation entry");
                String key = (String) entry.getKey(), text = (String) entry.getValue();
                characters += (long) key.length() + text.length();
                if (characters > 134217728) throw new IllegalStateException("Native translation text too large");
                entries.put(key, text);
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                digestText(digest, entry.getKey());
                digestText(digest, entry.getValue());
            }
            JsonObject result = new JsonObject();
            result.addProperty("identity", identity(value));
            result.addProperty("size", entries.size());
            result.addProperty("sha256", hex(digest.digest()));
            result.addProperty("fixtureWitness", entries.get(suite.pack.key));
            return result;
        }

        JsonObject error(Throwable error) {
            JsonObject result = new JsonObject();
            result.addProperty("identity", identity(error));
            result.addProperty("type", error.getClass().getName());
            result.addProperty("message", error.getMessage());
            return result;
        }
    }

    static final class FixturePack implements IResourcePack {
        final Suite suite;
        final String domain, key;
        final Map<String, byte[]> resources;
        boolean enabled = true;

        FixturePack(Suite suite, String domain) {
            this.suite = suite;
            this.domain = domain;
            key = domain + ".witness";
            Map<String, byte[]> files = new HashMap<>();
            files.put("lang/en_us.lang", (key + "=Fixture base\n").getBytes(StandardCharsets.UTF_8));
            files.put("lang/" + FIRST + ".lang", (key + "=Fixture A\n").getBytes(StandardCharsets.UTF_8));
            files.put("lang/" + SECOND + ".lang", (key + "=Fixture B\n").getBytes(StandardCharsets.UTF_8));
            files.put("lang/" + THIRD + ".lang", (key + "=Fixture C\n").getBytes(StandardCharsets.UTF_8));
            resources = Collections.unmodifiableMap(files);
        }

        @Override public InputStream func_110590_a(ResourceLocation location) throws IOException {
            if (!func_110589_b(location)) throw new FileNotFoundException(location.toString());
            String path = location.func_110623_a();
            return new ByteArrayInputStream(resources.get(path)) {
                private boolean observed;
                private void observe() {
                    if (observed) return;
                    observed = true;
                    Case test = suite.active;
                    if (enabled && test != null) test.read(path);
                    LanguageNativeFailureAdversary nativeTest = suite.activeNative;
                    if (enabled && nativeTest != null) nativeTest.read(path);
                }
                @Override public synchronized int read() { observe(); return super.read(); }
                @Override public synchronized int read(byte[] bytes, int offset, int length) {
                    if (length > 0) observe();
                    return super.read(bytes, offset, length);
                }
            };
        }
        @Override public boolean func_110589_b(ResourceLocation location) {
            return domain.equals(location.func_110624_b()) && resources.containsKey(location.func_110623_a());
        }
        @Override public Set<String> func_110587_b() {
            LanguageNativeFailureAdversary test = suite.activeNative;
            if (enabled && test != null) test.domains(this);
            return Collections.singleton(domain);
        }
        @Override public <T extends IMetadataSection> T func_135058_a(MetadataSerializer serializer, String section) { return null; }
        @Override public BufferedImage func_110586_a() { return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB); }
        @Override public String func_130077_b() { return "Owned lifecycle preparation fixture " + domain; }
    }

    private static JsonObject languages(String... codes) {
        JsonObject result = new JsonObject();
        JsonArray values = new JsonArray();
        for (String code : codes) values.add(code);
        result.add("languages", values);
        return result;
    }

    private static boolean hasTable(Object snapshot, String code) throws Exception {
        for (Object table : (List<?>) NativeAccess.get(snapshot, "tables"))
            if (code.equals(NativeAccess.get(table, "code"))) return true;
        return false;
    }

    private static long number(Object owner, String field) throws Exception {
        return ((Number) NativeAccess.get(owner, field)).longValue();
    }

    private static Object threadEntry(Object key) throws Exception {
        Object map = NativeAccess.get(Thread.currentThread(), "threadLocals");
        if (map == null) return null;
        if (!map.getClass().getName().equals("java.lang.ThreadLocal$ThreadLocalMap"))
            throw new IllegalStateException("Unknown thread local map");
        for (Object entry : (Object[]) NativeAccess.get(map, "table")) {
            if (entry == null) continue;
            if (!entry.getClass().getName().equals("java.lang.ThreadLocal$ThreadLocalMap$Entry"))
                throw new IllegalStateException("Unknown thread local entry");
            if (((Reference<?>) entry).get() == key) return entry;
        }
        return null;
    }

    private static Object invoke(Object target, String method, Object... args) throws Exception {
        try { return NativeAccess.call(target, method, args); }
        catch (InvocationTargetException error) { rethrow(error.getCause()); throw new AssertionError(); }
    }

    private static Throwable unwrap(Throwable error) {
        while (error instanceof InvocationTargetException && error.getCause() != null) error = error.getCause();
        return error;
    }

    private static void rethrow(Throwable error) throws Exception {
        if (error instanceof Error) throw (Error) error;
        if (error instanceof Exception) throw (Exception) error;
        throw new IllegalStateException(error);
    }

    private static boolean containsIdentity(List<?> values, Object sought) {
        for (Object value : values) if (value == sought) return true;
        return false;
    }

    private static boolean sameIdentities(List<?> first, List<?> second) {
        if (first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) if (first.get(index) != second.get(index)) return false;
        return true;
    }

    private static void digestText(MessageDigest digest, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
}
