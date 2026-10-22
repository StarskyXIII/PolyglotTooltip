package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;

public final class CarbonTrace {
    private static Object array;
    private static JsonArray events = new JsonArray();
    private static String error;
    private CarbonTrace() { }
    public static void reset() { array = null; events = new JsonArray(); error = null; }
    public static void bind(Object value) { array = value; }
    public static void callback(Object owner, Object node, String value) {
        if (owner != array) return;
        try {
            JsonObject event = base("callback"); event.addProperty("node", System.identityHashCode(node)); event.addProperty("value", value);
            event.addProperty("index", ((List<?>) NativeAccess.get(owner, "values")).indexOf(node)); append(event);
        } catch (Throwable failure) { error = failure.toString(); }
    }
    public static void returned(Object owner) {
        if (owner != array) return;
        try { JsonObject event = base("returned"); JsonArray values = new JsonArray(); for (Object value : (List<?>) NativeAccess.get(owner, "currentValues")) values.add(String.valueOf(value)); event.add("values", values); append(event); }
        catch (Throwable failure) { error = failure.toString(); }
    }
    public static void changed(net.minecraftforge.fml.client.event.ConfigChangedEvent event) {
        if (array == null || !event.getModID().equals("polyglotdevbridge")) return;
        try { JsonObject row = base(event.getClass().getSimpleName()); row.addProperty("result", event.getResult().name()); append(row); }
        catch (Throwable failure) { error = failure.toString(); }
    }
    private static JsonObject base(String name) { JsonObject result = new JsonObject(); result.addProperty("event", name); result.addProperty("array", System.identityHashCode(array)); return result; }
    private static void append(JsonObject value) { if (events.size() >= 4096) throw new IllegalStateException("Carbon callback trace exceeds bound"); events.add(value); }
    public static JsonObject snapshot() { JsonObject result = new JsonObject(); result.add("events", new com.google.gson.JsonParser().parse(events.toString())); if (error != null) result.addProperty("error", error); return result; }
}
