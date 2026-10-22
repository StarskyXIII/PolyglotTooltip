package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class HudDelivery {
    private static final Deque<Delivery> held = new ArrayDeque<>();
    private static final Deque<JsonObject> events = new ArrayDeque<>();
    private static JsonObject armed, current;
    private static World world;
    private static long sequence, deadline;
    private static String failure;
    private HudDelivery() { }

    public static JsonObject registration() { return current; }
    public static void block(int dimension, BlockPos position, Object info) {
        JsonObject target = new JsonObject(); target.addProperty("dimension", dimension);
        target.addProperty("x", position.func_177958_n()); target.addProperty("y", position.func_177956_o()); target.addProperty("z", position.func_177952_p());
        accept(new Delivery(target, info, dimension, position, null));
    }
    public static void entity(UUID uuid, Object info) {
        JsonObject target = new JsonObject(); target.addProperty("uuid", uuid.toString());
        accept(new Delivery(target, info, 0, null, uuid));
    }
    private static void accept(Delivery delivery) {
        maintain();
        delivery.record.addProperty("sequence", ++sequence); delivery.record.addProperty("identity", System.identityHashCode(delivery.info));
        delivery.record.add("target", delivery.target); events.addLast(delivery.record); while (events.size() > 128) events.removeFirst();
        if (armed != null && contains(delivery.target, armed)) {
            if (held.size() >= 32) { failure = "HUD packet hold exceeded bound"; release(); forward(delivery); }
            else { delivery.record.addProperty("held", true); held.addLast(delivery); }
        } else forward(delivery);
    }
    public static boolean contains(JsonObject actual, JsonObject expected) {
        for (java.util.Map.Entry<String, JsonElement> entry : expected.entrySet()) {
            if (!actual.has(entry.getKey())) return false;
            JsonElement value = actual.get(entry.getKey());
            if (entry.getValue().isJsonObject() ? !value.isJsonObject() || !contains(value.getAsJsonObject(), entry.getValue().getAsJsonObject()) : !value.equals(entry.getValue())) return false;
        }
        return true;
    }
    public static void maintain() {
        World now = Minecraft.func_71410_x().field_71441_e;
        if (world != now) { held.clear(); armed = null; current = null; events.clear(); world = now; failure = null; }
        if (armed != null && System.nanoTime() > deadline) { failure = "HUD packet hold deadline elapsed"; release(); }
    }
    private static void forward(Delivery delivery) {
        JsonObject previous = current; current = delivery.record;
        try {
            Class<?> renderer = NativeAccess.type("mcjty.theoneprobe.rendering.OverlayRenderer");
            if (delivery.uuid != null) NativeAccess.call(renderer, "registerProbeInfo", delivery.uuid, delivery.info);
            else NativeAccess.call(renderer, "registerProbeInfo", delivery.dimension, delivery.position, delivery.info);
            delivery.record.addProperty("forwarded", true);
        } catch (InvocationTargetException error) { HudDelivery.<RuntimeException>raise(error.getCause()); }
        catch (Exception error) { throw new IllegalStateException("Native TOP delivery could not be forwarded", error); }
        finally { current = previous; }
    }
    private static void release() { armed = null; while (!held.isEmpty()) forward(held.removeFirst()); }
    public static JsonObject command(JsonObject params) throws Exception {
        maintain(); String action = params.get("action").getAsString();
        if (action.equals("arm")) {
            if (armed != null || !held.isEmpty() || world == null) throw new IllegalStateException("Cannot arm HUD delivery hold");
            JsonObject candidate = params.getAsJsonObject("target"); Object cacheKey; String cacheName;
            if (candidate == null) throw new IllegalArgumentException("Missing HUD packet target");
            if (candidate.has("uuid") && candidate.entrySet().size() == 1) { cacheKey = UUID.fromString(candidate.get("uuid").getAsString()); cacheName = "cachedEntityInfo"; }
            else {
                if (candidate.entrySet().size() != 4) throw new IllegalArgumentException("Invalid HUD block target");
                for (String key : new String[]{"dimension", "x", "y", "z"}) if (!candidate.has(key) || !candidate.get(key).isJsonPrimitive() || !candidate.get(key).getAsJsonPrimitive().isNumber() || !candidate.get(key).getAsString().matches("-?[0-9]{1,9}")) throw new IllegalArgumentException("Invalid HUD coordinate " + key);
                cacheKey = org.apache.commons.lang3.tuple.Pair.of(candidate.get("dimension").getAsInt(), new BlockPos(candidate.get("x").getAsInt(), candidate.get("y").getAsInt(), candidate.get("z").getAsInt())); cacheName = "cachedInfo";
            }
            Minecraft client = Minecraft.func_71410_x();
            if (client.func_71401_C() == null || !client.func_71401_C().func_71218_a(0).func_72912_H().func_76065_j().startsWith("pt112-")) throw new IllegalStateException("Not an owned packet fixture world");
            java.util.Map<?, ?> cache = (java.util.Map<?, ?>) NativeAccess.get(NativeAccess.type("mcjty.theoneprobe.rendering.OverlayRenderer"), cacheName);
            if (cache.containsKey(cacheKey)) throw new IllegalStateException("HUD hold target already has a native cache entry");
            armed = new com.google.gson.JsonParser().parse(candidate.toString()).getAsJsonObject();
            failure = null; deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        } else if (action.equals("release")) release();
        else if (action.equals("cancel")) { held.clear(); armed = null; }
        else throw new IllegalArgumentException("Unknown HUD delay action");
        return observe();
    }
    public static JsonObject observe() {
        JsonObject result = new JsonObject(); result.addProperty("held", held.size()); result.addProperty("sequence", sequence);
        if (armed != null) result.add("armed", armed); if (failure != null) result.addProperty("failure", failure);
        JsonArray rows = new JsonArray(); for (JsonObject event : events) rows.add(new com.google.gson.JsonParser().parse(event.toString())); result.add("events", rows); return result;
    }
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void raise(Throwable error) throws T { throw (T) error; }
    private static final class Delivery {
        final JsonObject target, record = new JsonObject(); final Object info; final int dimension; final BlockPos position; final UUID uuid;
        Delivery(JsonObject target, Object info, int dimension, BlockPos position, UUID uuid) { this.target = target; this.info = info; this.dimension = dimension; this.position = position; this.uuid = uuid; }
    }
}
