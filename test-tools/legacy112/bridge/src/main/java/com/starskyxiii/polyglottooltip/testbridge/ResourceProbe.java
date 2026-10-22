package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraftforge.client.resource.SelectiveReloadStateHandler;
import net.minecraftforge.client.resource.VanillaResourceType;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.Loader;
import java.util.ArrayList;
import java.util.List;

public final class ResourceProbe {
    private static final JsonArray calls = new JsonArray();
    private static boolean installed, nestedUnknown;
    private static int remaining;
    private static String failure;
    private static final IResourceManagerReloadListener provider = manager -> {
        record("provider", manager);
        if (nestedUnknown) {
            nestedUnknown = false;
            ((IReloadableResourceManager) manager).func_110541_a(packs());
        }
        if (remaining-- > 0) {
            if ("error".equals(failure)) throw new AssertionError("fixture resource error");
            throw new IllegalStateException("fixture resource failure");
        }
    };

    private ResourceProbe() { }

    public static JsonObject run(JsonObject params) throws Exception {
        Minecraft client = Minecraft.func_71410_x();
        IReloadableResourceManager manager = (IReloadableResourceManager) client.func_110442_L();
        String action = params.get("action").getAsString();
        JsonObject result = new JsonObject(); result.add("before", snapshot());
        if (params.has("heads") && params.get("heads").getAsBoolean()) result.add("headsBefore", heads());
        int first = calls.size();
        try {
            switch (action) {
                case "install":
                    if (installed) throw new IllegalStateException("fixture provider already installed");
                    manager.func_110542_a(provider); installed = true; break;
                case "full": client.func_110436_a(); break;
                case "fallback": remaining = 1; failure = "runtime"; client.func_110436_a(); break;
                case "bothFail": remaining = 2; failure = "runtime"; client.func_110436_a(); break;
                case "error": remaining = 1; failure = "error"; client.func_110436_a(); break;
                case "selective": FMLClientHandler.instance().refreshResources(VanillaResourceType.LANGUAGES); break;
                case "selectiveFail": remaining = 2; failure = "runtime"; FMLClientHandler.instance().refreshResources(VanillaResourceType.LANGUAGES); break;
                case "resetSelector": SelectiveReloadStateHandler.INSTANCE.endReload(); break;
                case "unknown": manager.func_110541_a(packs()); break;
                case "nestedUnknown": nestedUnknown = true; client.func_110436_a(); break;
                case "nestedRegistration":
                    boolean[] nestedOnce = {true};
                    manager.func_110542_a(resource -> {
                        record("outerRegistration", resource);
                        if (nestedOnce[0]) {
                            nestedOnce[0] = false;
                            ((IReloadableResourceManager) resource).func_110542_a(child -> record("innerRegistration", child));
                        }
                    }); break;
                case "registrationFailure":
                    boolean[] once = {true};
                    manager.func_110542_a(resource -> {
                        record("failingRegistration", resource);
                        if (once[0]) { once[0] = false; throw new IllegalStateException("fixture registration failure"); }
                    }); break;
                case "otherManager":
                    new SimpleReloadableResourceManager(new MetadataSerializer()).func_110542_a(resource -> record("otherManager", resource)); break;
                case "observerFailure":
                    if (Loader.isModLoaded("polyglottooltip")) NativeAccess.call(NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.bootstrap.ResourceReloadState"),
                            "subscribe", (java.util.function.Consumer<Object>) value -> { throw new IllegalStateException("fixture observer failure"); });
                    client.func_110436_a(); break;
                default: throw new IllegalArgumentException("Unknown resource action " + action);
            }
            result.addProperty("outcome", "returned");
        } catch (Throwable error) {
            result.addProperty("outcome", error.getClass().getName()); result.addProperty("message", error.getMessage());
        }
        JsonArray observed = new JsonArray(); for (int index = first; index < calls.size(); index++) observed.add(calls.get(index));
        result.add("calls", observed); result.add("after", snapshot());
        if (params.has("heads") && params.get("heads").getAsBoolean()) result.add("headsAfter", heads());
        result.addProperty("rawSelectorPresent", NativeAccess.get(SelectiveReloadStateHandler.INSTANCE, "currentPredicate") != null);
        return result;
    }

    private static JsonArray heads() throws Exception {
        JsonArray result = new JsonArray();
        for (Object event : (List<?>) NativeAccess.call(NativeAccess.type("com.starskyxiii.polyglottooltip.resourceheads.HeadEvents"), "snapshot")) result.add((String) event);
        return result;
    }

    public static JsonObject snapshot() {
        JsonObject result = new JsonObject();
        if (!Loader.isModLoaded("polyglottooltip")) return result;
        try {
            Object value = NativeAccess.call(NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.bootstrap.ResourceReloadState"), "snapshot");
            result.addProperty("epoch", (Number) NativeAccess.get(value, "resourceReloadEpoch"));
            result.addProperty("active", (Number) NativeAccess.get(value, "active"));
            for (String flag : new String[]{"baseline", "poisoned", "disabled"}) result.addProperty(flag, (Boolean) NativeAccess.get(value, flag));
            result.addProperty("ready", (Boolean) NativeAccess.call(value, "ready", Minecraft.func_71410_x().func_110442_L()));
        } catch (Exception error) { throw new IllegalStateException(error); }
        return result;
    }

    private static void record(String event, IResourceManager manager) {
        JsonObject value = new JsonObject(); value.addProperty("event", event);
        value.addProperty("manager", System.identityHashCode(manager)); value.add("state", snapshot()); calls.add(value);
    }

    @SuppressWarnings("unchecked")
    private static List<IResourcePack> packs() {
        try {
            Minecraft client = Minecraft.func_71410_x();
            List<IResourcePack> result = new ArrayList<>((List<IResourcePack>) NativeAccess.get(client, "field_110449_ao"));
            Object repository = NativeAccess.get(client, "field_110448_aq");
            for (Object entry : (List<?>) NativeAccess.call(repository, "func_110613_c")) result.add((IResourcePack) NativeAccess.call(entry, "func_110514_c"));
            IResourcePack server = (IResourcePack) NativeAccess.call(repository, "func_148530_e");
            if (server != null) result.add(server);
            return result;
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
}
