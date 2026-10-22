package com.starskyxiii.polyglottooltip.testbridge;

import appeng.api.AEApi;
import appeng.api.storage.channels.IItemStorageChannel;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** A controlled cold-admission regression for one cached AE2 tooltip in an owned client. */
final class ColdNameCacheProbe {
    private static final String PRODUCT = "com.starskyxiii.polyglottooltip.legacy.";
    private static Object retained;
    private ColdNameCacheProbe() { }

    @SuppressWarnings("unchecked")
    static JsonObject run(JsonObject params) throws Exception {
        String action = params.get("action").getAsString();
        if (action.equals("start")) {
            Class<?> libraries = NativeAccess.type(PRODUCT + "bootstrap.NameLibrarySource");
            if ((Boolean) NativeAccess.call(libraries, "preparationPending")) throw new IllegalStateException("Preparation must be settled");
            Object capabilities = NativeAccess.get(libraries, "capabilities");
            Map<Class<?>, Object> entries = (Map<Class<?>, Object>) NativeAccess.get(capabilities, "entries");
            Class<?> selected = null;
            synchronized (entries) {
                for (Class<?> candidate : entries.keySet())
                    if (candidate.getName().equals("com.google.common.collect.UnmodifiableIterator")) selected = candidate;
            }
            if (selected == null || !(Boolean) NativeAccess.call(libraries, "admits", selected))
                throw new IllegalStateException("Expected already admitted component iterator");
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            ((Executor) NativeAccess.get(capabilities, "executor")).execute(() -> {
                entered.countDown();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
            try {
                if (!entered.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("Worker gate did not enter");
                // Remove one prepared entry to reproduce its first-use state; source/class bytes are untouched.
                synchronized (entries) { entries.remove(selected); }
                retained = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)
                        .createStack(new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("industrialforegoing:conveyor"))));
                JsonObject result = capture();
                result.addProperty("controlledColdClass", selected.getName());
                result.addProperty("libraryPending", (Boolean) NativeAccess.call(libraries, "preparationPending"));
                return result;
            } finally { release.countDown(); }
        }
        if (action.equals("observe")) return capture();
        throw new IllegalArgumentException("Unknown cold cache action");
    }

    private static JsonObject capture() throws Exception {
        if (retained == null) throw new IllegalStateException("Cold cache test not started");
        JsonObject result = new JsonObject();
        Object snapshot = NativeAccess.call(NativeAccess.type(PRODUCT + "minecraft.MinecraftLanguageAdapter"), "snapshot");
        result.addProperty("generation", (Number) NativeAccess.get(snapshot, "generation"));
        result.addProperty("nativeNameEpoch", (Number) NativeAccess.get(snapshot, "nativeNameEpoch"));
        result.addProperty("primaryTable", System.identityHashCode(NativeAccess.get(snapshot, "primaryTable")));
        result.addProperty("stackIdentity", System.identityHashCode(retained));
        result.add("tooltip", new Gson().toJsonTree(NativeAccess.call(retained, "getToolTip")));
        return result;
    }
}
