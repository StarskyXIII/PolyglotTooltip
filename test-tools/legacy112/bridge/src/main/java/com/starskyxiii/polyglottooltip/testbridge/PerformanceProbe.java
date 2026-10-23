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
        throw new IllegalArgumentException("Unknown performance action");
    }

    private static JsonObject state() {
        JsonObject result = new JsonObject();
        result.addProperty("monitoring", System.nanoTime() <= until);
        result.addProperty("ticks", ticks.size());
        result.addProperty("frames", frames.size());
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
