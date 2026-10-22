package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.*;
import com.starskyxiii.polyglottooltip.testbridge.nametable.NativeNameTable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

/** Owned-fixture shadow prototype only. Not used by shipped tooltip/search code. */
final class NameTableProbe {
    private static final String LANGUAGE = "com.starskyxiii.polyglottooltip.legacy.minecraft.MinecraftLanguageAdapter";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static NativeNameTable table;
    private static Object snapshot, world, player;
    private static boolean invalidated = true, listening;
    private static String environment, tableDigest;
    private NameTableProbe() { }

    static JsonObject inspect(JsonObject params) throws Exception {
        if (!Boolean.getBoolean("clientdevbridge.nameAudit") || !Boolean.getBoolean("clientdevbridge.nameTablePrototype"))
            throw new IllegalStateException("Explicit owned-fixture prototype flags required");
        Minecraft client = Minecraft.func_71410_x();
        if (!client.func_152345_ab()) throw new IllegalStateException("Game thread required");
        String action = params.get("action").getAsString();
        if (action.equals("state")) return state();
        if (action.equals("disable")) { invalidated = true; return state(); }
        if (client.field_71441_e == null || client.field_71439_g == null) throw new IllegalStateException("World and player required");
        if (action.equals("load")) return load(params);
        JsonArray samples = params.getAsJsonArray("samples");
        if (samples == null || samples.size() < 1 || samples.size() > 32) throw new IllegalArgumentException("Use 1..32 frozen samples");
        CaptureContext captureContext = new CaptureContext();
        JsonObject result = state(); JsonArray rows = new JsonArray();
        if (action.equals("capture") || action.equals("lookup")) {
            boolean lookup = action.equals("lookup");
            for (JsonElement value : samples) rows.add(capture(value.getAsJsonObject(), lookup, captureContext));
        } else if (action.equals("tooltipPair")) {
            if (!current()) throw new IllegalStateException("Active table required for paired shadow control");
            for (JsonElement value : samples) rows.add(tooltipPair(value.getAsJsonObject()));
        } else throw new IllegalArgumentException("Unknown table probe action");
        result.add("rows", rows); result.add("afterContext", state()); result.addProperty("contextChanged", !captureContext.current()); return result;
    }

