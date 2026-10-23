package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class HudSettings {
    private static final String[] fields = {"needsProbe", "isVisible", "showLiquids", "leftX", "topY", "rightX", "bottomY", "tooltipScale", "extendedInMain", "holdKeyToMakeVisible", "timeout", "waitingForServerTimeout", "showEntityModel", "showLiquidWithBucket", "boxResizeAnimation", "overlayFadeAnimation", "overlayFadeSpeed", "autoWrapMode"};
    private static final String[] general = {"waila.cfg.show", "waila.cfg.showmode", "waila.cfg.liquid", "waila.cfg.metadata", "waila.cfg.shiftblock", "waila.cfg.shiftents", "waila.cfg.hidefromdebug", "waila.cfg.hidefromlist"};
    private static final String[] modules = {"general.showents", "general.showhp"};
    private static Map<String, Object> saved;
    private static boolean busy;
    private static long serial;
    private static net.minecraft.world.World pendingWorld;
    private HudSettings() { }

    public static void apply(JsonObject params, CompletableFuture<JsonElement> future) throws Exception {
        Minecraft client = Minecraft.func_71410_x();
        if (busy && pendingWorld == client.field_71441_e) throw new IllegalStateException("HUD settings update pending");
        busy = false;
        MinecraftServer server = client.func_71401_C();
        net.minecraft.world.World world = client.field_71441_e; net.minecraft.entity.Entity player = client.field_71439_g;
        long operation = ++serial;
        if (server == null || !server.func_71218_a(0).func_72912_H().func_76065_j().startsWith("pt112-")) throw new IllegalStateException("HUD settings require owned world");
        Class<?> top = optional("mcjty.theoneprobe.config.Config");
        Class<?> handler = optional("mcp.mobius.waila.api.impl.ConfigHandler");
        Object waila = handler == null ? null : NativeAccess.call(handler, "instance");
        Class<?> overlay = handler == null ? null : optional("mcp.mobius.waila.config.OverlayConfig");
        if (top == null && waila == null) throw new IllegalStateException("Neither TOP nor Waila is present");
        Map<String, Object> before = snapshot(top, waila, overlay);
        boolean restore = params.has("restore") && params.get("restore").getAsBoolean();
        if (restore && saved == null) throw new IllegalStateException("No HUD settings snapshot");
        if (saved == null) saved = before;
        Object modName;
        if (restore) {
            if (top != null) {
                NativeAccess.call(top, "setProbeNeeded", saved.get("needsProbe")); NativeAccess.call(top, "setVisible", saved.get("isVisible")); NativeAccess.call(top, "setLiquids", saved.get("showLiquids"));
                NativeAccess.call(top, "setPos", saved.get("leftX"), saved.get("topY"), saved.get("rightX"), saved.get("bottomY")); NativeAccess.call(top, "setScale", saved.get("tooltipScale")); NativeAccess.call(top, "setExtendedInMain", saved.get("extendedInMain"));
                for (String key : fields) if (saved.containsKey(key)) NativeAccess.set(top, key, saved.get(key));
            }
            if (waila != null) {
                for (String key : general) NativeAccess.call(waila, "setConfig", "general", key, saved.get(key));
                for (String key : modules) NativeAccess.call(waila, "setConfig", "modules", key, saved.get(key));
                for (String key : new String[]{"waila.cfg.posx", "waila.cfg.posy"}) NativeAccess.call(waila, "setConfig", "general", key, saved.get(key));
                for (String key : new String[]{"scale", "posX", "posY"}) NativeAccess.set(overlay, key, saved.get("overlay." + key));
            }
            modName = saved.get("modName");
        } else {
            if (top != null) {
                NativeAccess.call(top, "setProbeNeeded", 0); NativeAccess.call(top, "setVisible", !params.has("top") || params.get("top").getAsBoolean());
                NativeAccess.call(top, "setLiquids", true); NativeAccess.call(top, "setPos", 8, 8, -1, -1); NativeAccess.call(top, "setScale", 1f); NativeAccess.call(top, "setExtendedInMain", false);
                NativeAccess.set(top, "holdKeyToMakeVisible", false);
                for (String key : new String[]{"timeout", "waitingForServerTimeout"}) if (params.has(key)) NativeAccess.set(top, key, params.get(key).getAsInt());
                for (String key : new String[]{"showEntityModel", "showLiquidWithBucket", "boxResizeAnimation", "overlayFadeAnimation"}) if (before.containsKey(key)) NativeAccess.set(top, key, params.has(key) && params.get(key).getAsBoolean());
                if (before.containsKey("overlayFadeSpeed")) NativeAccess.set(top, "overlayFadeSpeed", 0.1f);
                if (before.containsKey("autoWrapMode")) NativeAccess.set(top, "autoWrapMode", "NONE");
            }
            if (waila != null) {
                for (String key : general) {
                    boolean value = !key.contains("shift") && !key.contains("hidefrom");
                    if (key.equals("waila.cfg.show") && params.has("waila")) value = params.get("waila").getAsBoolean();
                    NativeAccess.call(waila, "setConfig", "general", key, value);
                }
                for (String key : modules) NativeAccess.call(waila, "setConfig", "modules", key, true);
                NativeAccess.call(waila, "setConfig", "general", "waila.cfg.posx", 5000); NativeAccess.call(waila, "setConfig", "general", "waila.cfg.posy", 1500);
                NativeAccess.set(overlay, "scale", 1f); NativeAccess.set(overlay, "posX", 5000); NativeAccess.set(overlay, "posY", 1500);
            }
            modName = top == null ? null : params.has("modName") ? NativeAccess.get(NativeAccess.type("mcjty.theoneprobe.api.IProbeConfig$ConfigMode"), params.get("modName").getAsBoolean() ? "NORMAL" : "NOT") : before.get("modName");
        }
        busy = true; pendingWorld = world;
        server.func_152344_a(() -> {
            try {
                if (future.isDone() || operation != serial || client.func_71401_C() != server || client.field_71441_e != world || client.field_71439_g != player) throw new IllegalStateException("HUD settings context changed");
                if (top != null) NativeAccess.call(NativeAccess.call(top, "getDefaultConfig"), "showModName", modName);
                Minecraft.func_71410_x().func_152344_a(() -> {
                    try {
                        if (future.isDone() || operation != serial || client.func_71401_C() != server || client.field_71441_e != world || client.field_71439_g != player) throw new IllegalStateException("HUD settings context changed before completion");
                        JsonObject result = new JsonObject(); result.add("before", json(before)); result.add("after", json(snapshot(top, waila, overlay))); result.addProperty("restored", restore);
                        if (restore) saved = null; future.complete(result);
                    } catch (Throwable error) { future.completeExceptionally(error); }
                    finally { if (operation == serial) busy = false; }
                });
            } catch (Throwable error) { Minecraft.func_71410_x().func_152344_a(() -> { if (operation == serial) busy = false; future.completeExceptionally(error); }); }
        });
    }
    private static Map<String, Object> snapshot(Class<?> top, Object waila, Class<?> overlay) throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        if (top != null) for (String field : fields) try { values.put(field, NativeAccess.get(top, field)); } catch (NoSuchFieldException ignored) { }
        if (waila != null) {
            for (String key : general) values.put(key, NativeAccess.call(waila, "getConfig", "general", key, false));
            for (String key : modules) values.put(key, NativeAccess.call(waila, "getConfig", "modules", key, false));
            for (String key : new String[]{"waila.cfg.posx", "waila.cfg.posy"}) values.put(key, NativeAccess.call(waila, "getConfig", "general", key, 0));
            for (String key : new String[]{"scale", "posX", "posY"}) values.put("overlay." + key, NativeAccess.get(overlay, key));
        }
        if (top != null) values.put("modName", NativeAccess.call(NativeAccess.call(top, "getDefaultConfig"), "getShowModName"));
        return values;
    }
    private static Class<?> optional(String name) { try { return NativeAccess.type(name); } catch (ClassNotFoundException missing) { return null; } }
    private static JsonObject json(Map<String, Object> values) { JsonObject result = new JsonObject(); for (Map.Entry<String, Object> entry : values.entrySet()) { Object value = entry.getValue(); if (value instanceof Boolean) result.addProperty(entry.getKey(), (Boolean) value); else if (value instanceof Number) result.addProperty(entry.getKey(), (Number) value); else result.addProperty(entry.getKey(), String.valueOf(value)); } return result; }
}
