package com.starskyxiii.polyglottooltip.testbridge;

import appeng.api.AEApi;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.util.AEPartLocation;
import appeng.fluids.util.AEFluidInventory;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fml.common.Loader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AddonTooltipProbe {
    private static final Gson gson = new Gson();
    private AddonTooltipProbe() { }

    public static JsonObject widgets() throws Exception {
        JsonArray rows = new JsonArray();
        if (Loader.isModLoaded("extracells")) {
            for (String name : new String[]{"water", "lava", "zero", "empty"}) {
                FluidStack fluid = name.equals("empty") ? null : new FluidStack(FluidRegistry.getFluid(name.equals("zero") ? "water" : name), name.equals("zero") ? 0 : 1250);
                for (AEPartLocation direction : new AEPartLocation[]{AEPartLocation.INTERNAL, AEPartLocation.NORTH}) {
                    FluidTank tank = new FluidTank(fluid, 4000);
                    Object widget = NativeAccess.create("extracells.gui.widget.WidgetFluidTank", null, tank, 10, 20, direction);
                    List<?> lines = (List<?>) NativeAccess.call(widget, "getToolTip", 999, 999);
                    rows.add(row("ec-tank-" + direction + "-" + name, lines, direction == AEPartLocation.INTERNAL ? 0 : 1,
                            fluid == null || fluid.amount <= 0 ? null : fluid.getLocalizedName()));
                }
                Object slot = NativeAccess.create("extracells.gui.widget.fluid.WidgetFluidSlot", null, null, 10, 20);
                NativeAccess.call(slot, "setFluid", fluid == null ? null : fluid.getFluid());
                rows.add(row("ec-slot-" + name, (List<?>) NativeAccess.call(slot, "getToolTip", 0, 0), 0, fluid == null ? null : fluid.getLocalizedName()));
            }
        }
        if (Loader.isModLoaded("mekeng")) {
            Object inventory = NativeAccess.create("com.mekeng.github.common.me.inventory.impl.GasInventory", 1, 4000);
            Object slot = NativeAccess.create("com.mekeng.github.client.slots.SlotGas", inventory, 0, 0, 0, 0);
            Object tank = NativeAccess.create("com.mekeng.github.client.slots.SlotGasTank", inventory, 0, 0, 0, 0, 16, 32);
            for (String name : new String[]{"hydrogen", "oxygen", "zero", "empty"}) {
                Object gas = name.equals("empty") ? null : AddonNameProbe.gas(name.equals("zero") ? "hydrogen" : name, name.equals("zero") ? 0 : 1250);
                NativeAccess.call(inventory, "setGas", 0, gas);
                Object stored = NativeAccess.call(inventory, "getGasStack", 0);
                String nativeName = stored == null ? null : (String) NativeAccess.call(NativeAccess.call(stored, "getGas"), "getLocalizedName");
                rows.add(row("mek-slot-" + name, lines((String) NativeAccess.call(slot, "getMessage")), 0, nativeName));
                rows.add(row("mek-tank-" + name, lines((String) NativeAccess.call(tank, "getMessage")), 0, name.equals("zero") ? null : nativeName));
            }
        }
        if (Loader.isModLoaded("ae2fc")) {
            AEFluidInventory inventory = new AEFluidInventory(null, 1, 4000);
            Object handler = NativeAccess.create("com.glodblock.github.handler.TankMouseHandler", inventory, 0);
            for (String name : new String[]{"water", "lava", "zero", "empty"}) {
                FluidStack fluid = name.equals("empty") ? null : new FluidStack(FluidRegistry.getFluid(name.equals("zero") ? "water" : name), 1250);
                IAEFluidStack record = fluid == null ? null : AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class).createStack(fluid);
                if (name.equals("zero")) record.setStackSize(0);
                inventory.setFluidInSlot(0, record);
                rows.add(row("fc-tank-" + name, (List<?>) NativeAccess.call(handler, "getTooltip"), 0, fluid == null ? null : fluid.getLocalizedName()));
            }
        }
        JsonObject result = new JsonObject(); result.add("rows", rows); return result;
    }

    private static List<String> lines(String message) { return message == null ? Collections.emptyList() : Arrays.asList(message.split("\n", -1)); }
    private static JsonObject row(String key, List<?> lines, int title, String nativeName) {
        JsonObject row = new JsonObject(); row.addProperty("key", key); row.add("lines", gson.toJsonTree(lines));
        row.addProperty("titleIndex", title); row.addProperty("nativeName", nativeName); return row;
    }
}