    private static JsonObject load(JsonObject params) throws Exception {
        invalidated = true;
        Path tableFile = ownedFile(params.get("table").getAsString()), manifestFile = ownedFile(params.get("environmentFile").getAsString());
        if (Files.size(manifestFile) > 16_777_216) throw new IOException("Environment manifest limit");
        byte[] manifestBytes = Files.readAllBytes(manifestFile);
        JsonObject manifest = NativeNameTable.parseEnvironmentManifest(new String(manifestBytes, StandardCharsets.UTF_8));
        if (!manifest.has("runtimeAttested") || !manifest.get("runtimeAttested").getAsBoolean() || manifest.has("historicalOnly"))
            throw new IOException("Fresh runtime attestation required");
        String observed = NativeNameTable.sha256(manifestBytes);
        if (!observed.equals(params.get("environment").getAsString())) throw new IOException("Environment manifest changed");
        if (!System.getProperty("java.runtime.version").equals(manifest.get("javaVersion").getAsString())) throw new IOException("Java runtime changed");
        Map<String, String> expectedMods = new TreeMap<>(), actualMods = new TreeMap<>();
        for (JsonElement value : manifest.getAsJsonArray("mods")) {
            JsonObject mod = value.getAsJsonObject();
            if (expectedMods.put(mod.get("id").getAsString(), mod.get("version").getAsString()) != null) throw new IOException("Duplicate attested mod");
        }
        for (ModContainer mod : Loader.instance().getModList()) actualMods.put(mod.getModId(), mod.getVersion());
        if (!expectedMods.equals(actualMods)) throw new IOException("Loaded mod set differs");
        JsonArray currentPacks = PackProbe.runtime().getAsJsonArray("resourcePacks");
        if (!currentPacks.equals(manifest.get("resourcePacks"))) throw new IOException("Resource pack order changed");
        Set<String> seenRoots = new HashSet<>(); Set<Path> inventoryPaths = new HashSet<>();
        for (JsonElement value : manifest.getAsJsonArray("roots")) {
            JsonObject root = value.getAsJsonObject(); String role = root.get("role").getAsString();
            if (!new HashSet<>(Arrays.asList("mods", "config", "scripts", "resources", "resourcepacks")).contains(role) || !seenRoots.add(role))
                throw new IOException("Unexpected attestation root");
            Path folder = Minecraft.func_71410_x().field_71412_D.toPath().resolve(role).toAbsolutePath().normalize();
            if (!folder.equals(Paths.get(root.get("path").getAsString()).toAbsolutePath().normalize())) throw new IOException("Attestation root outside fixture");
            Set<String> actualFiles = inventory(folder), expectedFiles = new TreeSet<>();
            for (JsonElement name : root.getAsJsonArray("files")) if (!expectedFiles.add(name.getAsString())) throw new IOException("Duplicate inventory file");
            if (!actualFiles.equals(expectedFiles) || Files.exists(folder) != root.get("exists").getAsBoolean()) throw new IOException("Input inventory changed: " + role);
            for (String relative : actualFiles) inventoryPaths.add(folder.resolve(relative).normalize());
        }
        if (seenRoots.size() != 5) throw new IOException("Incomplete attestation roots");
        Set<Path> hashedPaths = new HashSet<>();
        for (JsonElement value : manifest.getAsJsonArray("files")) {
            JsonObject file = value.getAsJsonObject();
            Path filePath = Paths.get(file.get("path").getAsString()).toAbsolutePath().normalize();
            if (!hashedPaths.add(filePath)) throw new IOException("Duplicate attested input path");
            if (!NativeNameTable.sha256(filePath).equals(file.get("sha256").getAsString()))
                throw new IOException("Attested input changed: " + file.get("role").getAsString());
        }
        if (!hashedPaths.containsAll(inventoryPaths)) throw new IOException("Inventory files lack content hashes");
        if (!NativeNameTable.sha256(tableFile).equals(params.get("tableSha256").getAsString())) throw new IOException("Table changed");
        long start = System.nanoTime(); NativeNameTable loaded = NativeNameTable.load(tableFile);
        if (!loaded.environment().equals(observed)) throw new IOException("Table environment differs");
        Object next = NativeAccess.call(NativeAccess.type(LANGUAGE), "snapshot");
        if (!(Boolean) NativeAccess.call(next, "ready") || !(Boolean) NativeAccess.call(NativeAccess.type(LANGUAGE), "isCurrent", next)
                || !loaded.primaryLanguage().equals(NativeAccess.get(next, "primary"))) throw new IllegalStateException("Primary context not ready/current");
        if (!listening) {
            NativeAccess.call(NativeAccess.type(LANGUAGE), "onInputsChanging", (Runnable) () -> invalidated = true);
            listening = true;
        }
        table = loaded; environment = observed; tableDigest = params.get("tableSha256").getAsString(); snapshot = next;
        Minecraft client = Minecraft.func_71410_x(); world = client.field_71441_e; player = client.field_71439_g; invalidated = false;
        JsonObject result = state(); result.addProperty("loadAndBindNs", System.nanoTime() - start);
        result.add("statistics", table.statistics()); return result;
    }

