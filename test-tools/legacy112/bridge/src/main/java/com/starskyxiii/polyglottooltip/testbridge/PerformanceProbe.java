package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Bounded diagnostics for an explicitly owned test client; never part of the shipped mod. */
final class PerformanceProbe {
    private static final String PRODUCT = "com.starskyxiii.polyglottooltip.legacy.";
    private static final Gson GSON = new Gson();
    private static final List<Double> ticks = new ArrayList<>(), frames = new ArrayList<>(), frameIntervals = new ArrayList<>();
    private static long tickStart, frameStart, previousFrameStart, until, monitorStarted, monitorStartedAt;
    private PerformanceProbe() { }

    static void tick(boolean start) {
        if (System.nanoTime() > until) return;
        if (start) tickStart = System.nanoTime();
        else if (tickStart != 0 && ticks.size() < 1200) ticks.add((System.nanoTime() - tickStart) / 1_000_000.0);
    }
    static void frame(boolean start) {
        if (System.nanoTime() > until) return;
        if (start) {
            frameStart = System.nanoTime();
            if (previousFrameStart != 0 && frameIntervals.size() < 100000)
                frameIntervals.add((frameStart - previousFrameStart) / 1_000_000.0);
            previousFrameStart = frameStart;
        } else if (frameStart != 0 && frames.size() < 100000) frames.add((System.nanoTime() - frameStart) / 1_000_000.0);
    }

