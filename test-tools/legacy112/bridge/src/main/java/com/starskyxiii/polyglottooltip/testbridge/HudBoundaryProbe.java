package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.Event;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

public final class HudBoundaryProbe {
    private static String mode = "off", failure;
    private static World world;
    private static Object provider;
    private static List<Object> providers;
    private static Map<Class<?>, List<Object>> registry;
    private static boolean ownKey;
    private static int callbacks;
    private HudBoundaryProbe() { }

    @SuppressWarnings("unchecked")
    public static JsonObject command(JsonObject params) throws Exception {
        Minecraft client = Minecraft.func_71410_x();
        maintain();
        if (params.has("mode")) {
            String next = params.get("mode").getAsString();
            if (!next.equals("off") && !next.equals("event-title") && !next.equals("unknown-head")) throw new IllegalArgumentException("Unknown HUD boundary mode");
            if (!next.equals("off") && (client.func_71401_C() == null || !client.func_71401_C().func_71218_a(0).func_72912_H().func_76065_j().startsWith("pt112-"))) throw new IllegalStateException("HUD boundary requires owned world");
            remove(); callbacks = 0; failure = null; world = client.field_71441_e;
            if (next.equals("unknown-head")) {
                Object registrar = NativeAccess.call(NativeAccess.type("mcp.mobius.waila.api.impl.ModuleRegistrar"), "instance");
                Class<?> contract = NativeAccess.type("mcp.mobius.waila.api.IWailaDataProvider");
                registry = (Map<Class<?>, List<Object>>) NativeAccess.get(registrar, "headBlockProviders");
                ownKey = !registry.containsKey(Blocks.field_150348_b.getClass());
                provider = Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[]{contract}, (self, method, arguments) -> {
                    switch (method.getName()) {
                        case "hashCode": return System.identityHashCode(self);
                        case "equals": return self == arguments[0];
                        case "toString": return "DevBridgeUnknownHead";
                        case "getWailaHead": if (world == Minecraft.func_71410_x().field_71441_e) callbacks++; return arguments[1];
                        default: throw new IllegalStateException("Unexpected HUD boundary callback " + method);
                    }
                });
                registrar.getClass().getMethod("registerHeadProvider", contract, Class.class).invoke(registrar, provider, Blocks.field_150348_b.getClass());
                providers = registry.get(Blocks.field_150348_b.getClass());
            }
            mode = next;
        }
        JsonObject result = new JsonObject(); result.addProperty("mode", mode); result.addProperty("callbacks", callbacks);
        if (failure != null) result.addProperty("error", failure);
        return result;
    }

    public static void maintain() { if (world != Minecraft.func_71410_x().field_71441_e) { remove(); world = null; } }

    @SuppressWarnings("unchecked")
    public static void event(Event event) {
        if (!mode.equals("event-title") || world != Minecraft.func_71410_x().field_71441_e || !event.getClass().getName().equals("mcp.mobius.waila.api.event.WailaTooltipEvent")) return;
        try {
            Object accessor = NativeAccess.call(event, "getAccessor");
            if (!new BlockPos(40, 4, 0).equals(NativeAccess.call(accessor, "getPosition"))) return;
            List<String> lines = (List<String>) NativeAccess.call(event, "getCurrentTip");
            if (lines.isEmpty()) throw new IllegalStateException("Native Waila title is empty");
            lines.set(0, "PT external title"); callbacks++;
        } catch (Throwable error) { failure = error.toString(); }
    }

    private static void remove() {
        if (providers != null) providers.removeIf(value -> value == provider);
        if (ownKey && registry != null && providers != null && providers.isEmpty()) registry.remove(Blocks.field_150348_b.getClass());
        mode = "off"; provider = null; providers = null; registry = null; ownKey = false;
    }
}
