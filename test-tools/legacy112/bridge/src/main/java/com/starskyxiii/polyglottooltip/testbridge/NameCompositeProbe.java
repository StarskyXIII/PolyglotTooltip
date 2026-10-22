package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URL;
import java.util.Base64;
import java.util.Enumeration;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

final class NameCompositeProbe {
    private NameCompositeProbe() { }

    static JsonObject craft() throws Exception {
        IRecipe recipe = null;
        for (IRecipe candidate : ForgeRegistries.RECIPES) {
            if (!candidate.getClass().getName().equals("vazkii.botania.common.crafting.recipe.CompositeLensRecipe")) continue;
            if (recipe != null) throw new IllegalStateException("Multiple composite recipes");
            recipe = candidate;
        }
        if (recipe == null) throw new IllegalStateException("Missing composite recipe");
        Item lens = null;
        for (Item item : ForgeRegistries.ITEMS) {
            if (!"botania:lens".equals(String.valueOf(item.getRegistryName()))) continue;
            if (lens != null || !item.getClass().getName().equals("vazkii.botania.common.item.lens.ItemLens"))
                throw new IllegalStateException("Unexpected lens registration");
            lens = item;
        }
        if (lens == null) throw new IllegalStateException("Missing lens");
        JsonObject result = new JsonObject();
        result.addProperty("route", "registered-recipe-matches-and-getCraftingResult");
        result.addProperty("recipe", String.valueOf(recipe.getRegistryName()));
        result.add("recipeClass", describe(recipe.getClass()));
        result.add("lensClass", describe(lens.getClass()));
        JsonArray cases = new JsonArray();
        for (int[] pair : new int[][]{{1, 2}, {2, 1}, {1, 3}, {3, 1}}) {
            InventoryCrafting grid = new InventoryCrafting(new Container() {
                @Override public boolean func_75145_c(EntityPlayer player) { return true; }
            }, 3, 3);
            grid.func_70299_a(0, new ItemStack(lens, 1, pair[0]));
            grid.func_70299_a(1, new ItemStack(lens, 1, pair[1]));
            grid.func_70299_a(2, new ItemStack(Items.field_151123_aH));
            JsonObject row = new JsonObject();
            row.addProperty("id", "crafted-lens-" + pair[0] + "-" + pair[1]);
            row.add("inputsBefore", inventory(grid));
            boolean matches = recipe.func_77569_a(grid, Minecraft.func_71410_x().field_71441_e);
            row.addProperty("matches", matches);
            if (matches) {
                ItemStack output = recipe.func_77572_b(grid);
                if (output.func_190926_b()) throw new IllegalStateException("Empty matched recipe result");
                row.add("output", stack(output));
                Object tag = NativeAccess.get(output, "field_77990_d");
                Object rootMap = NativeAccess.get(tag, "field_74784_a");
                row.add("rootMap", map(rootMap));
                Object child = output.func_77978_p().func_74775_l("compositeLens");
                row.add("childMap", map(NativeAccess.get(child, "field_74784_a")));
            }
            row.add("inputsAfter", inventory(grid));
            cases.add(row);
        }
        result.add("cases", cases);
        Object wrapper = NativeAccess.get(Item.class, "field_150901_e");
        result.add("registryWrapper", describe(wrapper.getClass()));
        Object registry = NativeAccess.get(wrapper, "delegate");
        result.add("registry", describe(registry.getClass()));
        result.add("registryNames", describe(NativeAccess.get(registry, "names").getClass()));
        result.add("registryAliases", describe(NativeAccess.get(registry, "aliases").getClass()));
        Object pools = NativeAccess.get(NativeAccess.type("zone.rong.loliasm.api.LoliStringPool"), "POOLS");
        result.add("pools", describe(pools.getClass()));
        Object internal = NativeAccess.get(pools, "defRetValue");
        result.add("poolDefault", describe(internal.getClass()));
        Object set = NativeAccess.get(internal, "internalPool");
        result.add("poolSet", describe(set.getClass()));
        result.addProperty("poolMask", (Integer) NativeAccess.get(set, "mask"));
        result.addProperty("poolArrayLength", ((Object[]) NativeAccess.get(set, "key")).length);
        ClassLoader launch = ItemStack.class.getClassLoader(), parent = ClassLoader.getSystemClassLoader();
        result.add("launchClass", describe(launch.getClass()));
        result.add("launchSuperclass", describe(launch.getClass().getSuperclass()));
        result.addProperty("launchParentIsSystem", launch.getParent() == parent);
        result.addProperty("launchPrivateParentIsSystem", NativeAccess.get(launch, "parent") == parent);
        result.add("systemLoaderClass", describe(parent.getClass()));
        return result;
    }

    private static JsonArray inventory(InventoryCrafting grid) throws Exception {
        JsonArray result = new JsonArray();
        for (int i = 0; i < 3; i++) result.add(stack(grid.func_70301_a(i)));
        return result;
    }

    private static JsonObject stack(ItemStack value) throws Exception {
        byte[] bytes = NameAuditProbe.binary(value);
        JsonObject result = new JsonObject();
        result.addProperty("serialized", Base64.getEncoder().encodeToString(bytes));
        result.add("stack", NameAuditNbt.read(bytes));
        return result;
    }

    private static JsonObject map(Object value) throws Exception {
        JsonObject result = new JsonObject();
        result.add("outer", describe(value.getClass()));
        Object inner = NativeAccess.get(value, "map");
        result.add("inner", describe(inner.getClass()));
        result.addProperty("size", (Integer) NativeAccess.get(inner, "size"));
        result.addProperty("nullDefault", NativeAccess.get(inner, "defRetValue") == null);
        Object[] keys = (Object[]) NativeAccess.get(inner, "key");
        result.addProperty("keyArrayLength", keys.length);
        JsonArray types = new JsonArray();
        for (int i = 0, count = Math.min(keys.length, (Integer) NativeAccess.get(inner, "size")); i < count; i++)
            types.add(keys[i] == null ? "null" : keys[i].getClass().getName());
        result.add("activeKeyClasses", types);
        return result;
    }

    private static JsonObject describe(Class<?> type) throws Exception {
        JsonObject result = new JsonObject();
        result.addProperty("name", type.getName());
        ClassLoader loader = type.getClassLoader(), system = ClassLoader.getSystemClassLoader();
        result.addProperty("loader", loader == null ? "bootstrap" : loader.getClass().getName());
        result.addProperty("definedBySystem", loader == system);
        result.addProperty("source", type.getProtectionDomain().getCodeSource() == null ? null
                : String.valueOf(type.getProtectionDomain().getCodeSource().getLocation()));
        String resource = type.getName().replace('.', '/') + ".class";
        JsonArray resources = new JsonArray();
        Enumeration<URL> urls = (loader == null ? system : loader).getResources(resource);
        while (urls.hasMoreElements()) resources.add(urls.nextElement().toExternalForm());
        result.add("resources", resources);
        try {
            Object module = Class.class.getMethod("getModule").invoke(type);
            result.addProperty("module", String.valueOf(module));
            result.addProperty("systemUnnamedModule", module == ClassLoader.class.getMethod("getUnnamedModule").invoke(system));
        } catch (NoSuchMethodException ignored) { result.addProperty("module", "java8"); }
        return result;
    }
}