    static JsonObject run(JsonObject params) throws Exception {
        String action = params.get("action").getAsString();
        if (action.equals("monitor")) {
            ticks.clear(); frames.clear(); frameIntervals.clear(); tickStart = frameStart = previousFrameStart = 0;
            monitorStarted = System.nanoTime(); monitorStartedAt = System.currentTimeMillis(); until = monitorStarted + 60_000_000_000L;
            return state();
        }
        if (action.equals("report")) {
            long requestedAt = System.nanoTime(), deadline = until;
            long ended = Math.min(requestedAt, deadline);
            until = 0;
            JsonObject result = state(); result.add("ticksMs", statistics(ticks)); result.add("renderMs", statistics(frames));
            result.add("frameIntervalMs", statistics(frameIntervals));
            result.addProperty("startedAt", monitorStartedAt); result.addProperty("elapsedMs", (ended - monitorStarted) / 1_000_000.0);
            result.addProperty("wallElapsedMs", (requestedAt - monitorStarted) / 1_000_000.0);
            result.addProperty("expired", deadline == 0 || requestedAt > deadline);
            result.addProperty("capped", ticks.size() >= 1200 || frames.size() >= 100000 || frameIntervals.size() >= 100000);
            if (params.has("raw") && params.get("raw").getAsBoolean()) {
                result.add("rawTicksMs", GSON.toJsonTree(ticks)); result.add("rawRenderMs", GSON.toJsonTree(frames));
                result.add("rawFrameIntervalsMs", GSON.toJsonTree(frameIntervals));
            }
            return result;
        }
        if (action.equals("state")) return state();
        if (action.equals("settings")) {
            long start = System.nanoTime();
            JsonObject result = CoreProbe.configure(params);
            result.addProperty("saveMillis", (System.nanoTime() - start) / 1_000_000.0);
            result.add("immediate", state());
            return result;
        }
        if (action.equals("hover")) {
            int repetitions = params.has("repetitions") ? params.get("repetitions").getAsInt() : 32;
            if (repetitions < 1 || repetitions > 64) throw new IllegalArgumentException("Use 1..64 repetitions");
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(params.get("item").getAsString()));
            if (item == null) throw new IllegalArgumentException("Unknown item");
            ItemStack stack = new ItemStack(item, 1, params.has("metadata") ? params.get("metadata").getAsInt() : 0);
            if (params.has("nbt")) stack.func_77982_d(JsonToNBT.func_180713_a(params.get("nbt").getAsString()));
            NBTTagCompound before = stack.func_77955_b(new NBTTagCompound());
            List<Double> times = new ArrayList<>();
            List<String> tooltip = Collections.emptyList();
            for (int i = 0; i < repetitions; i++) {
                long start = System.nanoTime();
                tooltip = stack.func_82840_a(Minecraft.func_71410_x().field_71439_g, ITooltipFlag.TooltipFlags.NORMAL);
                times.add((System.nanoTime() - start) / 1_000_000.0);
            }
            JsonObject result = statistics(times);
            result.add("tooltip", GSON.toJsonTree(tooltip));
            result.addProperty("unchanged", before.equals(stack.func_77955_b(new NBTTagCompound())));
            return result;
        }
        if (action.equals("fallbackSample")) return fallbackSample(params);
        throw new IllegalArgumentException("Unknown performance action");
    }

    /**
     * For each named item class (or superclass), the first recipe-viewer stack is hovered as listed and with one
     * extra NBT byte. The listed stack is the table's own state; the extra byte keeps every name input but misses the
     * table, so that tooltip comes from the fallback resolver. Both tooltips and their timings are returned.
     */
    private static JsonObject fallbackSample(JsonObject params) throws Exception {
        int repetitions = params.has("repetitions") ? params.get("repetitions").getAsInt() : 16;
        if (repetitions < 1 || repetitions > 64) throw new IllegalArgumentException("Use 1..64 repetitions");
        java.util.Collection<ItemStack> ingredients = mezz.jei.Internal.getIngredientRegistry().getAllIngredients(ItemStack.class);
        JsonObject result = new JsonObject();
        for (com.google.gson.JsonElement element : params.getAsJsonArray("classes")) {
            String name = element.getAsString();
            ItemStack found = null;
            for (ItemStack stack : ingredients) {
                for (Class<?> type = stack.func_77973_b().getClass(); type != null && found == null; type = type.getSuperclass())
                    if (type.getName().equals(name)) found = stack;
                if (found != null) break;
            }
            JsonObject row = new JsonObject();
            if (found == null) { row.addProperty("missing", true); result.add(name, row); continue; }
            row.addProperty("item", String.valueOf(found.func_77973_b().getRegistryName()));
            row.addProperty("metadata", found.func_77960_j());
            row.addProperty("nbt", found.func_77942_o() ? found.func_77978_p().toString() : null);
            ItemStack listed = found.func_77946_l(), miss = found.func_77946_l();
            NBTTagCompound tag = miss.func_77942_o() ? miss.func_77978_p() : new NBTTagCompound();
            tag.func_74774_a("polyglotProbe", (byte) 1); miss.func_77982_d(tag);
            row.add("table", hover(listed, repetitions));
            row.add("miss", hover(miss, repetitions));
            try {
                Object observation = NativeAccess.call(NativeAccess.type(PRODUCT + "minecraft.NativeTableObservation"), "capture", listed);
                row.addProperty("covered", observation != null && (Boolean) NativeAccess.call(observation, "covered"));
            } catch (Exception unavailable) { row.addProperty("covered", "unavailable"); }
            result.add(name, row);
        }
        return result;
    }

    private static JsonObject hover(ItemStack stack, int repetitions) {
        List<Double> times = new ArrayList<>();
        List<String> tooltip = Collections.emptyList();
        try {
            for (int i = 0; i < repetitions; i++) {
                long start = System.nanoTime();
                tooltip = stack.func_82840_a(Minecraft.func_71410_x().field_71439_g, ITooltipFlag.TooltipFlags.NORMAL);
                times.add((System.nanoTime() - start) / 1_000_000.0);
            }
        } catch (RuntimeException | LinkageError error) {
            // Some mods' own name getters throw for listed stacks; record it instead of failing the whole sample.
            JsonObject failed = new JsonObject(); failed.addProperty("error", error.toString()); return failed;
        }
        JsonObject result = statistics(times);
        result.add("tooltip", GSON.toJsonTree(tooltip));
        return result;
    }

    private static JsonObject state() throws Exception {
        JsonObject result = new JsonObject();
        Class<?> adapter = NativeAccess.type(PRODUCT + "minecraft.MinecraftLanguageAdapter");
        Object language = NativeAccess.call(adapter, "snapshot");
        result.addProperty("ready", (Boolean) NativeAccess.call(language, "ready"));
        result.addProperty("generation", (Number) NativeAccess.get(language, "generation"));
        result.addProperty("nativeNameEpoch", (Number) NativeAccess.get(language, "nativeNameEpoch"));
        result.addProperty("snapshot", System.identityHashCode(language));
        result.addProperty("primaryTable", System.identityHashCode(NativeAccess.get(language, "primaryTable")));
        result.add("settings", GSON.toJsonTree(NativeAccess.call(NativeAccess.type(PRODUCT + "Config"), "get")));
        JsonArray languages = new JsonArray();
        for (Object table : (List<?>) NativeAccess.get(language, "tables")) languages.add((String) NativeAccess.call(table, "code"));
        result.add("languages", languages);
        Object filter = mezz.jei.Internal.getIngredientFilter();
        if (filter != null) {
            for (String field : new String[]{"elementSearch", "searchTree"}) {
                try { result.addProperty(field, System.identityHashCode(NativeAccess.get(filter, field))); }
                catch (NoSuchFieldException ignored) { }
            }
            try {
                Object aliases = NativeAccess.call(filter, "polyglot$aliases");
                if (aliases != null) {
                    result.addProperty("aliasReady", NativeAccess.get(aliases, "index") != null);
                    result.addProperty("aliasCursor", (Number) NativeAccess.get(aliases, "cursor"));
                    Object source = NativeAccess.get(aliases, "source");
                    if (source != null) result.addProperty("aliasSize", ((List<?>) NativeAccess.get(source, "elements")).size());
                    try {
                        result.addProperty("aliasIndexed", (Number) NativeAccess.call(aliases, "tableIndexed"));
                        result.addProperty("aliasFallback", (Number) NativeAccess.call(aliases, "tableFallback"));
                        result.addProperty("aliasMissing", (Number) NativeAccess.call(aliases, "tableMissing"));
                    } catch (NoSuchMethodException ignored) { }
                }
            } catch (NoSuchMethodException ignored) { }
        }
        try {
            Class<?> libraries = NativeAccess.type(PRODUCT + "bootstrap.NameLibrarySource");
            result.addProperty("libraryPending", (Boolean) NativeAccess.call(libraries, "preparationPending"));
            result.addProperty("libraryRevision", (Number) NativeAccess.call(libraries, "preparationRevision"));
        } catch (NoSuchMethodException ignored) { }
        return result;
    }

    private static JsonObject statistics(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values); Collections.sort(sorted);
        JsonObject result = new JsonObject(); result.addProperty("count", sorted.size());
        if (!sorted.isEmpty()) {
            result.addProperty("median", sorted.get(sorted.size() / 2));
            result.addProperty("p95", sorted.get(Math.min(sorted.size() - 1, (int) (sorted.size() * .95))));
            result.addProperty("p99", sorted.get(Math.min(sorted.size() - 1, (int) (sorted.size() * .99))));
            result.addProperty("max", sorted.get(sorted.size() - 1));
        }
        return result;
    }
}
