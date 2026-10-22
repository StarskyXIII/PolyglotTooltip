package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.NBTTagCompound;
import java.util.ArrayDeque;
import java.util.Deque;

public final class WailaNetworkTrace {
    private static final Deque<JsonObject> events = new ArrayDeque<>();
    private static long sequence;
    private WailaNetworkTrace() { }
    public static synchronized void receive(Object packet) {
        try { record("receive", (NBTTagCompound) NativeAccess.get(packet, "tag")); }
        catch (Throwable error) { JsonObject row = new JsonObject(); row.addProperty("error", error.toString()); append(row); }
    }
    public static synchronized void assigned(NBTTagCompound tag) { try { record("assigned", tag); } catch (Throwable error) { JsonObject row = new JsonObject(); row.addProperty("error", error.toString()); append(row); } }
    private static void record(String event, NBTTagCompound tag) {
        JsonObject row = tag(tag); row.addProperty("event", event); row.addProperty("sequence", ++sequence); append(row);
    }
    public static JsonObject tag(NBTTagCompound tag) {
        JsonObject row = new JsonObject(); if (tag == null) return row;
        row.addProperty("identity", System.identityHashCode(tag));
        for (String key : new String[]{"WailaX", "WailaY", "WailaZ", "WailaEntityID"}) if (tag.func_74764_b(key)) row.addProperty(key, tag.func_74762_e(key));
        String text = tag.toString(); if (text.length() > 32768) throw new IllegalStateException("HWYLA packet tag exceeds bound"); row.addProperty("tag", text); return row;
    }
    private static void append(JsonObject row) { events.addLast(row); while (events.size() > 128) events.removeFirst(); }
    public static synchronized JsonArray snapshot() { JsonArray result = new JsonArray(); for (JsonObject row : events) result.add(row); return result; }
    public static synchronized void clear() { events.clear(); }
}
