package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import java.awt.Rectangle;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class IntegrationWidgetProbe {
    private static final Map<String, Object> widgets = new LinkedHashMap<>();
    private static GuiScreen owner;
    private static Object modular, panel, handler;
    private static FluidTank tank;
    private IntegrationWidgetProbe() { }

    public static JsonObject open(JsonObject params) throws Exception {
        Minecraft client = Minecraft.func_71410_x();
        if (client.field_71439_g == null) throw new IllegalStateException("No player");
        widgets.clear(); modular = null; handler = null; panel = null;
        Fluid fluid = FluidRegistry.getFluid(params.has("fluid") ? params.get("fluid").getAsString() : "water");
        if (fluid == null) throw new IllegalArgumentException("Unregistered fluid");
        tank = new FluidTank(new FluidStack(fluid, params.has("amount") ? params.get("amount").getAsInt() : 12345), 64000);
        if (params.get("kind").getAsString().equals("mui")) {
            handler = NativeAccess.create("com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler", tank);
            NativeAccess.call(handler, "phantom", params.has("phantom") && params.get("phantom").getAsBoolean());
            NativeAccess.call(handler, "controlsAmount", !params.has("controlsAmount") || params.get("controlsAmount").getAsBoolean());
            NativeAccess.call(handler, "canFillSlot", true); NativeAccess.call(handler, "canDrainSlot", true);
            NativeAccess.call(handler, "setValue", tank.getFluid(), false, false);
            Object slot = NativeAccess.create("com.cleanroommc.modularui.widgets.slot.FluidSlot");
            NativeAccess.type("com.cleanroommc.modularui.widgets.slot.FluidSlot").getDeclaredMethod("syncHandler", handler.getClass()).invoke(slot, handler);
            Class<?> positioned = NativeAccess.type("com.cleanroommc.modularui.api.widget.IPositioned");
            positioned.getMethod("pos", int.class, int.class).invoke(slot, 20, 24);
            positioned.getMethod("size", int.class, int.class).invoke(slot, 18, 18);
            panel = NativeAccess.call(NativeAccess.type("com.cleanroommc.modularui.screen.ModularPanel"), "defaultPanel", "polyglot_native_fixture", 176, 120);
            NativeAccess.call(panel, "child", slot);
            modular = NativeAccess.create("com.cleanroommc.modularui.screen.ModularScreen", "polyglotdevbridge", panel);
            NativeAccess.call(NativeAccess.type("com.cleanroommc.modularui.factory.ClientGUI"), "open", modular);
            widgets.put("slot", slot);
        } else if (params.get("kind").getAsString().equals("gt")) {
            Object tankWidget = NativeAccess.create("gregtech.api.gui.widgets.TankWidget", tank, 20, 24, 18, 54);
            NativeAccess.call(tankWidget, "setClient");
            Object phantom = NativeAccess.create("gregtech.api.gui.widgets.PhantomFluidWidget", 60, 24, 18, 18, tank);
            NativeAccess.call(phantom, "setFluidStackSupplier", (Supplier<FluidStack>) tank::getFluid, true);
            NativeAccess.call(phantom, "showTip", true);
            Class<?> holderType = NativeAccess.type("gregtech.api.gui.IUIHolder");
            Object holder = Proxy.newProxyInstance(holderType.getClassLoader(), new Class<?>[]{holderType}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "isValid": case "isRemote": return true;
                    case "getUIColorOverride": return -1;
                    case "markAsDirty": return null;
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    case "toString": return "Native GUI fixture holder";
                    default: throw new UnsupportedOperationException(method.toString());
                }
            });
            Object background = NativeAccess.get(NativeAccess.type("gregtech.api.gui.GuiTextures"), "BACKGROUND");
            Object builder = NativeAccess.call(NativeAccess.type("gregtech.api.gui.ModularUI"), "builder", background, 176, 120);
            NativeAccess.call(builder, "shouldColor", false); NativeAccess.call(builder, "widget", tankWidget); NativeAccess.call(builder, "widget", phantom);
            Object ui = NativeAccess.call(builder, "build", holder, client.field_71439_g);
            NativeAccess.call(ui, "initWidgets");
            client.func_147108_a((GuiScreen) NativeAccess.create("gregtech.api.gui.impl.ModularUIGui", ui));
            widgets.put("tank", tankWidget); widgets.put("phantom", phantom);
        } else throw new IllegalArgumentException("Unknown native widget kind");
        owner = client.field_71462_r;
        return observe(new JsonObject());
    }

    public static JsonObject observe(JsonObject params) throws Exception {
        if (owner == null || owner != Minecraft.func_71410_x().field_71462_r) throw new IllegalStateException("Native widget screen changed");
        if (params.has("fluid")) {
            Fluid fluid = FluidRegistry.getFluid(params.get("fluid").getAsString());
            FluidStack stack = fluid == null ? null : new FluidStack(fluid, params.has("amount") ? params.get("amount").getAsInt() : 23456);
            if (fluid == null && !params.get("fluid").getAsString().equals("empty")) throw new IllegalArgumentException("Unregistered fluid");
            tank.setFluid(stack);
            if (handler != null) NativeAccess.call(handler, "setValue", stack, false, false);
        }
        JsonObject result = new JsonObject(); result.addProperty("screen", owner.getClass().getName());
        result.addProperty("screenIdentity", System.identityHashCode(owner));
        JsonArray rows = new JsonArray();
        for (Map.Entry<String, Object> entry : widgets.entrySet()) {
            Object widget = entry.getValue(); JsonObject row = new JsonObject();
            row.addProperty("key", entry.getKey()); row.addProperty("class", widget.getClass().getName()); row.addProperty("identity", System.identityHashCode(widget));
            FluidStack displayed;
            if (modular != null) {
                Rectangle area = (Rectangle) NativeAccess.type("com.cleanroommc.modularui.api.widget.IWidget").getMethod("getArea").invoke(widget);
                row.addProperty("x", area.x + area.width / 2); row.addProperty("y", area.y + area.height / 2);
                row.addProperty("width", area.width); row.addProperty("height", area.height);
                displayed = (FluidStack) NativeAccess.call(handler, "getValue");
            } else {
                Object position = NativeAccess.call(widget, "getPosition"), size = NativeAccess.call(widget, "getSize");
                int width = (Integer) NativeAccess.get(size, "width"), height = (Integer) NativeAccess.get(size, "height");
                row.addProperty("x", (Integer) NativeAccess.get(position, "x") + width / 2); row.addProperty("y", (Integer) NativeAccess.get(position, "y") + height / 2);
                row.addProperty("width", width); row.addProperty("height", height);
                displayed = (FluidStack) NativeAccess.get(widget, entry.getKey().equals("tank") ? "lastFluidInTank" : "lastFluidStack");
            }
            if (displayed != null) row.add("fluid", IntegrationNameProbe.fluid(displayed));
            rows.add(row);
        }
        result.add("widgets", rows);
        if (modular != null) {
            result.addProperty("phantom", (Boolean) NativeAccess.call(handler, "isPhantom"));
            result.addProperty("controlsAmount", (Boolean) NativeAccess.call(handler, "controlsAmount"));
            result.addProperty("opening", (Boolean) NativeAccess.call(panel, "isOpening"));
            result.addProperty("scale", (Number) NativeAccess.call(panel, "getScale"));
            Object context = NativeAccess.call(modular, "getContext");
            Object hovered = NativeAccess.call(context, "getTopHovered");
            result.addProperty("hovered", hovered == null ? 0 : System.identityHashCode(hovered));
        }
        return result;
    }

    public static void beforeFrame(GuiScreen screen, int x, int y, float partial) throws Exception {
        if (screen != owner || modular == null) return;
        NativeAccess.call(NativeAccess.call(modular, "getContext"), "updateState", x, y, partial);
        NativeAccess.call(modular, "onFrameUpdate");
    }
}
