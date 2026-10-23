package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AddonNameProbe {
    private static final Gson gson = new Gson();
    private static final Map<String, ItemStack> stacks = new LinkedHashMap<>();
    private static final String FC = "com.glodblock.github.common.item.";
    private AddonNameProbe() { }

    public static JsonObject inspect(JsonObject params) throws Exception {
        if (stacks.isEmpty() || params.has("reset")) setup();
        if (params.has("mutate")) {
            String fluid = params.get("mutate").getAsString();
            if (!fluid.equals("water") && !fluid.equals("lava")) throw new IllegalArgumentException("Unknown fixture fluid");
            for (Map.Entry<String, ItemStack> entry : stacks.entrySet()) {
                if (entry.getKey().equals("ec-fluid-water")) NativeAccess.call(entry.getValue().func_77973_b().getClass(), "setFluidName", entry.getValue(), fluid);
                if (entry.getKey().equals("fc-fluid-packet-water")) entry.getValue().func_77978_p().func_74782_a("FluidStack", new FluidStack(FluidRegistry.getFluid(fluid), 12345).writeToNBT(new NBTTagCompound()));
            }
        }
        JsonObject result = new JsonObject();
        JsonArray rows = new JsonArray();
        for (Map.Entry<String, ItemStack> entry : stacks.entrySet()) rows.add(row(entry.getKey(), entry.getValue()));
        result.add("items", rows);
        result.addProperty("language", Minecraft.func_71410_x().field_71474_y.field_74363_ab);
        if (Loader.isModLoaded("extracells")) {
            Item gas = findItem("extracells.item.ItemGas$");
            result.addProperty("extraGasEnabled", gas != null && (Boolean) NativeAccess.call(gas, "isMekanismGasEnabled"));
        }
        if (Loader.isModLoaded("ae2fc")) result.addProperty("craftingGasEnabled", (Boolean) NativeAccess.get(NativeAccess.type("com.glodblock.github.util.ModAndClassUtil"), "GAS"));
        return result;
    }

    private static void setup() throws Exception {
        stacks.clear();
        if (Loader.isModLoaded("extracells")) {
            Item fluid = item("extracells.item.ItemFluid");
            for (String name : new String[]{"water", "lava", "missing", ""}) {
                ItemStack stack = new ItemStack(fluid);
                if (!name.isEmpty()) NativeAccess.call(fluid.getClass(), "setFluidName", stack, name);
                stacks.put("ec-fluid-" + name, stack);
            }
            stacks.put("ec-pattern-water", (ItemStack) NativeAccess.call(NativeAccess.type("extracells.item.ItemFluidPattern"), "getPatternForFluid", FluidRegistry.WATER));
            stacks.put("ec-pattern-empty", new ItemStack(item("extracells.item.ItemFluidPattern")));
            Item gas = findItem("extracells.item.ItemGas$");
            if (gas != null) for (String name : new String[]{"hydrogen", "oxygen", "missing", ""}) {
                ItemStack stack = new ItemStack(gas);
                if (!name.isEmpty()) NativeAccess.call(gas, "setGasName", stack, name);
                stacks.put("ec-gas-" + name, stack);
            }
        }
        if (Loader.isModLoaded("mekeng")) {
            Item item = item("com.mekeng.github.common.item.ItemDummyGas");
            for (String name : new String[]{"hydrogen", "oxygen", "empty"}) {
                ItemStack stack = new ItemStack(item);
                if (!name.equals("empty")) NativeAccess.call(item, "setGasStack", stack, gas(name, 12345));
                stacks.put("mek-dummy-" + name, stack);
            }
        }
        if (Loader.isModLoaded("ae2fc")) {
            for (String content : new String[]{"fluid", "gas"}) {
                if (content.equals("gas") && !Boolean.TRUE.equals(NativeAccess.get(NativeAccess.type("com.glodblock.github.util.ModAndClassUtil"), "GAS"))) continue;
                String kind = content.equals("fluid") ? "Fluid" : "Gas";
                for (String form : new String[]{"Drop", "Packet"}) {
                    Item item = item(FC + "Item" + kind + form);
                    for (String name : content.equals("fluid") ? new String[]{"water", "lava", "invalid", "empty"} : new String[]{"hydrogen", "oxygen", "invalid", "empty"}) {
                        ItemStack stack = new ItemStack(item, form.equals("Drop") ? 16 : 1);
                        if (!name.equals("empty")) {
                            NBTTagCompound tag = new NBTTagCompound();
                            if (form.equals("Drop")) {
                                tag.func_74778_a(kind, name);
                                if (content.equals("fluid")) { NBTTagCompound nested = new NBTTagCompound(); nested.func_74778_a("test", "preserved"); tag.func_74782_a("FluidTag", nested); }
                            } else if (name.equals("invalid")) {
                                NBTTagCompound nested = new NBTTagCompound(); nested.func_74778_a(content.equals("fluid") ? "FluidName" : "gasName", "missing"); nested.func_74768_a("Amount", 12345); tag.func_74782_a(kind + "Stack", nested);
                            } else tag.func_74782_a(kind + "Stack", content.equals("fluid") ? new FluidStack(FluidRegistry.getFluid(name), 12345).writeToNBT(new NBTTagCompound())
                                    : (NBTTagCompound) NativeAccess.call(gas(name, 12345), "write", new NBTTagCompound()));
                            stack.func_77982_d(tag);
                        }
                        String key = "fc-" + content + "-" + form.toLowerCase() + "-" + name;
                        stacks.put(key, stack);
                        if (form.equals("Packet") && !name.equals("invalid") && !name.equals("empty")) {
                            ItemStack display = stack.func_77946_l(); display.func_77978_p().func_74757_a("DisplayOnly", true);
                            stacks.put(key + "-display", display);
                        }
                    }
                }
            }
        }
        if (Loader.isModLoaded("thaumicenergistics")) {
            Item item = item("thaumicenergistics.item.ItemDummyAspect");
            for (String aspect : new String[]{"aer", "terra", "missing"}) {
                ItemStack stack = new ItemStack(item); stack.func_77982_d(new NBTTagCompound()); stack.func_77978_p().func_74778_a("aspect", aspect);
                stacks.put("the-aspect-" + aspect, stack);
            }
        }
        for (Map.Entry<String, ItemStack> entry : new LinkedHashMap<>(stacks).entrySet()) {
            if (!entry.getKey().endsWith("water") && !entry.getKey().endsWith("hydrogen")) continue;
            ItemStack custom = entry.getValue().func_77946_l(); custom.func_151001_c("Literal addon name 42");
            stacks.put(entry.getKey() + "-custom", custom);
        }
    }

    public static Item item(String className) {
        Item item = findItem(className);
        if (item != null) return item;
        throw new IllegalArgumentException("Unregistered fixture item " + className);
    }

    private static Item findItem(String className) {
        for (Item item : Item.field_150901_e) if (item.getClass().getName().equals(className)) return item;
        return null;
    }

    public static Object gas(String name, int amount) throws Exception {
        Object gas = NativeAccess.call(NativeAccess.type("mekanism.api.gas.GasRegistry"), "getGas", name);
        if (gas == null) throw new IllegalArgumentException("Unregistered gas " + name);
        return NativeAccess.create("mekanism.api.gas.GasStack", gas, amount);
    }

    private static JsonObject row(String key, ItemStack stack) throws Exception {
        NBTTagCompound before = stack.func_77955_b(new NBTTagCompound());
        JsonObject row = new JsonObject();
        row.addProperty("key", key); row.addProperty("class", stack.func_77973_b().getClass().getName());
        row.addProperty("id", String.valueOf(stack.func_77973_b().getRegistryName()));
        row.addProperty("nativeName", stack.func_82833_r());
        row.add("tooltip", gson.toJsonTree(stack.func_82840_a(Minecraft.func_71410_x().field_71439_g, ITooltipFlag.TooltipFlags.NORMAL)));
        if (Loader.isModLoaded("polyglottooltip")) row.add("aliases", gson.toJsonTree(ProductNames.item(stack)));
        row.addProperty("unchanged", before.equals(stack.func_77955_b(new NBTTagCompound())));
        row.addProperty("count", stack.func_190916_E()); row.addProperty("nbt", String.valueOf(stack.func_77978_p()));
        return row;
    }
}
