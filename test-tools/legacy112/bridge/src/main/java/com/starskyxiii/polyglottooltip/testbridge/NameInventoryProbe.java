package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.*;
import java.io.BufferedReader;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Explicit name-only catalog research. Never calls getTooltip or the tooltip audit matrix. */
final class NameInventoryProbe {
    private static final String EXPORT = "com.starskyxiii.polyglottooltip.legacy.export.";
    private static final String LANGUAGE = "com.starskyxiii.polyglottooltip.legacy.minecraft.MinecraftLanguageAdapter";
    private static final Gson GSON = new Gson();
    private static final JsonParser PARSER = new JsonParser();
    private static Object baseline, baselineFiles;
    private static BufferedReader reader;
    private static JsonArray sources;
    private static String baselinePath, failure;
    private static Object catalogUnion, catalogContext, catalogWorld, catalogPlayer;
    private static String catalogSelector;
    private static String catalogMode;
    private static final Set<String> catalogSeen = new HashSet<>();
    private static int chunkSize, chunkIndex, chunkAccepted, totalAccepted, totalDuplicates;
    private NameInventoryProbe() { }

    static JsonObject inspect(JsonObject params) throws Exception {
        String action = params.get("action").getAsString();
        if (action.equals("registry")) return registry();
        if (action.equals("status")) return status();
        if (action.equals("next")) {
            requireCatalogContext();
            JsonObject product = (JsonObject) NativeAccess.call(NativeAccess.type(EXPORT + "NameExportManager"), "status");
            if (!product.get("closed").getAsBoolean() || !product.get("state").getAsString().equals("COMPLETE"))
                throw new IllegalStateException("Previous catalog chunk must complete successfully");
            if (allSourcesExhausted()) throw new IllegalStateException("Catalog sources already exhausted");
            beginChunk(); return status();
        }
        if (action.equals("start")) {
            if (baseline != null && !(Boolean) NativeAccess.call(baseline, "closed")) throw new IllegalStateException("Native baseline still running");
            Class<?> manager = NativeAccess.type(EXPORT + "NameExportManager");
            NativeAccess.call(manager, "requireStart");
            String selector = params.has("selector") ? params.get("selector").getAsString() : "*";
            String mode = params.has("mode") ? params.get("mode").getAsString() : "interactive";
            NativeAccess.call(NativeAccess.type(EXPORT + "NameExportMode"), "parse", mode);
            final List<Object> catalog = new ArrayList<>();
            sources = new JsonArray();
            for (String kind : new String[]{"jei", "creative", "defaults"}) {
                if (kind.equals("jei") && !Loader.isModLoaded("jei")) continue;
                catalog.add(NativeAccess.call(NativeAccess.type(EXPORT + "NameExportSources"), "catalog", kind, selector));
                JsonObject source = new JsonObject(); source.addProperty("kind", kind); source.addProperty("inputs", 0);
                source.addProperty("steps", 0); source.addProperty("exhausted", false); sources.add(source);
            }
            final int[] index = {0};
            catalogUnion = proxy("Source", (method, arguments) -> {
                while (index[0] < catalog.size()) {
                    Object value = NativeAccess.call(catalog.get(index[0]), "next");
                    JsonObject source = sources.get(index[0]).getAsJsonObject();
                    source.addProperty("steps", source.get("steps").getAsInt() + 1);
                    if (value == null) { source.addProperty("exhausted", true); index[0]++; continue; }
                    if (!(Boolean) NativeAccess.get(value, "skip")) source.addProperty("inputs", source.get("inputs").getAsInt() + 1);
                    return value;
                }
                return null;
            });
            chunkSize = params.has("chunkSize") ? params.get("chunkSize").getAsInt() : 90000;
            if (chunkSize < 1 || chunkSize > 90000) throw new IllegalArgumentException("Chunk size must be 1..90000");
            Minecraft client = Minecraft.func_71410_x();
            catalogWorld = client.field_71441_e; catalogPlayer = client.field_71439_g;
            catalogContext = NativeAccess.call(NativeAccess.type(LANGUAGE), "snapshot");
            catalogSelector = selector; catalogMode = mode;
            chunkIndex = totalAccepted = totalDuplicates = 0; catalogSeen.clear();
            beginChunk();
            return status();
        }
        if (action.equals("native")) { nativeStart(params); return status(); }
        if (action.equals("cancelNative")) { if (baseline != null) NativeAccess.call(baseline, "cancel"); return status(); }
        throw new IllegalArgumentException("Unknown name inventory action");
    }

