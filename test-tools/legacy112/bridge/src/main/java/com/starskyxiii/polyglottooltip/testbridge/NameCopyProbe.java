package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Raw keys of stacks the game derives from a catalogue stack: Forge's {@code ItemStack.copy()} hands the copy
 * {@code capabilities.serializeNBT()} as its capNBT ({@code {}} for writer-less dispatchers where the catalogue
 * stack holds null), and an NBT round trip reads {@code ForgeCaps} back only when it was written non-empty.
 * {@code audit} compares both against every JEI stack; {@code held} renders the tooltip of the held stack, its
 * copies and its NBT round trip and reports table hits and the product's secondary rows for each.
 */
public final class NameCopyProbe {
    private static final Gson GSON = new Gson();
    private static final String PRODUCT = "com.starskyxiii.polyglottooltip.legacy.nametable.";

    private NameCopyProbe() {}

    public static JsonObject inspect(JsonObject parameters) throws Exception {
        String action = parameters.has("action") ? parameters.get("action").getAsString() : "audit";
        if (action.equals("audit")) return audit(parameters);
        if (action.equals("held")) return held(parameters);
        throw new IllegalArgumentException("Unknown nameCopy action " + action);
    }

    private static Object binding() throws Exception {
        Object binding = NativeAccess.call(NativeAccess.type(PRODUCT + "NameTableRuntime"), "binding");
        if (binding == null) throw new IllegalStateException("No current table binding");
        return binding;
    }

    private static Object key(net.minecraft.item.ItemStack stack, Object registry) throws Exception {
        Class<?> raw = NativeAccess.type(PRODUCT + "RawStackState");
        NativeAccess.call(raw, "initializeCapabilities", stack);
        return NativeAccess.call(raw, "capture", stack, registry);
    }

    private static String capKind(net.minecraft.item.ItemStack stack) throws Exception {
        Object tag = NativeAccess.get(stack, "capNBT");
        if (tag == null) return "null";
        return ((net.minecraft.nbt.NBTTagCompound) tag).func_82582_d() ? "empty" : "nonempty";
    }

    private static net.minecraft.item.ItemStack roundTrip(net.minecraft.item.ItemStack stack) {
        return new net.minecraft.item.ItemStack(stack.func_77955_b(new net.minecraft.nbt.NBTTagCompound()));
    }

