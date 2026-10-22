package com.starskyxiii.polyglottooltip.testbridge;

import appeng.api.AEApi;
import appeng.api.implementations.ICraftingPatternItem;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public final class AddonSearchProbe {
    private static final Gson gson = new Gson();
    private static Object p2pList, p2pProxy, nee;
    private static GuiScreen p2pGui;
    private static final List<Object> lazySlots = new ArrayList<>();
    private static int applications;
    private AddonSearchProbe() { }

    @SuppressWarnings("unchecked")
    public static JsonObject setup() throws Exception {
        Minecraft client = Minecraft.func_71410_x();
        if (client.field_71439_g == null) throw new IllegalStateException("Expected live player");
        lazySlots.clear(); applications = 0;
        if (Loader.isModLoaded("betterp2p")) {
            p2pProxy = NativeAccess.get(NativeAccess.type("com.projecturanus.betterp2p.BetterP2P"), "proxy");
            List<?> types = (List<?>) NativeAccess.call(p2pProxy, "getP2PTypeList");
            List<Object> entries = new ArrayList<>(); int position = 0;
            for (Object type : types) {
                int index = ((Number) NativeAccess.call(type, "getIndex")).intValue();
                for (boolean output : new boolean[]{false, true}) entries.add(NativeAccess.create("com.projecturanus.betterp2p.network.data.P2PInfo", (short) (output ? 0 : 42),
                        new BlockPos(position++, 4, 0), 0, EnumFacing.NORTH, output ? "Output literal" : "Input literal", output, true, 1, index));
            }
            Object packet = NativeAccess.create("com.projecturanus.betterp2p.network.packet.S2COpenGui");
            NativeAccess.call(packet, "setInfos", entries);
            NativeAccess.call(NativeAccess.call(packet, "getMemoryInfo"), "setType", NativeAccess.call(types.get(0), "getIndex"));
            p2pGui = (GuiScreen) NativeAccess.create("com.projecturanus.betterp2p.client.gui.GuiAdvancedMemoryCard", packet);
            initialize(p2pGui); p2pList = NativeAccess.get(p2pGui, "infos");
            NativeAccess.call(p2pList, "setSelectedEntry", (Object) null);
            NativeAccess.call(p2pList, "refilter");
        }
        if (Loader.isModLoaded("threng")) {
            TileEntity core = (TileEntity) NativeAccess.create("io.github.phantamanta44.threng.tile.TileBigAssemblerCore"); core.func_145834_a(client.field_71441_e);
            List<Object> stores = (List<Object>) NativeAccess.call(core, "getPatternStores");
            ItemStack pattern = AEApi.instance().definitions().items().encodedPattern().maybeStack(1).get();
            pattern.func_77982_d(JsonToNBT.func_180713_a("{crafting:0b,in:[{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}],out:[{id:\"minecraft:diamond\",Count:1b,Damage:0s},{id:\"minecraft:apple\",Count:2b,Damage:0s}]}"));
            if (((ICraftingPatternItem) pattern.func_77973_b()).getPatternForItem(pattern, client.field_71441_e).getCondensedOutputs().length != 2) throw new IllegalStateException("Native pattern outputs differ");
            for (int page = 0; page < 2; page++) {
                TileEntity store = (TileEntity) NativeAccess.create("io.github.phantamanta44.threng.tile.TileBigAssemblerPatternStore"); store.func_145834_a(client.field_71441_e);
                ((IItemHandlerModifiable) NativeAccess.call(store, "getPatternInventory")).setStackInSlot(0, pattern.func_77946_l()); stores.add(store);
            }
            Container container = (Container) NativeAccess.create("io.github.phantamanta44.threng.inventory.ContainerBigAssembler", client.field_71439_g.field_71071_by, core);
            for (Slot slot : container.field_75151_b) if (slot.getClass().getName().endsWith("$SlotPage") && !slot.func_75211_c().func_190926_b()) lazySlots.add(slot);
            if (lazySlots.size() != 2) throw new IllegalStateException("Expected two real populated pages");
        }
        if (Loader.isModLoaded("neenergistics")) {
            GuiInventory screen = new GuiInventory(client.field_71439_g); initialize(screen);
            List<ItemStack> candidates = new ArrayList<>();
            for (int index = 1; index <= 28; index++) candidates.add(new ItemStack(index <= 20 ? Items.field_151045_i : index <= 24 ? Items.field_151042_j : Items.field_151034_e, index));
            Class<?> callback = NativeAccess.type("com.github.vfyjxf.nee.client.gui.IngredientSwitcherWidget$OnResultApply");
            Object action = Proxy.newProxyInstance(callback.getClassLoader(), new Class<?>[]{callback}, (proxy, method, args) -> { if (method.getName().equals("onApply")) applications++; return null; });
            nee = NativeAccess.create("com.github.vfyjxf.nee.client.gui.IngredientSwitcherWidget", 10, 10, 100, 110, candidates, screen, screen.field_147002_h.field_75151_b.get(0), action);
        }
        return observe();
    }

    public static JsonObject query(JsonObject params) throws Exception {
        String owner = params.get("owner").getAsString();
        String query = params.has("query") ? params.get("query").getAsString() : null;
        if (owner.equals("p2p")) {
            if (query != null) ((GuiTextField) NativeAccess.get(p2pGui, "searchBar")).func_146180_a(query);
            if (params.has("select")) {
                List<?> sorted = (List<?>) NativeAccess.call(p2pList, "getSorted");
                int index = params.get("select").getAsInt();
                NativeAccess.call(p2pList, "setSelectedEntry", index < 0 ? null : NativeAccess.call(sorted.get(index), "getLoc"));
            }
            NativeAccess.call(p2pList, "refilter");
            caret((GuiTextField) NativeAccess.get(p2pGui, "searchBar"), params);
        } else if (owner.equals("lazy")) {
            if (query != null) for (Object slot : lazySlots) NativeAccess.call(slot, "updateSearchQuery", query);
        } else if (owner.equals("nee")) {
            GuiTextField field = (GuiTextField) NativeAccess.get(nee, "searchField");
            if (query != null) { field.func_146180_a(query); NativeAccess.call(nee, "updateViews"); }
            if (params.has("select")) {
                int index = params.get("select").getAsInt();
                Object widget = index < 0 ? null : ((List<?>) NativeAccess.get(nee, "widgets")).get(index);
                NativeAccess.set(nee, "selectedWidget", widget);
                if (widget != null) NativeAccess.call(widget, "setSelected", true);
            }
            if (params.has("offset")) {
                int offset = params.get("offset").getAsInt(); List<?> view = (List<?>) NativeAccess.get(nee, "viewStacks");
                if (offset < 0 || offset * 4 >= view.size()) throw new IllegalArgumentException("Invalid offset");
                NativeAccess.set(nee, "scrollOffset", offset); NativeAccess.call(nee, "initWidgets", view.subList(offset * 4, view.size()));
            }
            caret(field, params);
        } else throw new IllegalArgumentException("Unknown search owner");
        return observe();
    }

    private static void initialize(GuiScreen screen) {
        Minecraft client = Minecraft.func_71410_x(); ScaledResolution size = new ScaledResolution(client);
        screen.func_146280_a(client, size.func_78326_a(), size.func_78328_b());
    }

    private static void caret(GuiTextField field, JsonObject params) {
        if (params.has("cursor")) field.func_146190_e(params.get("cursor").getAsInt());
        if (params.has("selection")) field.func_146199_i(params.get("selection").getAsInt());
        field.func_146195_b(true);
    }

    private static JsonObject field(GuiTextField field) {
        JsonObject result = new JsonObject(); result.addProperty("query", field.func_146179_b()); result.addProperty("cursor", field.func_146198_h());
        result.addProperty("selection", field.func_146186_n()); result.addProperty("focused", field.func_146206_l()); return result;
    }

    public static JsonObject observe() throws Exception {
        JsonObject result = new JsonObject();
        JsonObject names = new JsonObject();
        names.addProperty("diamond", new ItemStack(Items.field_151045_i).func_82833_r());
        names.addProperty("apple", new ItemStack(Items.field_151034_e).func_82833_r());
        names.addProperty("iron", new ItemStack(Items.field_151042_j).func_82833_r());
        result.add("nativeNames", names);
        if (p2pList != null) {
            JsonObject state = field((GuiTextField) NativeAccess.get(p2pGui, "searchBar")); JsonArray rows = new JsonArray();
            for (Object row : (List<?>) NativeAccess.call(p2pList, "getFiltered")) {
                JsonObject value = new JsonObject(); value.addProperty("type", ((Number) NativeAccess.call(row, "getType")).intValue());
                value.addProperty("name", (String) NativeAccess.call(row, "getName")); value.addProperty("output", (Boolean) NativeAccess.call(row, "getOutput")); rows.add(value);
            }
            state.add("rows", rows); JsonArray types = new JsonArray();
            for (Object type : (List<?>) NativeAccess.call(p2pProxy, "getP2PTypeList")) {
                JsonObject value = new JsonObject(); value.addProperty("index", ((Number) NativeAccess.call(type, "getIndex")).intValue());
                value.addProperty("name", (String) NativeAccess.call(type, "getDispName"));
                ItemStack item = (ItemStack) NativeAccess.call(type, "getStack"); String[] parts = item.func_77973_b().func_77653_i(item).split(" - ", -1);
                value.addProperty("currentNative", parts.length > 1 ? parts[1] : "§c<Unknown P2P Type>"); types.add(value);
            }
            state.add("types", types); state.addProperty("selected", String.valueOf(NativeAccess.call(p2pList, "getSelectedEntry")));
            result.add("p2p", state);
        }
        if (!lazySlots.isEmpty()) {
            JsonArray slots = new JsonArray();
            for (Object slot : lazySlots) {
                JsonObject state = new JsonObject(); state.addProperty("query", (String) NativeAccess.get(slot, "searchQuery"));
                state.addProperty("matches", (Boolean) NativeAccess.call(slot, "matchesQuery")); state.add("names", gson.toJsonTree(NativeAccess.get(slot, "resultNames"))); slots.add(state);
            }
            result.add("lazy", slots);
        }
        if (nee != null) {
            JsonObject state = field((GuiTextField) NativeAccess.get(nee, "searchField"));
            state.add("rows", items((List<?>) NativeAccess.get(nee, "viewStacks")));
            state.addProperty("offset", ((Number) NativeAccess.get(nee, "scrollOffset")).intValue());
            state.addProperty("maxScroll", ((Number) NativeAccess.get(nee, "maxScroll")).intValue());
            Object selected = NativeAccess.get(nee, "selectedWidget");
            state.addProperty("selected", selected == null ? "" : item((ItemStack) NativeAccess.call(selected, "getIngredient")));
            JsonArray visible = new JsonArray(); for (Object widget : (List<?>) NativeAccess.get(nee, "widgets")) visible.add(item((ItemStack) NativeAccess.call(widget, "getIngredient")));
            state.add("visible", visible); state.addProperty("applications", applications); result.add("nee", state);
        }
        return result;
    }

    private static String item(ItemStack stack) { return stack.func_77973_b().getRegistryName() + "#" + stack.func_190916_E(); }
    private static JsonArray items(List<?> values) { JsonArray rows = new JsonArray(); for (Object value : values) rows.add(item((ItemStack) value)); return rows; }
}
