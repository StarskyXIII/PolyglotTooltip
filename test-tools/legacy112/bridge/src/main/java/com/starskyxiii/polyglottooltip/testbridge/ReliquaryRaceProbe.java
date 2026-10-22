package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class ReliquaryRaceProbe {
    private static final Gson gson = new Gson();
    private static Job job;
    private static int sequence;
    private static final Map<String, ItemStack> retained = new HashMap<>();
    private ReliquaryRaceProbe() { }

    public static JsonObject run(JsonObject params) throws Exception {
        String action = params.get("action").getAsString();
        if (action.equals("prepare")) {
            if (job != null && !job.phase.equals("DONE")) throw new IllegalStateException("Race worker still owned");
            String family = params.get("family").getAsString();
            if (!family.equals("charm") && !family.equals("fragment")) throw new IllegalArgumentException(family);
            String mode = params.has("mode") ? params.get("mode").getAsString() : "item";
            if (!mode.equals("item") && !mode.equals("helper")) throw new IllegalArgumentException(mode);
            Class<?> helper = NativeAccess.type("xreliquary.util.LanguageHelper");
            Method method = helper.getDeclaredMethod("getLocalization", String.class, Object[].class);
            if (!retained.containsKey(family)) {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("xreliquary", family.equals("charm") ? "mob_charm" : "mob_charm_fragment"));
                ItemStack stack = family.equals("charm") ? (ItemStack) NativeAccess.call(item.getClass(), "getCharmStack", (byte) 0) : new ItemStack(item, 1, 0);
                if (!stack.func_77942_o()) stack.func_77982_d(new NBTTagCompound());
                stack.func_77978_p().func_74778_a("raceKeep", "native payload 314"); retained.put(family, stack);
            }
            ItemStack stack = retained.get(family);
            String key = "item.mob_charm_" + (family.equals("fragment") ? "fragment_" : "") + "0.name";
            job = new Job(++sequence, stack, key, mode, method, NativeAccess.get(helper, "preprocesssed"));
            job.worker.start();
        } else {
            if (job == null || params.has("serial") && params.get("serial").getAsInt() != job.serial) throw new IllegalStateException("Unknown race job");
            switch (action) {
                case "go":
                    if (!job.phase.equals("WAITING")) throw new IllegalStateException("Race worker not waiting");
                    job.start.countDown(); break;
                case "evaluate":
                    JsonObject evaluated = snapshot(params);
                    String value = job.evaluate(); evaluated.add("value", value == null ? JsonNull.INSTANCE : gson.toJsonTree(value)); return evaluated;
                case "state": break;
                case "snapshot": return snapshot(params);
                case "sentinels":
                    for (String key : new String[]{"item.mob_charm_0.tooltip", "item.mob_charm_14.name", "xreliquary.item.mob_charm_0.name"}) job.method.invoke(null, key, new Object[0]);
                    return snapshot(params);
                default: throw new IllegalArgumentException(action);
            }
        }
        return state();
    }

    private static JsonObject state() {
        JsonObject result = new JsonObject();
        result.addProperty("serial", job.serial); result.addProperty("thread", job.worker.getName()); result.addProperty("javaThreadId", job.worker.getId());
        result.addProperty("phase", job.phase); result.addProperty("key", job.key); result.addProperty("mode", job.mode);
        result.addProperty("stackIdentity", System.identityHashCode(job.stack));
        result.addProperty("unchanged", job.nbt.equals(job.stack.func_77955_b(new NBTTagCompound()).toString()));
        if (job.phase.equals("DONE")) {
            result.add("value", job.value == null ? JsonNull.INSTANCE : gson.toJsonTree(job.value));
            result.add("error", job.error == null ? JsonNull.INSTANCE : gson.toJsonTree(job.error.getClass().getName() + ": " + job.error.getMessage()));
        }
        return result;
    }

    private static JsonObject snapshot(JsonObject params) throws Exception {
        JsonObject result = state();
        Map<?, ?> current = (Map<?, ?>) NativeAccess.get(job.method.getDeclaringClass(), "preprocesssed");
        Object store = null, lock = null; boolean patched = false;
        try {
            Class<?> bridge = NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.bootstrap.ReliquaryNameCache");
            patched = (Boolean) NativeAccess.get(bridge, "patched"); store = NativeAccess.get(bridge, "store"); lock = NativeAccess.get(store, "lock");
        }
        catch (ClassNotFoundException ignored) { }
        Map<Object, Object> values = null; Object bound = null; long epoch = -1; boolean guarded = false, disabled = false;
        if (lock != null) {
            synchronized (lock) {
                bound = NativeAccess.get(store, "bound"); epoch = (Long) NativeAccess.get(store, "epoch");
                disabled = (Boolean) NativeAccess.get(store, "disabled");
                guarded = patched && bound == current && current == job.map && !disabled && current.getClass() == HashMap.class;
                if (guarded) values = new LinkedHashMap<>(current);
            }
        }
        if (!guarded) {
            if (job.phase.equals("RUNNING") && (!params.has("held") || !params.get("held").getAsBoolean())) throw new IllegalStateException("Old cache observation requires quiescent worker");
            values = new LinkedHashMap<>(current);
        }
        result.addProperty("cacheSame", current == job.map); result.addProperty("cacheIdentity", System.identityHashCode(current));
        result.addProperty("cacheClass", current.getClass().getName()); result.addProperty("guarded", guarded);
        result.addProperty("patched", patched); result.addProperty("disabled", disabled);
        result.addProperty("boundSame", bound == job.map); result.addProperty("cacheEpoch", epoch);
        result.addProperty("present", values.containsKey(job.key)); result.add("cached", gson.toJsonTree(values.get(job.key)));
        JsonObject sentinels = new JsonObject();
        for (String key : new String[]{"item.mob_charm_0.tooltip", "item.mob_charm_14.name", "xreliquary.item.mob_charm_0.name"}) sentinels.add(key, gson.toJsonTree(values.get(key)));
        result.add("sentinels", sentinels);
        Object context = NativeAccess.call(NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.minecraft.MinecraftLanguageAdapter"), "snapshot");
        result.addProperty("generation", (Long) NativeAccess.get(context, "generation"));
        result.addProperty("resourceEpoch", (Long) NativeAccess.get(context, "nativeNameEpoch"));
        result.addProperty("finalClassDirectory", String.valueOf(NativeAccess.get(net.minecraft.launchwrapper.Launch.classLoader, "tempFolder")));
        return result;
    }

    private static final class Job {
        final int serial; final ItemStack stack; final String key, mode, nbt; final Method method; final Object map;
        final CountDownLatch start = new CountDownLatch(1); final Thread worker;
        volatile String phase = "WAITING", value; volatile Throwable error;
        Job(int serial, ItemStack stack, String key, String mode, Method method, Object map) {
            this.serial = serial; this.stack = stack; this.key = key; this.mode = mode; this.method = method; this.map = map;
            nbt = stack.func_77955_b(new NBTTagCompound()).toString();
            worker = new Thread(() -> {
                try {
                    if (!start.await(60, TimeUnit.SECONDS)) throw new IllegalStateException("Race start timeout");
                    phase = "RUNNING"; value = evaluate();
                } catch (Throwable failure) { error = failure instanceof InvocationTargetException ? failure.getCause() : failure; }
                finally { phase = "DONE"; }
            }, "pt112-reliquary-race-" + serial);
            worker.setDaemon(true);
        }
        String evaluate() throws Exception { return mode.equals("item") ? stack.func_82833_r() : (String) method.invoke(null, key, new Object[0]); }
    }
}
