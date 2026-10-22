package com.starskyxiii.polyglottooltip.testbridge.nametable;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Offline driver for the exact engine used by the in-game research bridge. */
public final class NameTableTool {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private NameTableTool() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 4 || !args[0].equals("build")) throw new IllegalArgumentException("build observations.jsonl table.jsonl metrics.json");
        Path input = Paths.get(args[1]), output = Paths.get(args[2]), metrics = Paths.get(args[3]);
        String sourceDigest = NativeNameTable.sha256(input);
        long beforeHeap = heap(), start = System.nanoTime();
        NativeNameTable.Builder builder;
        List<JsonObject> benchmark = new ArrayList<>();
        int rows = 0;
        JsonObject header;
        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            header = NativeNameTable.parse(NativeNameTable.boundedLine(reader));
            List<String> languages = new ArrayList<>(); for (JsonElement code : header.getAsJsonArray("languages")) languages.add(code.getAsString());
            builder = new NativeNameTable.Builder(header.get("environment").getAsString(), header.get("primaryLanguage").getAsString(), languages, sourceDigest);
            for (String line; (line = NativeNameTable.boundedLine(reader)) != null;) {
                JsonObject row = NativeNameTable.parse(line);
                JsonObject state = row.getAsJsonObject("state");
                for (Map.Entry<String, JsonElement> entry : row.getAsJsonObject("targets").entrySet()) {
                    JsonObject target = entry.getValue().getAsJsonObject();
                    builder.observe(state, text(row, "primary"), entry.getKey(), text(target, "name"), text(target, "rejection"));
                }
                if (rows % 97 == 0 && benchmark.size() < 2048) benchmark.add(row);
                rows++;
            }
        }
        NativeNameTable table = builder.build(); builder = null;
        double buildMs = (System.nanoTime() - start) / 1_000_000.0;
        long afterHeap = heap(); start = System.nanoTime(); table.save(output);
        double saveMs = (System.nanoTime() - start) / 1_000_000.0;
        table = null; heap(); start = System.nanoTime();
        table = NativeNameTable.load(output);
        double loadMs = (System.nanoTime() - start) / 1_000_000.0;
        JsonObject result = table.statistics(); result.addProperty("inputRows", rows); result.addProperty("environment", table.environment());
        result.addProperty("sourceSha256", sourceDigest); result.addProperty("tableSha256", NativeNameTable.sha256(output));
        result.addProperty("fileBytes", Files.size(output)); result.addProperty("buildMs", buildMs); result.addProperty("saveMs", saveMs); result.addProperty("loadMs", loadMs);
        result.addProperty("observedRetainedHeapDeltaBytes", afterHeap - beforeHeap); result.addProperty("heapCaveat", "Noisy post-System.gc heap observation includes benchmark rows and JVM effects; not peak RSS or per-entry object size");
        result.addProperty("javaVersion", System.getProperty("java.runtime.version")); result.addProperty("javaVendor", System.getProperty("java.vendor"));
        result.addProperty("scope", "Historical/fresh training replay only. Not held-out accuracy or end-to-end tooltip performance.");
        JsonObject replay = new JsonObject(); long mismatches = 0, comparisons = 0;
        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            NativeNameTable.boundedLine(reader);
            for (String line; (line = NativeNameTable.boundedLine(reader)) != null;) {
                JsonObject row = NativeNameTable.parse(line);
                for (Map.Entry<String, JsonElement> entry : row.getAsJsonObject("targets").entrySet()) {
                    NativeNameTable.Result actual = table.lookup(row.getAsJsonObject("state"), text(row, "primary"), entry.getKey(), table.environment(), true);
                    String status = actual.status.name(); replay.addProperty(status, replay.has(status) ? replay.get(status).getAsLong() + 1 : 1);
                    if (actual.name != null) { comparisons++; if (!actual.name.equals(text(entry.getValue().getAsJsonObject(), "name"))) mismatches++; }
                }
            }
        }
        result.add("trainingReplayStatuses", replay); result.addProperty("trainingReplayOutputPairs", comparisons); result.addProperty("trainingReplayNativeDisagreements", mismatches);
        List<Long> keyAndLookup = new ArrayList<>(), mapLookup = new ArrayList<>(); long checksum = 0;
        String language = table.languages().iterator().next();
        List<String> keys = new ArrayList<>(); for (JsonObject row : benchmark) keys.add(NativeNameTable.key(row.getAsJsonObject("state")));
        for (int iteration = 0; iteration < 6; iteration++) for (int index = 0; index < benchmark.size(); index++) {
            JsonObject row = benchmark.get(index); start = System.nanoTime();
            NativeNameTable.Result actual = table.lookup(row.getAsJsonObject("state"), text(row, "primary"), language, table.environment(), true);
            long elapsed = System.nanoTime() - start; checksum += actual.status.ordinal() + (actual.name == null ? 0 : actual.name.length());
            if (iteration >= 2) keyAndLookup.add(elapsed);
            start = System.nanoTime(); actual = table.lookupKey(keys.get(index), text(row, "primary"), language);
            elapsed = System.nanoTime() - start; checksum += actual.status.ordinal() + (actual.name == null ? 0 : actual.name.length());
            if (iteration >= 2) mapLookup.add(elapsed);
        }
        JsonObject perf = new JsonObject(); perf.addProperty("sampleSelection", "Every 97th training row, up to 2048; not a random gameplay sample");
        perf.addProperty("states", benchmark.size()); perf.addProperty("warmupPasses", 2); perf.addProperty("measuredPasses", 4); perf.addProperty("checksum", checksum);
        perf.add("frozenStateKeyAndLookupNs", stats(keyAndLookup)); perf.add("precomputedKeyMapLookupNs", stats(mapLookup)); result.add("benchmark", perf);
        Files.write(metrics, (GSON.toJson(result) + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
        System.out.println(GSON.toJson(result));
    }
    public static JsonObject stats(List<Long> values) {
        JsonObject value = new JsonObject(); value.addProperty("n", values.size()); if (values.isEmpty()) return value;
        List<Long> sorted = new ArrayList<>(values); Collections.sort(sorted); double total = 0; for (long number : values) total += number;
        value.addProperty("p50", percentile(sorted, .50)); value.addProperty("p95", percentile(sorted, .95)); value.addProperty("p99", percentile(sorted, .99));
        value.addProperty("max", sorted.get(sorted.size() - 1)); value.addProperty("mean", total / values.size());
        value.addProperty("operationsPerSecondFromMeasuredDurations", total == 0 ? 0 : values.size() * 1_000_000_000.0 / total); return value;
    }
    private static long percentile(List<Long> values, double fraction) { return values.get(Math.max(0, (int) Math.ceil(values.size() * fraction) - 1)); }
    private static String text(JsonObject value, String field) { return value.has(field) && !value.get(field).isJsonNull() ? value.get(field).getAsString() : null; }
    private static long heap() throws InterruptedException { System.gc(); Thread.sleep(30); return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(); }
}
