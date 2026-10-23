package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.Loader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import java.util.*;

public final class EmiProbe {
    private static final Gson gson = new Gson();
    private static final String E = "dev.emi.emi.search.EmiSearch", S = "dev.emi.emi.screen.EmiScreenManager";
    private static Object retainedQuery, retainedResults;
    private static final Map<Object, Integer> identities = new IdentityHashMap<>();
    private EmiProbe() { }

    public static JsonObject run(JsonObject params) throws Exception {
        String action = params.has("action") ? params.get("action").getAsString() : "state";
        Class<?> screen = NativeAccess.type(S), search = NativeAccess.type(E);
        Object widget = NativeAccess.get(screen, "search");
        switch (action) {
            case "open":
                Class<?> options = NativeAccess.type("dev.emi.emi.config.EmiConfig");
                String saved = (String) NativeAccess.call(options, "getSavedConfig");
                saved = saved.replaceAll("right-sidebar-pages:[^;]*;", "right-sidebar-pages: index, craftables, lookup-history;");
                NativeAccess.call(options, "loadConfig", NativeAccess.call(NativeAccess.type("dev.emi.emi.com.unascribed.qdcss.QDCSS"), "load", "Owned EMI fixture", saved));
                Minecraft client = Minecraft.func_71410_x(); client.func_147108_a(new GuiInventory(client.field_71439_g)); break;
            case "query":
                NativeAccess.call(widget, "setFocused", true); NativeAccess.call(widget, "setText", "");
                if (params.has("paste") && params.get("paste").getAsBoolean()) {
                    NativeAccess.call(widget, "write", params.get("text").getAsString()); break;
                }
                for (char character : params.get("text").getAsString().toCharArray()) {
                    if (!(Boolean) NativeAccess.call(widget, "charTyped", character, 0)) throw new IllegalStateException("Native widget rejected character");
                }
                break;
            case "cursor":
                NativeAccess.call(widget, "setCursor", params.get("start").getAsInt());
                NativeAccess.call(widget, "setSelectionEnd", params.get("end").getAsInt()); break;
            case "highlight": NativeAccess.set(widget, "highlight", params.get("value").getAsBoolean()); break;
            case "options":
                Class<?> config = NativeAccess.type("dev.emi.emi.config.EmiConfig");
                for (String key : new String[]{"searchTooltipByDefault", "searchModNameByDefault", "searchTagsByDefault", "appendItemModId", "editMode"}) {
                    if (params.has(key)) NativeAccess.set(config, key, params.get(key).getAsBoolean());
                }
                break;
            case "reload": case "clear":
                int count = params.has("count") ? params.get("count").getAsInt() : 1;
                if (count < 1 || count > 5) throw new IllegalArgumentException("Invalid native request count");
                for (int index = 0; index < count; index++) NativeAccess.call(NativeAccess.type("dev.emi.emi.runtime.EmiReloadManager"), action);
                break;
            case "retain": retainedQuery = NativeAccess.get(search, "compiledQuery"); retainedResults = NativeAccess.get(search, "stacks"); break;
            case "sidebar":
                Object type = NativeAccess.get(NativeAccess.type("dev.emi.emi.config.SidebarType"), params.get("type").getAsString());
                NativeAccess.set(NativeAccess.type("dev.emi.emi.config.EmiConfig"), "searchSidebarFocus", type);
                NativeAccess.call(screen, "focusSearchSidebarType", type); break;
            case "lookup":
                Class<?> stacks = NativeAccess.type("dev.emi.emi.api.stack.EmiStack");
                ItemStack item = new ItemStack(Items.field_151068_bn, 1, 0);
                item.func_77982_d(new NBTTagCompound()); item.func_77978_p().func_74778_a("Potion", "minecraft:swiftness");
                if (params.has("custom")) item.func_151001_c(params.get("custom").getAsString());
                Object value = NativeAccess.call(stacks, "of", item);
                if (params.has("custom")) NativeAccess.call(NativeAccess.type("dev.emi.emi.runtime.EmiSidebars"), "lookup", NativeAccess.call(stacks, "of", new ItemStack(Items.field_151055_y)));
                NativeAccess.call(NativeAccess.type("dev.emi.emi.runtime.EmiSidebars"), "lookup", value); break;
            case "page": NativeAccess.call(NativeAccess.call(screen, "getSearchPanel"), "scroll", params.get("offset").getAsInt()); break;
            case "state": break;
            default: throw new IllegalArgumentException(action);
        }
        return state();
    }