    private static boolean allSourcesExhausted() {
        if (sources == null) return false;
        for (JsonElement source : sources) if (!source.getAsJsonObject().get("exhausted").getAsBoolean()) return false;
        return true;
    }

    private static void requireCatalogContext() throws Exception {
        Minecraft client = Minecraft.func_71410_x();
        if (catalogUnion == null || client.field_71441_e != catalogWorld || client.field_71439_g != catalogPlayer
                || NativeAccess.call(NativeAccess.type(LANGUAGE), "snapshot") != catalogContext)
            throw new IllegalStateException("Catalog context changed between chunks");
        if (baseline != null && !(Boolean) NativeAccess.call(baseline, "closed"))
            throw new IllegalStateException("Native baseline still running");
    }

    private static void beginChunk() throws Exception {
        requireCatalogContext();
        chunkAccepted = 0; chunkIndex++;
        Object chunk = proxy("Source", (method, arguments) -> {
            if (chunkAccepted >= chunkSize) return null;
            Object value = NativeAccess.call(catalogUnion, "next");
            if (value == null || (Boolean) NativeAccess.get(value, "skip")) return value;
            String identity = (String) NativeAccess.get(value, "identity");
            if (identity != null && !catalogSeen.add(identity)) {
                totalDuplicates++; return NativeAccess.get(NativeAccess.type(EXPORT + "NameExportJob$Input"), "SKIP");
            }
            chunkAccepted++; totalAccepted++; return value;
        });
        NativeAccess.call(NativeAccess.type(EXPORT + "NameExportManager"), "begin", chunk,
                "jei+creative+defaults:chunk-" + chunkIndex, catalogSelector, 100000, catalogMode);
    }

    private static JsonObject registry() {
        JsonObject result = new JsonObject(); JsonArray mods = new JsonArray(), items = new JsonArray();
        for (ModContainer mod : Loader.instance().getModList()) {
            JsonObject value = new JsonObject(); value.addProperty("id", mod.getModId()); value.addProperty("name", mod.getName());
            value.addProperty("version", mod.getVersion()); mods.add(value);
        }
        for (Item item : ForgeRegistries.ITEMS.getValuesCollection()) {
            if (item.getRegistryName() == null) continue;
            JsonObject value = new JsonObject(); value.addProperty("item", item.getRegistryName().toString());
            value.addProperty("class", item.getClass().getName()); items.add(value);
        }
        result.add("mods", mods); result.add("items", items); return result;
    }

