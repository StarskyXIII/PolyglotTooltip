package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.*;
import java.io.BufferedReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

/** Replays at most 256 frozen stacks from an owned completed export; never enumerates a pack. */
final class NameExportBatchProbe {
    private static final String EXPORT = "com.starskyxiii.polyglottooltip.legacy.export.";
    private static final List<Object> samples = new ArrayList<>();
    private NameExportBatchProbe() { }

    static JsonObject inspect(JsonObject params) throws Exception {
        Class<?> manager = NativeAccess.type(EXPORT + "NameExportManager");
        String action = params.get("action").getAsString();
        if (action.equals("prepare")) {
            NativeAccess.call(manager, "requireStart");
            int count = params.has("count") ? params.get("count").getAsInt() : 128;
            if (count < 1 || count > 256) throw new IllegalArgumentException("Use 1..256 frozen stacks");
            String item = params.get("item").getAsString();
            if (!item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("One exact item ID required");
            Path root = Minecraft.func_71410_x().field_71412_D.toPath().resolve("exports/polyglot-names").toRealPath();
            Path source = Paths.get(params.get("directory").getAsString()).toRealPath();
            if (!source.startsWith(root)) throw new IllegalArgumentException("Use this owned fixture's completed export");
            JsonParser parser = new JsonParser();
            JsonObject summary = parser.parse(new String(Files.readAllBytes(source.resolve("summary.json")), StandardCharsets.UTF_8)).getAsJsonObject();
            if (!summary.get("state").getAsString().equals("COMPLETE") || !summary.get("sourceExhausted").getAsBoolean())
                throw new IllegalArgumentException("Completed source required");
            List<Object> prepared = new ArrayList<>(); Set<String> seen = new HashSet<>(); int scanned = 0;
            try (BufferedReader reader = Files.newBufferedReader(source.resolve("names.jsonl"), StandardCharsets.UTF_8)) {
                String line;
                while (scanned < 4096 && prepared.size() < count && (line = reader.readLine()) != null) {
                    scanned++; JsonObject row = parser.parse(line).getAsJsonObject();
                    if (!row.get("kind").getAsString().equals("item") || !row.get("item").getAsString().equals(item)) continue;
                    ItemStack stack = (ItemStack) NativeAccess.call(NativeAccess.type(EXPORT + "NameExportStack"), "decode", row);
                    Object input = NativeAccess.call(NativeAccess.type(EXPORT + "NameExportStack"), "freeze", stack, "focused-batch-snapshot");
                    String identity = (String) NativeAccess.get(input, "identity");
                    if (identity == null || (Boolean) NativeAccess.get(input, "skip")) throw new IllegalStateException("Invalid frozen input");
                    if (seen.add(identity)) prepared.add(input);
                }
            }
            if (prepared.size() != count) throw new IllegalStateException("Not enough distinct stacks in the bounded read");
            samples.clear(); samples.addAll(prepared);
            JsonObject result = new JsonObject(); result.addProperty("samples", samples.size()); result.addProperty("scanned", scanned);
            result.addProperty("item", item); result.addProperty("sourceDirectory", source.toString()); return result;
        }
        if (!action.equals("start") || samples.isEmpty()) throw new IllegalArgumentException("Prepare the bounded sample first");
        NativeAccess.call(manager, "requireStart");
        String mode = params.get("mode").getAsString();
        int repeats = params.has("repeats") ? params.get("repeats").getAsInt() : 1;
        if (repeats < 1 || repeats > 16) throw new IllegalArgumentException("Use 1..16 repeats");
        final List<Object> frozen = new ArrayList<>(samples); final int[] index = {0};
        Class<?> sourceType = NativeAccess.type(EXPORT + "NameExportJob$Source");
        Object source = Proxy.newProxyInstance(sourceType.getClassLoader(), new Class<?>[]{sourceType},
                (proxy, method, arguments) -> index[0] < frozen.size() * repeats ? frozen.get(index[0]++ / repeats) : null);
        NativeAccess.call(manager, "begin", source, "bounded-batch-replay", "frozen-" + frozen.size() + "-repeat-" + repeats, 10000, mode);
        if (params.has("paused") && params.get("paused").getAsBoolean()) NativeAccess.call(manager, "action", "pause");
        return (JsonObject) NativeAccess.call(manager, "status");
    }
}
