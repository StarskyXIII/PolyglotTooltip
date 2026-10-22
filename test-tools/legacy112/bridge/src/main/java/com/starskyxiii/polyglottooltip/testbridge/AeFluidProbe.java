package com.starskyxiii.polyglottooltip.testbridge;

import appeng.api.AEApi;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.fluids.client.gui.widgets.GuiFluidSlot;
import appeng.fluids.client.gui.widgets.GuiFluidTank;
import appeng.fluids.container.slots.IMEFluidSlot;
import appeng.fluids.util.AEFluidInventory;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class AeFluidProbe {
    private AeFluidProbe() { }

    public static JsonObject widgets() {
        AEFluidInventory inventory = new AEFluidInventory(null, 1, 32000);
        GuiFluidTank tank = new GuiFluidTank(inventory, 0, 0, 0, 16, 32, 0);
        GuiFluidSlot slot = new GuiFluidSlot(inventory, 0, 0, 0, 0);
        IFluidStorageChannel channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        JsonObject result = new JsonObject();
        for (String name : new String[]{"water", "lava", "zero", "empty"}) {
            IAEFluidStack stack = name.equals("empty") ? null : channel.createStack(new FluidStack(FluidRegistry.getFluid(name.equals("zero") ? "water" : name), 12345));
            if (name.equals("zero")) stack.setStackSize(0);
            inventory.setFluidInSlot(0, stack);
            JsonObject row = new JsonObject();
            row.addProperty("tank", tank.getMessage());
            row.addProperty("slot", slot.getMessage());
            result.add(name, row);
        }
        if (net.minecraftforge.fml.common.Loader.isModLoaded("jei")) {
            JsonArray lines = new JsonArray();
            for (String line : new mezz.jei.plugins.vanilla.ingredients.fluid.FluidStackRenderer().getTooltip(
                    Minecraft.func_71410_x(), new FluidStack(FluidRegistry.WATER, 12345), net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL)) lines.add(line);
            result.add("jei", lines);
        }
        return result;
    }

    public static JsonObject hover() throws Exception {
        GuiScreen screen = Minecraft.func_71410_x().field_71462_r;
        if (!(screen instanceof GuiContainer) || !screen.getClass().getName().startsWith("appeng.")) throw new IllegalStateException("Not an AE container");
        int left = field(screen, "field_147003_i").getInt(screen);
        int top = field(screen, "field_147009_r").getInt(screen);
        for (Slot slot : ((GuiContainer) screen).field_147002_h.field_75151_b) {
            if (!(slot instanceof IMEFluidSlot) || ((IMEFluidSlot) slot).getAEFluidStack() == null || !((IMEFluidSlot) slot).shouldRenderAsFluid()) continue;
            IAEFluidStack stack = ((IMEFluidSlot) slot).getAEFluidStack();
            int x = left + slot.field_75223_e + 8, y = top + slot.field_75221_f + 8;
            Method render = null;
            for (Class<?> type = screen.getClass(); type != null && render == null; type = type.getSuperclass()) {
                try { render = type.getDeclaredMethod("func_191948_b", int.class, int.class); }
                catch (NoSuchMethodException ignored) { }
            }
            if (render == null) throw new NoSuchMethodException("func_191948_b");
            TooltipProbe.captured = null;
            TooltipProbe.capturing = true;
            try {
                render.setAccessible(true);
                render.invoke(screen, x, y);
            } finally { TooltipProbe.capturing = false; }
            if (TooltipProbe.captured == null) throw new IllegalStateException("Native fluid hover did not emit a tooltip event");
            JsonObject result = new JsonObject();
            result.addProperty("class", screen.getClass().getName());
            result.addProperty("fluid", stack.getFluidStack().getFluid().getName());
            result.addProperty("amount", stack.getStackSize());
            result.addProperty("x", x);
            result.addProperty("y", y);
            JsonArray lines = new JsonArray();
            for (String line : TooltipProbe.captured) lines.add(line);
            result.add("lines", lines);
            return result;
        }
        throw new IllegalStateException("No visible native fluid slot");
    }

    private static Field field(Object owner, String name) throws NoSuchFieldException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