    public static JsonObject state() throws Exception {
        Class<?> screen = NativeAccess.type(S), search = NativeAccess.type(E);
        Object widget = NativeAccess.get(screen, "search"), query = NativeAccess.get(search, "compiledQuery"), results = NativeAccess.get(search, "stacks");
        JsonObject result = new JsonObject();
        result.addProperty("text", (String) NativeAccess.call(widget, "getText"));
        result.addProperty("widget", identity(widget)); result.addProperty("focused", (Boolean) NativeAccess.call(widget, "isFocused"));
        for (String key : new String[]{"selectionStart", "selectionEnd", "searchHistoryIndex"}) result.addProperty(key, (Number) NativeAccess.get(widget, key));
        result.add("history", gson.toJsonTree(NativeAccess.get(widget, "searchHistory")));
        result.addProperty("query", identity(query)); result.addProperty("results", identity(results));
        result.addProperty("worker", identity(NativeAccess.get(search, "currentWorker")));
        Thread worker = (Thread) NativeAccess.get(search, "searchThread"); result.addProperty("workerThread", worker == null ? "none" : worker.getState().name());
        result.addProperty("nativeLoaded", (Boolean) NativeAccess.call(NativeAccess.type("dev.emi.emi.runtime.EmiReloadManager"), "isLoaded"));
        result.addProperty("retainedQuerySame", query == retainedQuery); result.addProperty("retainedResultsSame", results == retainedResults);
        JsonObject indexes = new JsonObject();
        for (String key : new String[]{"names", "tooltips", "mods", "aliases", "bakedStacks"}) indexes.addProperty(key, identity(NativeAccess.get(search, key)));
        result.add("indexes", indexes);
        result.add("matches", keys((List<?>) results)); result.add("rendered", keys((List<?>) NativeAccess.get(screen, "searchedStacks")));
        List<?> source = (List<?>) NativeAccess.call(screen, "getSearchSource"); result.add("source", keys(source));
        Object panel = NativeAccess.call(screen, "getSearchPanel");
        result.addProperty("panel", identity(panel));
        JsonArray inventory = new JsonArray();
        if (Minecraft.func_71410_x().field_71462_r instanceof net.minecraft.client.gui.inventory.GuiContainer) {
            net.minecraft.inventory.Container container = (net.minecraft.inventory.Container) NativeAccess.get(Minecraft.func_71410_x().field_71462_r, "field_147002_h");
            for (net.minecraft.inventory.Slot slot : container.field_75151_b) if (slot.func_75216_d()) {
                ItemStack item = slot.func_75211_c(); inventory.add(item.func_77973_b().getRegistryName() + ":" + item.func_77960_j());
            }
        }
        result.add("inventorySlots", inventory);
        if (panel != null) {
            result.addProperty("type", String.valueOf(NativeAccess.call(panel, "getType")));
            result.addProperty("page", (Integer) NativeAccess.get(panel, "page"));
            Object space = NativeAccess.get(panel, "space");
            if (space != null) result.add("visible", keys((List<?>) NativeAccess.call(space, "getPage", NativeAccess.get(panel, "page"))));
        }
        Class<?> config = NativeAccess.type("dev.emi.emi.config.EmiConfig");
        for (String key : new String[]{"searchTooltipByDefault", "searchModNameByDefault", "searchTagsByDefault", "appendItemModId", "editMode"}) result.addProperty(key, (Boolean) NativeAccess.get(config, key));
        return result;
    }

    private static int identity(Object object) { if (object == null) return 0; Integer value = identities.get(object); if (value == null) { value = identities.size() + 1; identities.put(object, value); } return value; }

    private static JsonArray keys(List<?> values) throws Exception {
        JsonArray result = new JsonArray(); if (values == null) return result;
        for (Object ingredient : values) {
            List<?> stacks = (List<?>) NativeAccess.call(ingredient, "getEmiStacks");
            StringBuilder key = new StringBuilder();
            for (Object stack : stacks) {
                if (key.length() > 0) key.append('|');
                key.append(NativeAccess.call(stack, "getId")).append(':').append(NativeAccess.call(stack, "getSubtype"));
                Object nbt = NativeAccess.call(stack, "getNbt"); if (nbt != null) key.append(nbt);
                key.append('*').append(NativeAccess.call(stack, "getAmount"));
            }
            result.add(key.toString());
        }
        return result;
    }

    public static JsonObject profile() throws Exception {
        JsonObject result = new JsonObject(); JsonArray targets = new JsonArray();
        for (String target : new String[]{"dev.emi.emi.search.EmiSearch", "dev.emi.emi.search.EmiSearch$SearchWorker",
                "dev.emi.emi.search.EmiSearch$CompiledQuery", "dev.emi.emi.runtime.EmiReloadManager",
                "dev.emi.emi.runtime.EmiReloadManager$ReloadWorker", "dev.emi.emi.screen.EmiScreenManager",
                "dev.emi.emi.api.stack.ItemEmiStack", "dev.emi.emi.search.NameQuery", "dev.emi.emi.search.RegexNameQuery"}) {
            Class.forName(target, false, Launch.classLoader); targets.add(target);
        }
        result.add("targets", targets);
        result.addProperty("nativeLoaded", (Boolean) NativeAccess.call(NativeAccess.type("dev.emi.emi.runtime.EmiReloadManager"), "isLoaded"));
        return result;
    }
}
