package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.client.event.GuiScreenEvent;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class EmiFrameProbe {
    private static CompletableFuture<JsonElement> pending;
    private static GuiScreen screen;
    private static String name;
    private static long serial, deadline, capturedFrame;
    private static int pre, post;
    private static boolean canceled;
    private static JsonObject before, after;
    private EmiFrameProbe() { }

    public static void request(JsonObject params, CompletableFuture<JsonElement> future) {
        if (pending != null && !pending.isDone()) throw new IllegalStateException("Natural EMI frame pending");
        String filename = params.get("name").getAsString();
        if (!filename.matches("[a-zA-Z0-9_-]{1,100}\\.png")) throw new IllegalArgumentException("Invalid screenshot name");
        Minecraft client = Minecraft.func_71410_x();
        if (client.field_71462_r == null || new File(client.field_71412_D, "screenshots/" + filename).exists()) throw new IllegalStateException("Invalid native frame destination");
        screen = client.field_71462_r; name = filename; pending = future;
        pre = post = 0; canceled = false; before = after = null; capturedFrame = -1;
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
    }

    public static void start() { serial++; }
    public static void before(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (pending == null || event.getGui() != screen) return;
        try {
            if (capturedFrame == -1) capturedFrame = serial;
            if (capturedFrame != serial) throw new IllegalStateException("Native frame did not complete");
            pre++; canceled |= event.isCanceled(); before = EmiProbe.state();
        } catch (Throwable error) { fail(error); }
    }
    public static void after(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (pending == null || event.getGui() != screen) return;
        try { post++; after = EmiProbe.state(); }
        catch (Throwable error) { fail(error); }
    }
    public static void end() {
        if (pending == null) return;
        try {
            Minecraft client = Minecraft.func_71410_x();
            if (client.field_71462_r != screen || System.nanoTime() > deadline) throw new IllegalStateException("Natural EMI frame unavailable");
            if (capturedFrame == -1) return;
            if (pre != 1 || post != 1 || canceled || before == null || after == null) throw new IllegalStateException("Native frame was canceled, duplicated or incomplete");
            ScreenShotHelper.func_148259_a(client.field_71412_D, name, client.field_71443_c, client.field_71440_d, client.func_147110_a());
            File file = new File(client.field_71412_D, "screenshots/" + name);
            if (!file.isFile() || file.length() == 0) throw new IllegalStateException("Native screenshot missing");
            JsonObject result = new JsonObject(); result.addProperty("path", file.getAbsolutePath());
            result.addProperty("frame", capturedFrame); result.addProperty("pre", pre); result.addProperty("post", post); result.addProperty("canceled", canceled);
            result.addProperty("screen", screen.getClass().getName()); result.add("before", before); result.add("after", after);
            pending.complete(result); clear();
        } catch (Throwable error) { fail(error); }
    }
    private static void fail(Throwable error) { if (pending != null) pending.completeExceptionally(error); clear(); }
    private static void clear() { pending = null; screen = null; before = after = null; }
}