    private static void nativeStart(JsonObject params) throws Exception {
        if (baseline != null && !(Boolean) NativeAccess.call(baseline, "closed")) throw new IllegalStateException("Native baseline still running");
        JsonObject product = (JsonObject) NativeAccess.call(NativeAccess.type(EXPORT + "NameExportManager"), "status");
        if (product.has("closed") && !product.get("closed").getAsBoolean()) throw new IllegalStateException("Product export still running");
        Minecraft client = Minecraft.func_71410_x();
        if (client.field_71439_g == null || client.field_71441_e == null) throw new IllegalStateException("World required");
        Object context = NativeAccess.call(NativeAccess.type(LANGUAGE), "snapshot");
        if (!(Boolean) NativeAccess.call(context, "ready")) throw new IllegalStateException("Language not ready");
        Path root = client.field_71412_D.toPath().resolve("exports/polyglot-names").toRealPath();
        Path input = Paths.get(params.get("directory").getAsString()).toRealPath();
        if (!input.startsWith(root)) throw new IllegalArgumentException("Input must be this fixture's completed export");
        JsonObject summary = PARSER.parse(new String(Files.readAllBytes(input.resolve("summary.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        if (!summary.get("state").getAsString().equals("COMPLETE") || !summary.get("sourceExhausted").getAsBoolean())
            throw new IllegalStateException("Completed source required");
        String language = (String) NativeAccess.get(context, "primary");
        Path output = root.resolve("native-" + language + "-" + UUID.randomUUID().toString().substring(0, 8));
        JsonObject manifest = new JsonObject(); manifest.addProperty("schema", "polyglot-native-name-baseline-v1");
        manifest.addProperty("created", Instant.now().toString()); manifest.addProperty("language", language);
        manifest.addProperty("sourceDirectory", input.toString()); manifest.addProperty("scope", "Fresh decoded stack, one natural display-name call; no tooltip or secondary resolver");
        manifest.addProperty("generation", (Long) NativeAccess.get(context, "generation"));
        reader = Files.newBufferedReader(input.resolve("names.jsonl"), StandardCharsets.UTF_8);
        baselineFiles = NativeAccess.create(EXPORT + "NameExportFiles", output, manifest.toString());
        Object source = proxy("Source", (method, arguments) -> {
            String line = reader.readLine();
            if (line == null) return null;
            JsonObject row = PARSER.parse(line).getAsJsonObject();
            if (!row.get("kind").getAsString().equals("item")) return NativeAccess.get(NativeAccess.type(EXPORT + "NameExportJob$Input"), "SKIP");
            return NativeAccess.create(EXPORT + "NameExportJob$Input", row.get("sampleId").getAsString(), line);
        });
        Object processor = proxy("Processor", (method, arguments) -> {
            JsonObject inputRow = PARSER.parse((String) NativeAccess.get(arguments[0], "payload")).getAsJsonObject();
            JsonObject row = new JsonObject(); row.add("sampleId", inputRow.get("sampleId")); row.add("item", inputRow.get("item"));
            row.addProperty("sequence", (Integer) arguments[1]); row.addProperty("language", language);
            long start = System.nanoTime();
            try {
                Class<?> stackCodec = NativeAccess.type(EXPORT + "NameExportStack"), nbtCodec = NativeAccess.type(EXPORT + "NameExportNbt");
                ItemStack stack = (ItemStack) NativeAccess.call(stackCodec, "decode", inputRow);
                byte[] before = (byte[]) NativeAccess.call(stackCodec, "binary", stack.func_77955_b(new NBTTagCompound()));
                JsonElement typedBefore = (JsonElement) NativeAccess.call(nbtCodec, "read", before);
                row.addProperty("inputMatches", typedBefore.equals(inputRow.get("stack")));
                row.addProperty("name", stack.func_82833_r());
                byte[] after = (byte[]) NativeAccess.call(stackCodec, "binary", stack.func_77955_b(new NBTTagCompound()));
                row.addProperty("stateChanged", !typedBefore.equals(NativeAccess.call(nbtCodec, "read", after)));
            } catch (Exception | LinkageError error) {
                Throwable cause = error; while (cause.getCause() != null) cause = cause.getCause();
                row.addProperty("error", cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage()));
            }
            row.addProperty("elapsedMicros", (System.nanoTime() - start) / 1000);
            return NativeAccess.create(EXPORT + "NameExportJob$Row", row.toString(), "");
        });
        Object world = client.field_71441_e, player = client.field_71439_g;
        BooleanSupplier current = () -> {
            try { return client.field_71441_e == world && client.field_71439_g == player
                    && NativeAccess.call(NativeAccess.type(LANGUAGE), "snapshot") == context
                    && (Boolean) NativeAccess.call(context, "ready"); }
            catch (Exception error) { return false; }
        };
        baseline = NativeAccess.create(EXPORT + "NameExportJob", source, processor, baselineFiles, current, (LongSupplier) System::nanoTime, 100000);
        baselinePath = output.toString(); failure = null;
    }

    static void tick() {
        if (baseline == null) return;
        try {
            NativeAccess.call(baseline, "tick", 32, 4_000_000L);
            if ((Boolean) NativeAccess.call(baseline, "closed") && reader != null) { reader.close(); reader = null; }
        } catch (Exception | LinkageError error) { failure = error.toString(); }
    }

    private static JsonObject status() throws Exception {
        JsonObject result = new JsonObject();
        result.add("product", (JsonElement) NativeAccess.call(NativeAccess.type(EXPORT + "NameExportManager"), "status"));
        if (sources != null) result.add("sources", sources);
        if (catalogUnion != null) {
            JsonObject chunk = new JsonObject(); chunk.addProperty("index", chunkIndex); chunk.addProperty("size", chunkSize);
            chunk.addProperty("accepted", chunkAccepted); chunk.addProperty("totalAccepted", totalAccepted);
            chunk.addProperty("totalDuplicates", totalDuplicates); chunk.addProperty("allSourcesExhausted", allSourcesExhausted());
            result.add("chunk", chunk);
        }
        if (baseline != null) {
            JsonObject state = GSON.toJsonTree(NativeAccess.call(baseline, "summary")).getAsJsonObject();
            state.addProperty("closed", (Boolean) NativeAccess.call(baseline, "closed")); state.addProperty("directory", baselinePath);
            if (failure != null) state.addProperty("probeFailure", failure); result.add("baseline", state);
        }
        return result;
    }

    private interface Invocation { Object call(Method method, Object[] arguments) throws Exception; }
    private static Object proxy(String suffix, Invocation invocation) throws Exception {
        Class<?> type = NativeAccess.type(EXPORT + "NameExportJob$" + suffix);
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, arguments) -> invocation.call(method, arguments));
    }
}
