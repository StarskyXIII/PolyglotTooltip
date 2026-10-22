package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;

public final class JemiProbe {
    private static final String UTIL = "dev.emi.emi.jemi.JemiUtil", PLUGIN = "dev.emi.emi.jemi.JemiPlugin";
    private static final Map<String, Object> converted = new LinkedHashMap<>();
    private JemiProbe() { }

    public static JsonObject run(JsonObject params) throws Exception {
        String action = params.has("action") ? params.get("action").getAsString() : "state";
        if (action.equals("convert")) return convert();
        if (action.equals("lookup")) {
            Object stack = require(params.get("key").getAsString());
            Class<?> sidebars = NativeAccess.type("dev.emi.emi.runtime.EmiSidebars");
            NativeAccess.call(sidebars, "lookup", require("barrier"));
            NativeAccess.call(sidebars, "lookup", stack);
        } else if (action.equals("multi")) {
            Object value = NativeAccess.call(NativeAccess.type("dev.emi.emi.api.stack.EmiIngredient"), "of", Arrays.asList(require("stone"), require("variant")));
            converted.put("multi", value);
            NativeAccess.call(NativeAccess.type("dev.emi.emi.runtime.EmiSidebars"), "lookup", value);
        } else if (action.equals("hide")) {
            NativeAccess.call(NativeAccess.type("dev.emi.emi.runtime.EmiHidden"), "setVisibility", require("stone"), params.get("hidden").getAsBoolean(), false);
        } else if (action.equals("recalculate")) {
            NativeAccess.call(NativeAccess.type("dev.emi.emi.screen.EmiScreenManager"), "forceRecalculate");
        } else if (!action.equals("state")) throw new IllegalArgumentException(action);
        return state();
    }

    private static Object require(String key) {
        Object value = converted.get(key);
        if (value == null) throw new IllegalArgumentException("Missing converted ingredient " + key);
        return value;
    }

