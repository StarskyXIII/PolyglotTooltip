package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.client.event.GuiScreenEvent;
import java.io.File;
import java.util.concurrent.CompletableFuture;

public final class ScreenProbe {
    private static CompletableFuture<JsonElement> pending;
    private static GuiScreen screen;
    private static String name;
    private static int mouseX, mouseY;
    private static boolean rendered;
    private static JsonObject configuration, inheritedBefore, inheritedChanged, inheritedAfter;
    private static boolean inherited;
    private static boolean nativeEvents, innerDraw, requireTooltip, innerCanceled;
    private static int frames, requestedFrames;
    private static long deadline;
    private static String integration;
    private static JsonObject integrationBefore, integrationChanged;

    private ScreenProbe() { }

    public static void request(JsonObject params, CompletableFuture<JsonElement> future) {
        if (pending != null && !pending.isDone()) throw new IllegalStateException("A render is pending");
        Minecraft client = Minecraft.func_71410_x();
        String filename = params.get("name").getAsString();
        if (!filename.matches("[a-zA-Z0-9_-]{1,100}\\.png")) throw new IllegalArgumentException("Invalid screenshot name");
        if (new File(client.field_71412_D, "screenshots/" + filename).exists()) throw new IllegalArgumentException("Screenshot already exists");
        GuiScreen requestedScreen = client.field_71462_r;
        if (requestedScreen == null) throw new IllegalStateException("No screen");
        String requestedIntegration = params.has("integration") ? params.get("integration").getAsString() : null;
        if (requestedIntegration != null && (!requestedIntegration.equals("pe") && !requestedIntegration.equals("it") && !requestedIntegration.equals("rs") && !requestedIntegration.equals("emi") && !requestedIntegration.equals("jei") || !params.has("nativeEvents") || !params.get("nativeEvents").getAsBoolean())) throw new IllegalArgumentException("Invalid native integration observation");
        int x = params.get("x").getAsInt(), y = params.get("y").getAsInt();
        if (x < 0 || y < 0 || x >= requestedScreen.field_146294_l || y >= requestedScreen.field_146295_m) throw new IllegalArgumentException("Coordinates outside screen");
        int count = params.has("frames") ? params.get("frames").getAsInt() : 1;
        if (count < 1 || count > 1200 || count > 1 && (!params.has("nativeEvents") || !params.get("nativeEvents").getAsBoolean()))
            throw new IllegalArgumentException("Use 1..1200 native frames");
        screen = requestedScreen;
        name = filename;
        mouseX = x;
        mouseY = y;
        pending = future;
        rendered = false;
        configuration = params.has("configuration") ? params.getAsJsonObject("configuration") : null;
        inherited = params.has("inherited") && params.get("inherited").getAsBoolean();
        nativeEvents = params.has("nativeEvents") && params.get("nativeEvents").getAsBoolean();
        requireTooltip = params.has("requireTooltip") && params.get("requireTooltip").getAsBoolean();
        integration = requestedIntegration; integrationBefore = null; integrationChanged = null;
        frames = 0;
        requestedFrames = count;
        deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(count > 1 ? 60 : 15);
    }

    public static void tick() {
        if (pending == null) return;
        if (pending.isDone()) clear();
        else if (Minecraft.func_71410_x().field_71462_r != screen) {
            pending.completeExceptionally(new IllegalStateException("Screen changed before render"));
            clear();
        } else if (System.nanoTime() > deadline) {
            pending.completeExceptionally(new IllegalStateException("Native render timed out after " + frames + " frames"));
            clear();
        }
    }

    public static void drawNative(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (pending == null || !nativeEvents || innerDraw) return;
        if (pending.isDone()) { clear(); return; }
        try {
            if (event.isCanceled() || event.getGui() != screen || Minecraft.func_71410_x().field_71462_r != screen) throw new IllegalStateException("Native render changed or canceled before capture");
            event.setCanceled(true);
            if (frames++ == 0) {
                if (inherited) inheritedBefore = AddonInheritedProbe.ui(new JsonObject());
                if (integration != null) integrationBefore = integrationState(true);
                if (configuration != null) {
                    CoreProbe.configure(configuration);
                    Class.forName("com.starskyxiii.polyglottooltip.legacy.minecraft.MinecraftLanguageAdapter").getMethod("tick").invoke(null);
                    configuration = null;
                }
                if (inherited) inheritedChanged = AddonInheritedProbe.ui(new JsonObject());
                if (integration != null) integrationChanged = integrationState(true);
            }
            TooltipProbe.captured = null;
            TooltipProbe.refined = null;
            TooltipProbe.events.clear();
            TooltipProbe.capturing = true;
            innerDraw = true;
            try {
                IntegrationWidgetProbe.beforeFrame(screen, mouseX, mouseY, event.getRenderPartialTicks());
                net.minecraftforge.client.ForgeHooksClient.drawScreen(screen, mouseX, mouseY, event.getRenderPartialTicks());
            } finally { innerDraw = false; TooltipProbe.capturing = false; }
        } catch (Throwable error) {
            if (pending != null) pending.completeExceptionally(error);
            clear();
        }
    }