    private static JsonObject audit(JsonObject parameters) throws Exception {
        int limit = parameters.has("samples") ? parameters.get("samples").getAsInt() : 3;
        Object binding = binding(), table = NativeAccess.get(binding, "table"), registry = NativeAccess.get(binding, "registry");
        java.util.Collection<net.minecraft.item.ItemStack> ingredients =
                mezz.jei.Internal.getIngredientRegistry().getAllIngredients(net.minecraft.item.ItemStack.class);
        // [ingredients, original in table, copy key equal, copy in table, round-trip key equal, round-trip in table]
        Map<String, int[]> byCopyCap = new TreeMap<>();
        Map<String, int[]> copyMissByClass = new TreeMap<>(), roundMissByClass = new TreeMap<>();
        Map<String, Integer> failures = new TreeMap<>();
        Map<String, JsonArray> samples = new LinkedHashMap<>();
        int total = 0, original = 0, copyEqual = 0, copyHit = 0, roundEqual = 0, roundHit = 0, copyOfCopyEqual = 0;
        long started = System.nanoTime();
        for (net.minecraft.item.ItemStack ingredient : ingredients) {
            String type = ingredient.func_77973_b() == null ? "<none>" : ingredient.func_77973_b().getClass().getName();
            Object base;
            net.minecraft.item.ItemStack copy, round;
            Object copied, read, copiedTwice;
            String cap;
            try {
                // The registry stack is read as the catalogue reads it, without a lifecycle step of its own.
                base = NativeAccess.call(NativeAccess.type(PRODUCT + "RawStackState"), "capture", ingredient, registry);
                copy = ingredient.func_77946_l();
                cap = capKind(copy);
                copied = key(copy, registry);
                copiedTwice = key(copy.func_77946_l(), registry);
                round = roundTrip(ingredient);
                read = key(round, registry);
            } catch (java.lang.reflect.InvocationTargetException error) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                failures.merge(String.valueOf(cause.getMessage()), 1, Integer::sum);
                continue;
            } catch (RuntimeException | LinkageError error) {
                failures.merge(error.getClass().getSimpleName(), 1, Integer::sum);
                continue;
            }
            total++;
            boolean inTable = (Boolean) NativeAccess.call(table, "contains", base);
            boolean ce = base.equals(copied), ch = (Boolean) NativeAccess.call(table, "contains", copied);
            boolean re = base.equals(read), rh = (Boolean) NativeAccess.call(table, "contains", read);
            if (inTable) original++;
            if (ce) copyEqual++;
            if (ch) copyHit++;
            if (re) roundEqual++;
            if (rh) roundHit++;
            if (copied.equals(copiedTwice)) copyOfCopyEqual++;
            int[] counts = byCopyCap.computeIfAbsent(cap, ignored -> new int[6]);
            counts[0]++;
            if (inTable) counts[1]++;
            if (ce) counts[2]++;
            if (ch) counts[3]++;
            if (re) counts[4]++;
            if (rh) counts[5]++;
            // Only a stack whose catalogue state is in the table can lose its row through the copy.
            if (inTable && !ch) {
                copyMissByClass.computeIfAbsent(type, ignored -> new int[1])[0]++;
                JsonArray list = samples.computeIfAbsent("copy:" + type, ignored -> new JsonArray());
                if (list.size() < limit) list.add(sample(ingredient, copy, base, copied));
            }
            if (inTable && !rh) {
                roundMissByClass.computeIfAbsent(type, ignored -> new int[1])[0]++;
                JsonArray list = samples.computeIfAbsent("round:" + type, ignored -> new JsonArray());
                if (list.size() < limit) list.add(sample(ingredient, round, base, read));
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("elapsedMs", (System.nanoTime() - started) / 1_000_000);
        result.addProperty("ingredients", ingredients.size());
        result.addProperty("captured", total);
        result.addProperty("originalInTable", original);
        result.addProperty("copyKeyEqual", copyEqual);
        result.addProperty("copyInTable", copyHit);
        result.addProperty("copyOfCopyKeyEqual", copyOfCopyEqual);
        result.addProperty("roundTripKeyEqual", roundEqual);
        result.addProperty("roundTripInTable", roundHit);
        result.add("byCopyCapNbt", GSON.toJsonTree(byCopyCap));
        result.add("copyMissByClass", GSON.toJsonTree(copyMissByClass));
        result.add("roundTripMissByClass", GSON.toJsonTree(roundMissByClass));
        result.add("failures", GSON.toJsonTree(failures));
        result.add("samples", GSON.toJsonTree(samples));
        return result;
    }

    private static JsonObject sample(net.minecraft.item.ItemStack ingredient, net.minecraft.item.ItemStack derived,
            Object base, Object derivedKey) throws Exception {
        JsonObject row = new JsonObject();
        row.addProperty("id", String.valueOf(ingredient.func_77973_b().getRegistryName()));
        row.addProperty("damage", ingredient.func_77960_j());
        row.addProperty("nbt", String.valueOf(ingredient.func_77978_p()));
        row.addProperty("capNbt", String.valueOf(NativeAccess.get(ingredient, "capNBT")));
        row.addProperty("derivedNbt", String.valueOf(derived.func_77978_p()));
        row.addProperty("derivedCapNbt", String.valueOf(NativeAccess.get(derived, "capNBT")));
        row.add("key", header((byte[]) NativeAccess.call(base, "bytes")));
        row.add("derivedKey", header((byte[]) NativeAccess.call(derivedKey, "bytes")));
        return row;
    }

    private static JsonObject held(JsonObject parameters) throws Exception {
        int slot = parameters.has("slot") ? parameters.get("slot").getAsInt() : 0;
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.func_71410_x();
        if (client.field_71441_e == null || client.field_71439_g == null) throw new IllegalStateException("World required");
        Object binding = binding(), table = NativeAccess.get(binding, "table"), registry = NativeAccess.get(binding, "registry");
        net.minecraft.item.ItemStack synced = client.field_71439_g.field_71071_by.func_70301_a(slot);
        if (synced.func_190926_b()) throw new IllegalStateException("Empty slot " + slot);
        // Derive every variant before any tooltip touches the synced stack's lifecycle.
        Map<String, net.minecraft.item.ItemStack> variants = new LinkedHashMap<>();
        net.minecraft.item.ItemStack copy = synced.func_77946_l();
        variants.put("copy", copy);
        variants.put("copyOfCopy", copy.func_77946_l());
        variants.put("split", synced.func_77946_l().func_77979_a(1));
        variants.put("nbtRoundTrip", roundTrip(synced));
        variants.put("synced", synced);
        Class<?> runtime = NativeAccess.type(PRODUCT + "NameTableRuntime");
        JsonObject result = new JsonObject();
        result.addProperty("slot", slot);
        result.addProperty("id", String.valueOf(synced.func_77973_b().getRegistryName()));
        result.addProperty("nbt", String.valueOf(synced.func_77978_p()));
        JsonObject rows = new JsonObject();
        for (Map.Entry<String, net.minecraft.item.ItemStack> entry : variants.entrySet()) {
            net.minecraft.item.ItemStack stack = entry.getValue();
            JsonObject row = new JsonObject();
            row.addProperty("capNbtBefore", String.valueOf(NativeAccess.get(stack, "capNBT")));
            for (String pass : new String[] {"first", "repeat"}) {
                Map<?, ?> before = (Map<?, ?>) NativeAccess.call(runtime, "status");
                java.util.List<String> tooltip = stack.func_82840_a(client.field_71439_g,
                        net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL);
                Map<?, ?> after = (Map<?, ?>) NativeAccess.call(runtime, "status");
                row.add(pass + "Tooltip", GSON.toJsonTree(tooltip));
                row.addProperty(pass + "TableHits",
                        ((Number) after.get("hits")).longValue() - ((Number) before.get("hits")).longValue());
                JsonArray secondary = new JsonArray();
                for (int index = 1; index < tooltip.size(); index++) {
                    String line = tooltip.get(index);
                    if (line.startsWith("§e§o") && line.endsWith("§r"))
                        secondary.add(line.replaceAll("§.", "").trim());
                }
                row.add(pass + "SecondaryRows", secondary);
            }
            try {
                Object key = NativeAccess.call(NativeAccess.type(PRODUCT + "RawStackState"), "capture", stack, registry);
                row.add("keyAfter", header((byte[]) NativeAccess.call(key, "bytes")));
                row.addProperty("keyAfterInTable", (Boolean) NativeAccess.call(table, "contains", key));
            } catch (java.lang.reflect.InvocationTargetException error) {
                row.addProperty("keyFailure", String.valueOf(error.getCause()));
            }
            row.addProperty("capNbtAfter", String.valueOf(NativeAccess.get(stack, "capNBT")));
            rows.add(entry.getKey(), row);
        }
        result.add("variants", rows);
        return result;
    }

    /** Raw key header: item id, count, damage, capability state and descriptors, plus the NBT tail. */
    private static JsonObject header(byte[] bytes) throws Exception {
        java.io.DataInputStream input = new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes));
        JsonObject result = new JsonObject();
        result.addProperty("schema", input.readInt());
        result.addProperty("id", text(input));
        result.addProperty("count", input.readInt());
        result.addProperty("damage", input.readInt());
        int capabilities = input.readUnsignedByte();
        result.addProperty("capabilities", capabilities);
        if (capabilities >= 8) {
            JsonArray providers = new JsonArray();
            for (int index = input.readInt(); index > 0; index--) providers.add(text(input));
            result.add("providers", providers);
        }
        byte[] tail = new byte[input.available()];
        input.readFully(tail);
        StringBuilder hex = new StringBuilder();
        for (byte value : tail) hex.append(String.format("%02x", value));
        result.addProperty("nbtTail", hex.length() > 160 ? hex.substring(0, 160) + "..." : hex.toString());
        result.addProperty("keyBytes", bytes.length);
        return result;
    }

    private static String text(java.io.DataInputStream input) throws java.io.IOException {
        int length = input.readInt();
        if (length < 0 || length > 65_536) throw new java.io.IOException("Key text length");
        char[] value = new char[length];
        for (int index = 0; index < length; index++) value[index] = input.readChar();
        return new String(value);
    }
}