    private static JsonObject convert() throws Exception {
        Class<?> util = NativeAccess.type(UTIL), vanilla = NativeAccess.type("mezz.jei.api.ingredients.VanillaTypes");
        Object itemType = NativeAccess.get(vanilla, "ITEM"), fluidType = NativeAccess.call(util, "getFluidType");
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("stone", new ItemStack(Blocks.field_150348_b, 3, 0));
        inputs.put("variant", new ItemStack(Blocks.field_150348_b, 2, 1));
        inputs.put("barrier", new ItemStack(Items.field_151055_y));
        ItemStack potion = new ItemStack(Items.field_151068_bn); potion.func_77982_d(new NBTTagCompound());
        potion.func_77978_p().func_74778_a("Potion", "minecraft:swiftness"); inputs.put("potion", potion);
        ItemStack custom = potion.func_77946_l(); custom.func_151001_c("JEMI custom vial"); inputs.put("custom", custom);
        FluidStack fluid = new FluidStack(FluidRegistry.WATER, 1375); fluid.tag = new NBTTagCompound();
        fluid.tag.func_74778_a("JemiFixture", "water"); inputs.put("fluid", fluid);
        JsonObject result = new JsonObject(); JsonArray rows = new JsonArray();
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            Object value = entry.getValue(), type = value instanceof FluidStack ? fluidType : itemType;
            Object untyped = util.getMethod("getStack", Object.class).invoke(null, value);
            Object typed = NativeAccess.call(util, "getStack", type, value);
            converted.put(entry.getKey(), typed);
            JsonObject row = new JsonObject(); row.addProperty("key", entry.getKey());
            row.add("input", ingredient(value)); row.add("untyped", describe(untyped)); row.add("typed", describe(typed)); rows.add(row);
        }
        result.add("rows", rows);
        Object unknown = util.getMethod("getStack", Object.class).invoke(null, new Object());
        result.addProperty("unknownEmpty", (Boolean) NativeAccess.call(unknown, "isEmpty"));
        Object nullItem = NativeAccess.call(util, "getStack", itemType, null);
        result.addProperty("nullEmpty", (Boolean) NativeAccess.call(nullItem, "isEmpty"));
        result.addProperty("fluidLanguageKey", fluid.getUnlocalizedName()); result.add("state", state()); return result;
    }

    private static JsonObject ingredient(Object value) {
        JsonObject result = new JsonObject(); result.addProperty("class", value.getClass().getName());
        if (value instanceof ItemStack) {
            ItemStack stack = (ItemStack) value;
            result.addProperty("id", String.valueOf(stack.func_77973_b().getRegistryName())); result.addProperty("metadata", stack.func_77960_j());
            result.addProperty("amount", stack.func_190916_E()); result.addProperty("nbt", String.valueOf(stack.func_77978_p()));
            result.addProperty("custom", stack.func_82837_s()); result.addProperty("name", stack.func_82833_r());
        } else if (value instanceof FluidStack) {
            FluidStack stack = (FluidStack) value;
            result.addProperty("id", stack.getFluid().getName()); result.addProperty("amount", stack.amount);
            result.addProperty("nbt", String.valueOf(stack.tag)); result.addProperty("name", stack.getLocalizedName());
        } else result.addProperty("value", String.valueOf(value));
        return result;
    }

    private static JsonObject describe(Object stack) throws Exception {
        JsonObject result = new JsonObject(); result.addProperty("class", stack.getClass().getName());
        result.addProperty("empty", (Boolean) NativeAccess.call(stack, "isEmpty"));
        result.addProperty("id", String.valueOf(NativeAccess.call(stack, "getId")));
        result.addProperty("subtype", (Number) NativeAccess.call(stack, "getSubtype"));
        result.addProperty("amount", (Number) NativeAccess.call(stack, "getAmount"));
        result.addProperty("nbt", String.valueOf(NativeAccess.call(stack, "getNbt")));
        Object key = NativeAccess.call(stack, "getKey"); result.addProperty("keyClass", key.getClass().getName());
        result.addProperty("name", (String) NativeAccess.call(NativeAccess.call(stack, "getName"), "getString"));
        JsonArray tooltip = new JsonArray();
        for (Object text : (List<?>) NativeAccess.call(stack, "getTooltipText")) tooltip.add((String) NativeAccess.call(text, "getString"));
        result.add("tooltip", tooltip);
        Optional<?> typed = (Optional<?>) NativeAccess.call(NativeAccess.type(UTIL), "getTyped", stack);
        result.addProperty("typedPresent", typed.isPresent());
        if (typed.isPresent()) {
            Object type = NativeAccess.call(typed.get(), "type");
            result.addProperty("ingredientClass", ((Class<?>) NativeAccess.call(type, "getIngredientClass")).getName());
            result.add("roundTrip", ingredient(NativeAccess.call(typed.get(), "ingredient")));
        }
        return result;
    }

    private static JsonArray members(List<?> values) throws Exception {
        JsonArray result = new JsonArray();
        for (Object value : values) {
            JsonObject row = new JsonObject(); row.addProperty("class", value.getClass().getName());
            row.addProperty("identity", System.identityHashCode(value));
            JsonArray stacks = new JsonArray();
            for (Object stack : (List<?>) NativeAccess.call(value, "getEmiStacks")) {
                JsonObject member = new JsonObject(); member.addProperty("id", String.valueOf(NativeAccess.call(stack, "getId")));
                member.addProperty("subtype", (Number) NativeAccess.call(stack, "getSubtype"));
                member.addProperty("amount", (Number) NativeAccess.call(stack, "getAmount"));
                member.addProperty("nbt", String.valueOf(NativeAccess.call(stack, "getNbt"))); stacks.add(member);
            }
            row.add("stacks", stacks);
            if (NativeAccess.type("dev.emi.emi.runtime.EmiFavorite").isInstance(value)) {
                Object recipe = NativeAccess.call(value, "getRecipe");
                row.addProperty("recipe", recipe == null ? "none" : String.valueOf(NativeAccess.call(recipe, "getId")));
            }
            result.add(row);
        }
        return result;
    }

    private static JsonObject state() throws Exception {
        Class<?> plugin = NativeAccess.type(PLUGIN); Object registry = NativeAccess.get(plugin, "ingredientRegistry"), runtime = NativeAccess.get(plugin, "runtime");
        JsonObject result = new JsonObject(); result.addProperty("ready", registry != null && runtime != null);
        if (registry == null || runtime == null) return result;
        result.addProperty("runtimeClass", runtime.getClass().getName()); result.addProperty("runtimeIdentity", System.identityHashCode(runtime));
        result.addProperty("registryClass", registry.getClass().getName()); result.addProperty("registrySource", String.valueOf(registry.getClass().getProtectionDomain().getCodeSource().getLocation()));
        JsonArray types = new JsonArray();
        for (Object type : (Collection<?>) NativeAccess.call(registry, "getRegisteredIngredientTypes")) types.add(((Class<?>) NativeAccess.call(type, "getIngredientClass")).getName());
        result.add("types", types);
        Object filter = NativeAccess.call(NativeAccess.type("mezz.jei.Internal"), "getIngredientFilter");
        Class<?> accessor = NativeAccess.type("dev.emi.emi.mixin.jei.accessor.IngredientFilterAccessor");
        result.addProperty("filterAccessor", accessor.isInstance(filter));
        if (converted.containsKey("stone")) {
            Optional<?> typed = (Optional<?>) NativeAccess.call(NativeAccess.type(UTIL), "getTyped", require("stone"));
            if (!typed.isPresent()) throw new IllegalStateException("Converted stone has no native typed value");
            result.addProperty("hostHidden", (Boolean) NativeAccess.call(plugin, "isIngredientHidden", typed.get()));
            result.addProperty("emiHidden", (Boolean) NativeAccess.call(NativeAccess.type("dev.emi.emi.runtime.EmiHidden"), "isHidden", require("stone")));
        }
        Class<?> stacks = NativeAccess.type("dev.emi.emi.registry.EmiStackList");
        result.add("full", members((List<?>) NativeAccess.get(stacks, "stacks")));
        result.add("filtered", members((List<?>) NativeAccess.get(stacks, "filteredStacks")));
        result.add("craftables", members((List<?>) NativeAccess.get(NativeAccess.type("dev.emi.emi.runtime.EmiSidebars"), "craftables")));
        result.add("lookup", members((List<?>) NativeAccess.get(NativeAccess.type("dev.emi.emi.runtime.EmiSidebars"), "lookupHistory")));
        Object source = NativeAccess.call(NativeAccess.type("dev.emi.emi.screen.EmiScreenManager"), "getSearchSource");
        Object results = NativeAccess.get(NativeAccess.type("dev.emi.emi.search.EmiSearch"), "stacks");
        result.addProperty("sourceIdentity", System.identityHashCode(source)); result.addProperty("resultsIdentity", System.identityHashCode(results));
        result.add("source", members((List<?>) source)); result.add("results", members((List<?>) results));
        Minecraft client = Minecraft.func_71410_x();
        result.addProperty("screen", client.field_71462_r == null ? "none" : client.field_71462_r.getClass().getName());
        if (client.field_71462_r instanceof GuiContainer) {
            result.addProperty("container", NativeAccess.get(client.field_71462_r, "field_147002_h").getClass().getName());
            result.addProperty("handlers", ((List<?>) NativeAccess.call(NativeAccess.type("dev.emi.emi.registry.EmiRecipeFiller"), "getAllHandlers", client.field_71462_r)).size());
        }
        return result;
    }
}
