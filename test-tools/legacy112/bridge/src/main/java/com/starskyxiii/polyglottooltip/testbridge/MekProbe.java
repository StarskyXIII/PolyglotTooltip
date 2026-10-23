package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.Loader;
import java.lang.reflect.Method;

public final class MekProbe {
    private static final Gson gson = new Gson();
    private MekProbe() { }

    public static JsonObject names() throws Exception {
        Class<?> machines = Class.forName("mekanism.common.block.states.BlockStateMachine$MachineType");
        Object[] recipes = Class.forName("mekanism.common.base.IFactory$RecipeType").getEnumConstants();
        JsonArray items = new JsonArray();
        for (Object machine : machines.getEnumConstants()) {
            String name = ((Enum<?>) machine).name();
            if (machines.getField("factoryTier").get(machine) != null) {
                ItemStack stack = (ItemStack) machines.getMethod("getStack").invoke(machine);
                stack.func_77982_d(new NBTTagCompound());
                stack.func_77978_p().func_74768_a("tier", 4);
                for (int recipe = 0; recipe < recipes.length; recipe++) {
                    stack.func_77978_p().func_74768_a("recipeType", recipe);
                    items.add(item(name + ":" + recipe, stack));
                }
            } else if (name.equals("FLUID_TANK")) {
                ItemStack stack = (ItemStack) machines.getMethod("getStack").invoke(machine);
                for (int tier = 0; tier < 5; tier++) {
                    stack.func_77982_d(new NBTTagCompound());
                    stack.func_77978_p().func_74768_a("tier", tier);
                    items.add(item(name + ":" + tier, stack));
                }
            }
        }
        for (Item item : Item.field_150901_e) {
            if (!item.getClass().getName().equals("mekanism.common.item.ItemBlockGasTank")) continue;
            ItemStack stack = new ItemStack(item);
            stack.func_77982_d(new NBTTagCompound());
            for (int tier = 0; tier < 5; tier++) {
                stack.func_77978_p().func_74768_a("tier", tier);
                items.add(item("GAS_TANK:" + tier, stack));
            }
            stack.func_151001_c("Literal tank 42");
            items.add(item("CUSTOM_TANK", stack));
        }
        Class<?> gasType = Class.forName("mekanism.api.gas.Gas");
        Class<?> stackType = Class.forName("mekanism.api.gas.GasStack");
        Object renderer = Class.forName("mekanism.client.jei.gas.GasStackRenderer").getConstructor().newInstance();
        Method tooltip = renderer.getClass().getMethod("getTooltip", Minecraft.class, stackType, ITooltipFlag.class);
        JsonArray gases = new JsonArray();
        for (Object gas : (Iterable<?>) Class.forName("mekanism.api.gas.GasRegistry").getMethod("getRegisteredGasses").invoke(null)) {
            Object stack = stackType.getConstructor(gasType, int.class).newInstance(gas, 12345);
            JsonObject row = new JsonObject();
            row.addProperty("key", (String) gasType.getMethod("getTranslationKey").invoke(gas));
            row.addProperty("nativeName", (String) gasType.getMethod("getLocalizedName").invoke(gas));
            row.addProperty("amount", stackType.getField("amount").getInt(stack));
            row.add("tooltip", gson.toJsonTree(tooltip.invoke(renderer, Minecraft.func_71410_x(), stack, ITooltipFlag.TooltipFlags.NORMAL)));
            gases.add(row);
        }
        JsonObject result = new JsonObject();
        result.addProperty("recipes", recipes.length);
        result.add("items", items);
        result.add("gases", gases);
        return result;
    }

    private static JsonObject item(String key, ItemStack stack) throws Exception {
        JsonObject row = new JsonObject();
        row.addProperty("key", key);
        row.addProperty("nativeName", stack.func_82833_r());
        row.addProperty("id", String.valueOf(stack.func_77973_b().getRegistryName()));
        row.addProperty("metadata", stack.func_77960_j());
        row.addProperty("nbt", String.valueOf(stack.func_77978_p()));
        row.add("tooltip", gson.toJsonTree(stack.func_82840_a(Minecraft.func_71410_x().field_71439_g, ITooltipFlag.TooltipFlags.NORMAL)));
        if (Loader.isModLoaded("polyglottooltip")) row.add("aliases", gson.toJsonTree(ProductNames.item(stack)));
        return row;
    }
}
