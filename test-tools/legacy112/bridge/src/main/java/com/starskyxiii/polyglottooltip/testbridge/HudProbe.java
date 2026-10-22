package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class HudProbe {
    private static final Map<Object, JsonObject> origins = new WeakHashMap<>();
    private static final Deque<JsonObject> top = new ArrayDeque<>(), waila = new ArrayDeque<>();
    private static World world;
    private static JsonObject frame = new JsonObject(), last = new JsonObject(), target;
    private static long serial, registrations, deadline;
    private static CompletableFuture<JsonElement> pending;
    private static JsonObject request;
    private static World captureWorld;
    private static Entity capturePlayer;
    private static int treeNodes;
    private HudProbe() { }

    public static void startFrame() {
        Minecraft client = Minecraft.func_71410_x();
        HudDelivery.maintain();
        HudBoundaryProbe.maintain();
        if (world != client.field_71441_e) { origins.clear(); WailaNetworkTrace.clear(); world = client.field_71441_e; }
        top.clear(); waila.clear(); target = null;
        frame = new JsonObject(); frame.addProperty("serial", ++serial);
        frame.addProperty("world", System.identityHashCode(world)); frame.addProperty("registrations", registrations);
        frame.add("top", new JsonArray()); frame.add("waila", new JsonArray());
        frame.add("aim", hit(client.field_71476_x));
        if (client.field_71439_g != null) {
            JsonObject pose = new JsonObject(); pose.addProperty("x", client.field_71439_g.field_70165_t);
            pose.addProperty("y", client.field_71439_g.field_70163_u); pose.addProperty("z", client.field_71439_g.field_70161_v);
            pose.addProperty("yaw", client.field_71439_g.field_70177_z); pose.addProperty("pitch", client.field_71439_g.field_70125_A);
            frame.add("pose", pose);
        }
    }

    public static void endFrame() {
        safe(() -> frame.add("aim", hit(Minecraft.func_71410_x().field_71476_x)));
        last = frame;
        if (pending == null) return;
        if (pending.isDone()) { clear(); return; }
        Minecraft client = Minecraft.func_71410_x();
        try {
            if (captureWorld != client.field_71441_e || capturePlayer != client.field_71439_g || client.field_71462_r != null || client.func_147113_T()) throw new IllegalStateException("HUD context changed");
            if (System.nanoTime() > deadline) throw new IllegalStateException("Native HUD capture timed out: " + last);
            String kind = request.get("kind").getAsString();
            JsonArray rows = frame.getAsJsonArray(kind);
            if (frame.has("error")) throw new IllegalStateException(frame.get("error").getAsString());
            JsonObject delivery = HudDelivery.observe();
            if (delivery.has("failure")) throw new IllegalStateException(delivery.get("failure").getAsString());
            if (request.has("aim") && !HudDelivery.contains(frame.getAsJsonObject("aim"), request.getAsJsonObject("aim"))) return;
            if (request.has("held") && delivery.get("held").getAsInt() < request.get("held").getAsInt()) return;
            JsonObject match = null;
            for (JsonElement row : rows) if (matches(row.getAsJsonObject())) { match = row.getAsJsonObject(); break; }
            boolean negative = request.has("negative") && request.get("negative").getAsBoolean();
            if (negative) {
                if (!frame.has(kind + "HudComplete")) return;
                for (JsonElement row : rows) if (row.getAsJsonObject().has("complete") && row.getAsJsonObject().get("complete").getAsBoolean()) return;
                if (!top.isEmpty() || !waila.isEmpty()) throw new IllegalStateException("Incomplete HUD observer scope");
            } else if (match == null) return;
            String name = request.get("name").getAsString();
            ScreenShotHelper.func_148259_a(client.field_71412_D, name, client.field_71443_c, client.field_71440_d, client.func_147110_a());
            File file = new File(client.field_71412_D, "screenshots/" + name);
            if (!file.isFile() || file.length() == 0) throw new IllegalStateException("HUD screenshot missing");
            JsonObject result = new JsonObject(); result.add("frame", frame); result.add("predicate", request);
            result.addProperty("path", file.getAbsolutePath()); result.addProperty("negative", negative);
            result.add("delivery", delivery);
            result.add("wailaPackets", WailaNetworkTrace.snapshot());
            if (match != null) result.add("matched", match);
            pending.complete(new com.google.gson.JsonParser().parse(result.toString())); clear();
        } catch (Throwable error) { pending.completeExceptionally(error); clear(); }
    }

    private static boolean matches(JsonObject row) {
        if (!row.has("complete") || !row.get("complete").getAsBoolean()) return false;
        if (request.has("text")) {
            String expected = request.get("text").getAsString(); boolean found = false;
            for (JsonElement text : row.getAsJsonArray("text")) if (text.getAsJsonObject().get("text").getAsString().contains(expected)) found = true;
            if (!found) return false;
        }
        if (request.has("visible") && (!row.has("visible") || row.get("visible").getAsBoolean() != request.get("visible").getAsBoolean())) return false;
        if (request.has("fading") && request.get("fading").getAsBoolean()
                && (!row.has("alpha") || row.get("alpha").getAsFloat() <= 0.001f || row.get("alpha").getAsFloat() >= 1f)) return false;
        if (request.has("identity") && row.get("identity").getAsInt() != request.get("identity").getAsInt()) return false;
        if (request.has("origin") && (!row.has("origin") || !HudDelivery.contains(row.getAsJsonObject("origin"), request.getAsJsonObject("origin")))) return false;
        if (request.has("target") && (!row.has("target") || !HudDelivery.contains(row.getAsJsonObject("target"), request.getAsJsonObject("target")))) return false;
        if (request.has("packet") && request.get("packet").getAsBoolean() && !row.has("packet")) return false;
        return true;
    }

    public static void request(JsonObject params, CompletableFuture<JsonElement> future) {
        if (pending != null && !pending.isDone()) throw new IllegalStateException("HUD capture pending");
        Minecraft client = Minecraft.func_71410_x();
        String name = params.get("name").getAsString(), kind = params.get("kind").getAsString();
        for (Map.Entry<String, JsonElement> entry : params.entrySet()) if (!java.util.Arrays.asList("name", "kind", "text", "visible", "fading", "identity", "origin", "target", "packet", "held", "negative", "aim").contains(entry.getKey())) throw new IllegalArgumentException("Unknown HUD predicate " + entry.getKey());
        if (!name.matches("[a-zA-Z0-9_-]{1,100}\\.png") || (!kind.equals("top") && !kind.equals("waila"))) throw new IllegalArgumentException("Invalid HUD capture");
        if (client.field_71441_e == null || client.field_71439_g == null || client.field_71462_r != null) throw new IllegalStateException("HUD requires an active world without a screen");
        if (new File(client.field_71412_D, "screenshots/" + name).exists()) throw new IllegalArgumentException("HUD screenshot exists");
        if (params.has("negative") && params.get("negative").getAsBoolean() && !params.has("aim")) throw new IllegalArgumentException("Negative HUD capture requires native aim");
        request = params; pending = future; captureWorld = client.field_71441_e; capturePlayer = client.field_71439_g; deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    }

    public static JsonObject observe() { JsonObject result = new JsonObject(); result.add("frame", new com.google.gson.JsonParser().parse(last.toString())); result.add("delivery", HudDelivery.observe()); result.add("wailaPackets", WailaNetworkTrace.snapshot()); return result; }
    private static void clear() { pending = null; request = null; captureWorld = null; capturePlayer = null; }
    public static void topHud() { target = null; }
    public static void topHudEnd() { safe(() -> frame.addProperty("topHudComplete", true)); }
    public static void wailaHudEnd() { safe(() -> frame.addProperty("wailaHudComplete", true)); }
    public static void topTarget(RayTraceResult hit) { safe(() -> target = hit(hit)); }
    public static void registeredBlock(int dimension, BlockPos position, Object info) {
        safe(() -> {
            if (info == null || position == null) return;
            JsonObject origin = position(position); origin.addProperty("dimension", dimension); origin.addProperty("world", System.identityHashCode(Minecraft.func_71410_x().field_71441_e));
            origins.put(info, origin); registrations++;
            if (HudDelivery.registration() != null) origin.add("packet", HudDelivery.registration());
        });
    }
    public static void registeredEntity(UUID uuid, Object info) {
        safe(() -> {
            if (info == null || uuid == null) return;
            JsonObject origin = new JsonObject(); origin.addProperty("uuid", uuid.toString()); origin.addProperty("world", System.identityHashCode(Minecraft.func_71410_x().field_71441_e));
            origins.put(info, origin); registrations++;
            if (HudDelivery.registration() != null) origin.add("packet", HudDelivery.registration());
        });
    }

    public static void topBegin(Object info, boolean visible) {
        safe(() -> {
            JsonObject row = trace(info); row.addProperty("visible", visible);
            if (target != null) row.add("target", target); if (origins.containsKey(info)) row.add("origin", origins.get(info));
            if (origins.containsKey(info) && origins.get(info).has("packet")) row.add("packet", origins.get(info).get("packet"));
            treeNodes = 0; row.add("tree", tree(info, 0));
            bounded(frame.getAsJsonArray("top"), row); top.push(row);
        });
    }
    public static void topEnd() { safe(() -> { if (!top.isEmpty()) { JsonObject row = top.pop(); row.addProperty("complete", row.has("drawn")); } }); }
    public static void topWidth(int value) { safe(() -> { if (!top.isEmpty()) top.peek().addProperty("width", value); }); }
    public static void topHeight(int value) { safe(() -> { if (!top.isEmpty()) top.peek().addProperty("height", value); }); }
    public static void topPosition(int x, int y) { safe(() -> { if (!top.isEmpty()) { top.peek().addProperty("x", x); top.peek().addProperty("y", y); } }); }
    public static void topDrawn() { safe(() -> { if (!top.isEmpty()) top.peek().addProperty("drawn", true); }); }
    public static void topFade(float alpha) { safe(() -> { if (!top.isEmpty()) top.peek().addProperty("alpha", alpha); }); }
    public static void topLayout(Object layout) { safe(() -> { if (!top.isEmpty()) { JsonObject row = new JsonObject(); row.addProperty("identity", System.identityHashCode(layout)); for (String field : new String[]{"width", "height", "headerHeight", "columnsWidth", "columnsHeight", "scrolling"}) row.addProperty(field, String.valueOf(NativeAccess.get(layout, field))); top.peek().add("layout", row); } }); }
    public static void topText(int width, String text, int x, int y) { safe(() -> { if (!top.isEmpty()) { JsonObject row = text(text, x, y); row.addProperty("width", width); bounded(top.peek().getAsJsonArray("text"), row); } }); }
    public static int topMeasured(Object font, String text, int width) {
        safe(() -> { if (!top.isEmpty()) { JsonObject row = new JsonObject(); row.addProperty("text", text); row.addProperty("width", width); bounded(top.peek().getAsJsonArray("measured"), row); } });
        return width;
    }
    public static void topBox(int x, int y, int width, int height) {
        safe(() -> { if (!top.isEmpty()) { JsonObject row = new JsonObject(); row.addProperty("x", x); row.addProperty("y", y); row.addProperty("width", width); row.addProperty("height", height); bounded(top.peek().getAsJsonArray("boxes"), row); } });
    }

    public static void wailaBegin(Object tooltip) {
        safe(() -> {
            JsonObject row = trace(tooltip); JsonArray lines = new JsonArray();
            for (Object line : (List<?>) NativeAccess.get(tooltip, "textData")) lines.add(String.valueOf(line));
            row.add("lines", lines);
            for (String field : new String[]{"w", "h", "x", "y", "ty", "offsetX", "maxStringW"}) row.addProperty(field, (Number) NativeAccess.get(tooltip, field));
            for (String field : new String[]{"sizes", "columnsWidth", "columnsPos", "additionalHeights"}) row.addProperty(field, String.valueOf(NativeAccess.get(tooltip, field)));
            Object accessor = NativeAccess.get(tooltip, "accessor");
            row.add("remoteTag", WailaNetworkTrace.tag((net.minecraft.nbt.NBTTagCompound) NativeAccess.get(accessor, "remoteNbt")));
            Entity entity = (Entity) NativeAccess.call(accessor, "getEntity");
            if (entity != null) row.add("origin", entity(entity));
            else { BlockPos position = (BlockPos) NativeAccess.call(accessor, "getPosition"); if (position != null) row.add("origin", position(position)); }
            row.addProperty("passes", 0); bounded(frame.getAsJsonArray("waila"), row); waila.push(row);
        });
    }
    public static void wailaDrawn() { safe(() -> { if (!waila.isEmpty()) { JsonObject row = waila.peek(); row.addProperty("passes", row.get("passes").getAsInt() + 1); } }); }
    public static void wailaText(String value, int x, int y) { safe(() -> { if (!waila.isEmpty()) bounded(waila.peek().getAsJsonArray("text"), text(value, x, y)); }); }
    public static void wailaEnd() { safe(() -> { if (!waila.isEmpty()) { JsonObject row = waila.pop(); row.addProperty("complete", row.get("passes").getAsInt() == 2); } }); }
    public static void wailaBox(int x, int y, int width, int height) { safe(() -> { if (!waila.isEmpty()) { JsonObject row = new JsonObject(); row.addProperty("x", x); row.addProperty("y", y); row.addProperty("width", width); row.addProperty("height", height); bounded(waila.peek().getAsJsonArray("boxes"), row); } }); }

    private static JsonObject trace(Object value) {
        JsonObject row = new JsonObject(); row.addProperty("identity", System.identityHashCode(value)); row.addProperty("class", value.getClass().getName());
        row.add("text", new JsonArray()); row.add("measured", new JsonArray()); row.add("boxes", new JsonArray()); return row;
    }
    private static JsonObject text(String value, int x, int y) { JsonObject row = new JsonObject(); row.addProperty("text", value); row.addProperty("x", x); row.addProperty("y", y); return row; }
    private static JsonObject tree(Object value, int depth) throws Exception {
        if (++treeNodes > 512) throw new IllegalStateException("HUD tree exceeds node bound");
        JsonObject row = new JsonObject(); row.addProperty("class", value.getClass().getName()); row.addProperty("identity", System.identityHashCode(value));
        if (depth >= 8) throw new IllegalStateException("HUD tree exceeds depth bound");
        List<?> children = null;
        try { children = (List<?>) NativeAccess.get(value, "children"); } catch (NoSuchFieldException ignored) { }
        if (children == null) try { children = (List<?>) NativeAccess.get(value, "elements"); } catch (NoSuchFieldException ignored) { }
        if (children != null) { JsonArray list = new JsonArray(); for (Object child : children) { if (list.size() >= 128) throw new IllegalStateException("HUD tree exceeds capture bound"); list.add(tree(child, depth + 1)); } row.add("children", list); }
        for (String field : new String[]{"text", "entityName", "fallbackName", "fixedName"}) {
            try { Object fieldValue = NativeAccess.get(value, field); String text = fieldValue == null ? null : String.valueOf(fieldValue); if (text != null && text.length() > 16384) throw new IllegalStateException("HUD field exceeds text bound"); row.addProperty(field, text); } catch (NoSuchFieldException ignored) { }
        }
        return row;
    }
    private static JsonObject hit(RayTraceResult value) {
        JsonObject row = new JsonObject(); if (value == null) return row;
        row.addProperty("type", value.field_72313_a.name());
        if (value.field_72308_g != null) row.add("entity", entity(value.field_72308_g));
        else if (value.func_178782_a() != null) row.add("block", position(value.func_178782_a()));
        return row;
    }
    private static JsonObject entity(Entity value) { JsonObject row = new JsonObject(); row.addProperty("uuid", value.func_110124_au().toString()); row.addProperty("id", value.func_145782_y()); row.addProperty("class", value.getClass().getName()); return row; }
    private static JsonObject position(BlockPos value) { JsonObject row = new JsonObject(); row.addProperty("x", value.func_177958_n()); row.addProperty("y", value.func_177956_o()); row.addProperty("z", value.func_177952_p()); return row; }
    private static void bounded(JsonArray rows, JsonElement value) { if (rows.size() >= 512) throw new IllegalStateException("HUD trace exceeds capture bound"); rows.add(value); }
    private interface Action { void run() throws Exception; }
    private static void safe(Action action) { try { action.run(); } catch (Throwable error) { frame.addProperty("error", error.toString()); } }
}
