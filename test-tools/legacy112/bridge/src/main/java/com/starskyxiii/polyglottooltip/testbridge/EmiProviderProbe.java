package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EmiProviderProbe extends Item {
    private static volatile Hold hold;
    private static int serial;
    private static final Gson gson = new Gson();
    public EmiProviderProbe() { setRegistryName("polyglotdevbridge", "emi_gate"); func_77655_b("polyglotdevbridge.emi_gate"); func_77637_a(CreativeTabs.field_78026_f); }

    @Override public String func_77653_i(ItemStack stack) {
        String value = super.func_77653_i(stack); Hold selected = hold;
        if (selected == null || selected.entered.get()) return value;
        boolean match = false; List<String> frames = new ArrayList<>();
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            frames.add(frame.toString());
            if (selected.mode.equals("bake") && frame.getClassName().equals("dev.emi.emi.search.EmiSearch") && frame.getMethodName().equals("bake")) match = true;
            if (selected.mode.equals("worker") && frame.getClassName().equals("dev.emi.emi.search.RegexNameQuery") && frame.getMethodName().equals("matches")) match = true;
        }
        if (!match) return value;
        if (Minecraft.func_71410_x().func_152345_ab()) { selected.clientReached = true; return value; }
        if (!selected.entered.compareAndSet(false, true)) return value;
        selected.worker = Thread.currentThread(); selected.thread = selected.worker.getName(); selected.frames = frames; selected.nativeValue = value;
        selected.phase = "HELD";
        boolean interrupted = false;
        try {
            if (selected.failure.equals("exception")) throw new IllegalStateException("Owned EMI provider exception " + selected.serial);
            if (selected.failure.equals("error")) throw new AssertionError("Owned EMI provider error " + selected.serial);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (selected.release.getCount() != 0 && System.nanoTime() < deadline) {
                try { selected.release.await(100, TimeUnit.MILLISECONDS); }
                catch (InterruptedException error) { interrupted = true; selected.interrupted = true; }
            }
            if (selected.release.getCount() != 0) { selected.timedOut = true; throw new IllegalStateException("Owned EMI provider hold timeout"); }
            return value;
        } finally { selected.phase = "FINISHED"; if (interrupted) Thread.currentThread().interrupt(); }
    }

    public static synchronized JsonObject arm(JsonObject params) {
        if (hold != null && hold.phase.equals("HELD")) throw new IllegalStateException("EMI provider still held");
        String mode = params.get("mode").getAsString(), failure = params.has("failure") ? params.get("failure").getAsString() : "none";
        if (!mode.equals("bake") && !mode.equals("worker") || !failure.equals("none") && !failure.equals("exception") && !failure.equals("error")) throw new IllegalArgumentException("Unknown provider operation");
        hold = new Hold(++serial, mode, failure); return state();
    }
    public static JsonObject release(JsonObject params) {
        Hold selected = hold;
        if (selected == null || params.get("serial").getAsInt() != selected.serial) throw new IllegalArgumentException("Unknown provider token");
        selected.release.countDown(); return state();
    }
    public static JsonObject state() {
        Hold selected = hold; JsonObject result = new JsonObject(); if (selected == null) return result;
        result.addProperty("serial", selected.serial); result.addProperty("mode", selected.mode); result.addProperty("phase", selected.phase);
        result.addProperty("thread", selected.thread); result.addProperty("nativeValue", selected.nativeValue);
        result.addProperty("threadId", selected.worker == null ? -1 : selected.worker.getId());
        result.addProperty("threadState", selected.worker == null ? "none" : selected.worker.getState().name());
        result.addProperty("entered", selected.entered.get()); result.addProperty("clientReached", selected.clientReached);
        result.addProperty("timedOut", selected.timedOut); result.addProperty("interrupted", selected.interrupted);
        result.add("frames", gson.toJsonTree(selected.frames)); return result;
    }
    private static final class Hold {
        final int serial; final String mode, failure;
        final CountDownLatch release = new CountDownLatch(1); final AtomicBoolean entered = new AtomicBoolean();
        volatile String phase = "ARMED", thread, nativeValue; volatile List<String> frames;
        volatile Thread worker;
        volatile boolean clientReached, timedOut, interrupted;
        Hold(int serial, String mode, String failure) { this.serial = serial; this.mode = mode; this.failure = failure; }
    }
}