    public static void draw(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (pending == null) return;
        if (nativeEvents) {
            if (innerDraw) { innerCanceled = event.isCanceled(); rendered = true; }
            return;
        }
        if (pending.isDone()) { clear(); return; }
        try {
            if (event.isCanceled() || event.getGui() != screen || Minecraft.func_71410_x().field_71462_r != screen) throw new IllegalStateException("Screen render changed or canceled");
            event.setCanceled(true);
            if (inherited) inheritedBefore = AddonInheritedProbe.ui(new JsonObject());
            if (configuration != null) {
                CoreProbe.configure(configuration);
                Class.forName("com.starskyxiii.polyglottooltip.legacy.minecraft.MinecraftLanguageAdapter").getMethod("tick").invoke(null);
            }
            if (inherited) inheritedChanged = AddonInheritedProbe.ui(new JsonObject());
            TooltipProbe.captured = null;
            TooltipProbe.refined = null;
            TooltipProbe.capturing = true;
            try { screen.func_73863_a(mouseX, mouseY, event.getRenderPartialTicks()); }
            finally { TooltipProbe.capturing = false; }
            if (inherited) inheritedAfter = AddonInheritedProbe.ui(new JsonObject());
            if (pending.isDone()) { clear(); return; }
            if (Minecraft.func_71410_x().field_71462_r != screen) throw new IllegalStateException("Screen changed during render");
            rendered = true;
        } catch (Throwable error) { pending.completeExceptionally(error); clear(); }
    }

    public static void capture(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (pending == null || !rendered) return;
        if (nativeEvents && (!innerDraw || (requireTooltip && TooltipProbe.events.isEmpty()))) return;
        if (nativeEvents && frames < requestedFrames) return;
        CompletableFuture<JsonElement> future = pending;
        if (future.isDone()) { clear(); return; }
        try {
            if (event.getGui() != screen || Minecraft.func_71410_x().field_71462_r != screen) throw new IllegalStateException("Screen changed after render");
            Minecraft client = Minecraft.func_71410_x();
            if (future.isDone()) return;
            ScreenShotHelper.func_148259_a(client.field_71412_D, name, client.field_71443_c, client.field_71440_d, client.func_147110_a());
            File output = new File(client.field_71412_D, "screenshots/" + name);
            if (!output.isFile() || output.length() == 0) throw new IllegalStateException("Screenshot was not saved");
            JsonObject result = new JsonObject();
            result.addProperty("path", output.getAbsolutePath());
            result.addProperty("class", screen.getClass().getName());
            result.addProperty("x", mouseX);
            result.addProperty("y", mouseY);
            JsonArray lines = new JsonArray();
            if (TooltipProbe.captured != null) for (String line : TooltipProbe.captured) lines.add(line);
            result.add("lines", lines);
            if (TooltipProbe.refined != null) result.add("refinedTooltip", TooltipProbe.refined);
            result.addProperty("nativeEvents", nativeEvents);
            result.addProperty("frames", frames);
            result.addProperty("innerPreCanceled", innerCanceled);
            JsonArray events = new JsonArray();
            for (String type : TooltipProbe.events) events.add(type);
            result.add("tooltipEvents", events);
            if (nativeEvents && inherited) inheritedAfter = AddonInheritedProbe.ui(new JsonObject());
            if (inherited) { result.add("before", inheritedBefore); result.add("changed", inheritedChanged); result.add("after", inheritedAfter); }
            if (integration != null) { result.addProperty("integration", integration); result.add("before", integrationBefore); result.add("changed", integrationChanged); result.add("after", integrationState(false)); }
            future.complete(result);
        } catch (Throwable error) { future.completeExceptionally(error); }
        finally { clear(); }
    }

    private static JsonObject integrationState(boolean raw) throws Exception {
        if (integration.equals("pe")) return ProjectEProbe.observe();
        if (integration.equals("it")) return IntegratedTerminalProbe.observe();
        if (integration.equals("emi")) return EmiProbe.state();
        if (integration.equals("jei")) return LifecycleProbe.recipeVisible();
        JsonObject params = new JsonObject(); params.addProperty("raw", raw); return RefinedProbe.ui(params);
    }

    private static void clear() {
        pending = null;
        screen = null;
        name = null;
        rendered = false;
        configuration = null; inherited = false; inheritedBefore = null; inheritedChanged = null; inheritedAfter = null;
        nativeEvents = false; requireTooltip = false; innerCanceled = false; frames = 0; requestedFrames = 1;
        integration = null; integrationBefore = null; integrationChanged = null;
    }
}