    private static Set<String> inventory(Path root) throws IOException {
        Set<String> result = new TreeSet<>();
        if (!Files.exists(root)) return result;
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            Iterator<Path> iterator = stream.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (Files.isSymbolicLink(path)) throw new IOException("Symbolic input inventory is unsupported");
                if (Files.isRegularFile(path)) {
                    if (result.size() >= 50000) throw new IOException("Input inventory limit");
                    result.add(root.relativize(path).toString().replace('\\', '/'));
                }
            }
        }
        return result;
    }

    private static final class CaptureContext {
        final Object language, world, player;
        CaptureContext() throws Exception {
            Minecraft client = Minecraft.func_71410_x();
            language = NativeAccess.call(NativeAccess.type(LANGUAGE), "snapshot");
            world = client.field_71441_e; player = client.field_71439_g;
            if (!current()) throw new IllegalStateException("Acquisition requires ready/current language, world and player");
        }
        boolean current() throws Exception {
            Minecraft client = Minecraft.func_71410_x();
            return world != null && player != null && world == client.field_71441_e && player == client.field_71439_g
                    && language == NativeAccess.call(NativeAccess.type(LANGUAGE), "snapshot")
                    && (Boolean) NativeAccess.call(language, "ready")
                    && (Boolean) NativeAccess.call(NativeAccess.type(LANGUAGE), "isCurrent", language);
        }
    }

    private static Path ownedFile(String name) throws IOException {
        if (!name.matches("[a-zA-Z0-9_.-]{1,120}")) throw new IOException("Use a prototype artifact filename");
        Path root = Minecraft.func_71410_x().field_71412_D.toPath().resolve("exports/native-name-table").toRealPath();
        Path path = root.resolve(name).toRealPath();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) throw new IOException("Artifact is outside fixture prototype root");
        return path;
    }
    private static boolean current() throws Exception {
        Minecraft client = Minecraft.func_71410_x();
        return table != null && !invalidated && world != null && world == client.field_71441_e && player == client.field_71439_g
                && snapshot == NativeAccess.call(NativeAccess.type(LANGUAGE), "snapshot")
                && (Boolean) NativeAccess.call(NativeAccess.type(LANGUAGE), "isCurrent", snapshot)
                && table.primaryLanguage().equals(client.field_71474_y.field_74363_ab);
    }
    private static JsonObject state() throws Exception {
        Minecraft client = Minecraft.func_71410_x(); JsonObject result = new JsonObject();
        result.addProperty("active", current()); result.addProperty("loaded", table != null);
        result.addProperty("language", client.field_71474_y.field_74363_ab);
        result.addProperty("worldIdentity", System.identityHashCode(client.field_71441_e)); result.addProperty("playerIdentity", System.identityHashCode(client.field_71439_g));
        result.addProperty("invalidated", invalidated); result.addProperty("environment", environment); result.addProperty("tableSha256", tableDigest);
        if (Loader.isModLoaded("polyglottooltip")) {
            Object context = NativeAccess.call(NativeAccess.type(LANGUAGE), "snapshot");
            result.addProperty("ready", (Boolean) NativeAccess.call(context, "ready"));
            result.addProperty("generation", (Number) NativeAccess.get(context, "generation"));
            result.addProperty("contextIdentity", System.identityHashCode(context));
        }
        return result;
    }

    private static ItemStack decode(JsonObject sample) throws Exception {
        NativeNameTable.key(sample);
        String value = sample.get("serialized").getAsString();
        if (value.length() > 349528) throw new IOException("Serialized stack limit");
        byte[] bytes = Base64.getDecoder().decode(value); NameAuditNbt.read(bytes);
        ItemStack stack;
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            stack = new ItemStack(CompressedStreamTools.func_152456_a(input, new NBTSizeTracker(262144)));
            if (input.read() != -1) throw new IOException("Trailing stack bytes");
        }
        stack.func_190920_e(sample.get("count").getAsInt()); stack.func_77964_b(sample.get("damage").getAsInt());
        if (sample.has("edit")) {
            JsonObject edit = sample.getAsJsonObject("edit");
            if (edit.has("count")) stack.func_190920_e(edit.get("count").getAsInt());
            if (edit.has("damage")) stack.func_77964_b(edit.get("damage").getAsInt());
            if (edit.has("literalName")) stack.func_151001_c(edit.get("literalName").getAsString());
            if (edit.has("unknownNbt")) {
                NBTTagCompound tag = stack.func_77978_p();
                if (tag == null) { tag = new NBTTagCompound(); stack.func_77982_d(tag); }
                tag.func_74768_a("polyglotPrototypeUnseen", edit.get("unknownNbt").getAsInt());
            }
        }
        return stack;
    }
    /** Serializes capabilities; deliberately kept outside the pure engine boundary. */
    private static JsonObject freeze(ItemStack stack) throws IOException {
        if (stack.func_190926_b() || stack.func_77973_b().getRegistryName() == null) throw new IOException("Empty or unregistered live stack");
        JsonObject value = new JsonObject(); value.addProperty("item", stack.func_77973_b().getRegistryName().toString());
        value.addProperty("metadata", stack.func_77960_j()); value.addProperty("damage", stack.func_77952_i()); value.addProperty("count", stack.func_190916_E());
        byte[] bytes = NameAuditProbe.binary(stack);
        if (bytes.length > 262144) throw new IOException("Live serialized state limit");
        value.add("stack", NameAuditNbt.read(bytes)); return value;
    }
    private static JsonObject capture(JsonObject sample, boolean lookup, CaptureContext context) throws Exception {
        JsonObject row = new JsonObject(); row.add("sampleId", sample.get("sampleId")); row.add("item", sample.get("item"));
        row.addProperty("language", Minecraft.func_71410_x().field_71474_y.field_74363_ab); row.addProperty("directNativeNameCalls", 0);
        if (sample.has("edit")) row.add("edit", sample.get("edit"));
        if (lookup && !current() || !context.current()) {
            row.addProperty("status", "STALE_ENVIRONMENT"); row.addProperty("reason", "adapter-context-not-current"); return row;
        }
        long start = System.nanoTime();
        try {
            ItemStack stack = decode(sample); row.addProperty("decodeNs", System.nanoTime() - start);
            start = System.nanoTime(); JsonObject before = freeze(stack); row.addProperty("liveCaptureBeforeNs", System.nanoTime() - start);
            String frozenKey = NativeNameTable.key(before);
            row.addProperty("inputMatches", frozenKey.equals(NativeNameTable.key(sample)));
            row.addProperty("metadataMatches", sample.get("metadata").getAsInt() == stack.func_77960_j());
            row.addProperty("damageMatches", sample.get("damage").getAsInt() == stack.func_77952_i());
            row.addProperty("countMatches", sample.get("count").getAsInt() == stack.func_190916_E());
            start = System.nanoTime(); row.addProperty("directNativeNameCalls", 1); String primary = stack.func_82833_r();
            row.addProperty("nativeNameNs", System.nanoTime() - start); row.addProperty("name", primary);
            start = System.nanoTime(); JsonObject afterName = freeze(stack); row.addProperty("liveCaptureAfterNameNs", System.nanoTime() - start);
            row.addProperty("stateChanged", !frozenKey.equals(NativeNameTable.key(afterName))); row.add("state", before);
            if (lookup) {
                JsonObject outputs = new JsonObject();
                boolean mutated = row.get("stateChanged").getAsBoolean();
                boolean decodeMismatch = !sample.has("edit") && !row.get("inputMatches").getAsBoolean();
                for (String language : table.languages()) {
                    start = System.nanoTime();
                    JsonObject outcome;
                    if (mutated || decodeMismatch) { outcome = new JsonObject(); outcome.addProperty("status", "INVALID_INPUT"); outcome.addProperty("reason", mutated ? "native-call-mutated-state" : "input-roundtrip-mismatch"); }
                    else outcome = table.lookup(before, primary, language, environment, current()).json();
                    outcome.addProperty("frozenKeyAndLookupNs", System.nanoTime() - start);
                    outputs.add(language, outcome);
                }
                row.add("outputs", outputs); row.addProperty("directNativeNameCallsInsideEngine", 0);
                start = System.nanoTime(); JsonObject afterLookup = freeze(stack); row.addProperty("liveCaptureAfterLookupNs", System.nanoTime() - start);
                row.addProperty("serializedStateChangedAcrossLookupObservation", !NativeNameTable.key(afterName).equals(NativeNameTable.key(afterLookup)));
            }
        } catch (Exception | LinkageError error) {
            Throwable cause = error; while (cause.getCause() != null) cause = cause.getCause();
            row.addProperty("error", cause.getClass().getName() + ": " + String.valueOf(cause.getMessage()));
        }
        row.addProperty("languageAfter", Minecraft.func_71410_x().field_71474_y.field_74363_ab);
        boolean changed = !context.current(); row.addProperty("contextChanged", changed);
        if (changed && lookup && row.has("outputs")) {
            row.add("invalidatedOutputs", row.get("outputs")); JsonObject outputs = new JsonObject();
            for (String language : table.languages()) { JsonObject stale = new JsonObject(); stale.addProperty("status", "STALE_ENVIRONMENT"); outputs.add(language, stale); }
            row.add("outputs", outputs);
        }
        return row;
    }

    private static JsonObject tooltipPair(JsonObject sample) throws Exception {
        JsonObject result = new JsonObject(); result.add("sampleId", sample.get("sampleId")); result.add("item", sample.get("item"));
        JsonArray observations = new JsonArray(); result.add("contextBefore", state());
        for (String mode : new String[]{"NORMAL", "ADVANCED"}) for (String phase : new String[]{"inactive-before", "active-shadow", "inactive-after"}) {
            JsonObject row = new JsonObject(); row.addProperty("mode", mode); row.addProperty("phase", phase);
            boolean active = phase.equals("active-shadow"); NameAuditCapture.Frame frame = null;
            try {
                ItemStack stack = decode(sample); JsonObject before = freeze(stack); row.add("before", before);
                JsonObject activeFrozen = active ? freeze(stack) : null;
                ITooltipFlag flags = mode.equals("NORMAL") ? ITooltipFlag.TooltipFlags.NORMAL : ITooltipFlag.TooltipFlags.ADVANCED;
                frame = NameAuditCapture.begin(stack, false); List<String> tooltip;
                try { tooltip = stack.func_82840_a(Minecraft.func_71410_x().field_71439_g, flags); }
                finally { NameAuditCapture.end(); }
                row.add("tooltip", GSON.toJsonTree(tooltip)); row.addProperty("nativeNameCaptured", frame.nativeNameCaptured);
                row.addProperty("nativeName", frame.nativeName); row.addProperty("literalCustomName", stack.func_82837_s());
                row.addProperty("contextCurrent", current());
                if (active) {
                    JsonObject outcomes = new JsonObject();
                    for (String language : table.languages()) outcomes.add(language, table.lookup(activeFrozen, frame.nativeName, language, environment, current()).json());
                    row.add("shadowOutputs", outcomes);
                }
                row.add("after", freeze(stack));
            } catch (Exception | LinkageError error) { row.addProperty("error", error.getClass().getName() + ": " + error.getMessage()); }
            observations.add(row);
        }
        result.add("observations", observations); result.add("contextAfter", state()); return result;
    }
}
