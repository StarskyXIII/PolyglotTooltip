package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RefinedProbe {
    private static final String R = "com.raoulvdberge.refinedstorage.";
    private static final Gson gson = new Gson();
    private static final BlockPos controller = new BlockPos(120, 4, 0), drive = controller.func_177982_a(-1, 0, 0);
    private static final BlockPos itemGrid = controller.func_177982_a(0, 0, 1), fluidGrid = controller.func_177982_a(0, 0, -1), manager = controller.func_177982_a(0, 1, 0);
    private static final List<ItemStack> domain = new ArrayList<>();
    private static boolean inserted;
    private RefinedProbe() { }

    private static boolean more() { return Loader.instance().getIndexedModList().get("refinedstorage").getVersion().equals("2.3.2"); }
    private static Class<?> type(String name) throws Exception { return NativeAccess.type(R + name); }
    private static Object api() throws Exception { return NativeAccess.call(type("apiimpl.API"), "instance"); }
    private static Block block(String name) throws Exception { return (Block) NativeAccess.get(type("RSBlocks"), name); }
    private static Item item(String name) throws Exception { return (Item) NativeAccess.get(type("RSItems"), name); }
    private static Object node(WorldServer world, BlockPos pos) throws Exception { return NativeAccess.call(world.func_175625_s(pos), "getNode"); }

    public static void world(JsonObject params, CompletableFuture<JsonElement> future) {
        Minecraft client = Minecraft.func_71410_x(); MinecraftServer server = client.func_71401_C();
        if (server == null || client.field_71439_g == null) throw new IllegalStateException("No integrated player");
        UUID id = client.field_71439_g.func_110124_au();
        server.func_152344_a(() -> {
            if (future.isDone()) return;
            try {
                WorldServer world = server.func_71218_a(0); EntityPlayerMP player = server.func_184103_al().func_177451_a(id);
                if (player == null || !world.func_72912_H().func_76065_j().startsWith("pt112-")) throw new IllegalStateException("Not an owned fixture world");
                future.complete(server(world, player, params));
            } catch (Throwable error) { future.completeExceptionally(error); }
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static JsonObject server(WorldServer world, EntityPlayerMP player, JsonObject params) throws Exception {
        String operation = params.has("operation") ? params.get("operation").getAsString() : "state";
        if (operation.equals("setup")) {
            inserted = false; domain.clear();
            player.field_71135_a.func_147364_a(120.5, 6, 3.5, 180, 30);
            for (int x = -1; x <= 8; x++) for (int y = 0; y <= 1; y++) for (int z = -1; z <= 1; z++) world.func_175698_g(controller.func_177982_a(x, y, z));
            IProperty property = (IProperty) NativeAccess.get(type("block.BlockController"), "TYPE");
            Comparable creative = (Comparable) NativeAccess.get(type("block.enums.ControllerType"), "CREATIVE");
            world.func_180501_a(controller, block("CONTROLLER").func_176223_P().func_177226_a(property, creative), 3);
            world.func_180501_a(drive, block("DISK_DRIVE").func_176223_P(), 3);
            property = (IProperty) NativeAccess.get(type("block.BlockGrid"), "TYPE");
            world.func_180501_a(itemGrid, block("GRID").func_176223_P().func_177226_a(property, (Comparable) NativeAccess.get(type("api.network.grid.GridType"), "NORMAL")), 3);
            world.func_180501_a(fluidGrid, block("GRID").func_176223_P().func_177226_a(property, (Comparable) NativeAccess.get(type("api.network.grid.GridType"), "FLUID")), 3);
            world.func_180501_a(manager, block("CRAFTER_MANAGER").func_176223_P(), 3);
            for (BlockPos pos : new BlockPos[]{itemGrid, fluidGrid, manager}) NativeAccess.call(node(world, pos), "setSize", 1);
            domain.add(new ItemStack(Items.field_151045_i)); domain.add(new ItemStack(Items.field_151166_bC)); domain.add(new ItemStack(Items.field_151044_h));
            List<Item> blocks = new ArrayList<>();
            for (Item candidate : ForgeRegistries.ITEMS.getValuesCollection()) if (candidate instanceof ItemBlock && candidate.getRegistryName() != null && candidate.getRegistryName().func_110624_b().equals("minecraft")) blocks.add(candidate);
            blocks.sort(Comparator.comparing(value -> value.getRegistryName().toString()));
            for (Item candidate : blocks.subList(0, Math.min(96, blocks.size()))) domain.add(new ItemStack(candidate));
            domain.add(new ItemStack(Items.field_151045_i).func_151001_c("Literal fixture diamond 314"));
            ItemStack lore = new ItemStack(Items.field_151045_i); NBTTagCompound display = new NBTTagCompound(); NBTTagList lines = new NBTTagList();
            lines.func_74742_a(new NBTTagString("Only native lore sentinel 314")); display.func_74782_a("Lore", lines);
            lore.func_77982_d(new NBTTagCompound()); lore.func_77978_p().func_74782_a("display", display); domain.add(lore);
            for (int n = 1; n <= 8; n++) {
                world.func_180501_a(controller.func_177982_a(n, 0, 0), block("CABLE").func_176223_P(), 3);
                BlockPos pos = controller.func_177982_a(n, 0, 1); world.func_180501_a(pos, block("CRAFTER").func_176223_P(), 3);
                Object crafter = node(world, pos); NativeAccess.call(crafter, "setDirection", EnumFacing.SOUTH);
                NativeAccess.call(crafter, "setDisplayName", n <= 4 ? "Fixture Alpha" : "Fixture Beta");
                IItemHandlerModifiable patterns = (IItemHandlerModifiable) NativeAccess.call(crafter, "getPatternInventory");
                for (int slot = 0; slot < patterns.getSlots(); slot++) {
                    int index = (n - 1) * patterns.getSlots() + slot;
                    ItemStack pattern = new ItemStack(item("PATTERN"));
                    NativeAccess.call(type("item.ItemPattern"), "setProcessing", pattern, true);
                    NativeAccess.call(type("item.ItemPattern"), "setOredict", pattern, false);
                    NativeAccess.call(type("item.ItemPattern"), "setVersion", pattern);
                    NativeAccess.call(type("item.ItemPattern"), "setInputSlot", pattern, 0, new ItemStack(Items.field_151055_y));
                    if (index == 71) NativeAccess.call(type("item.ItemPattern"), "setFluidOutputSlot", pattern, 0, new FluidStack(FluidRegistry.WATER, 1000));
                    else {
                        NativeAccess.call(type("item.ItemPattern"), "setOutputSlot", pattern, 0, domain.get(index == 70 ? 0 : index).func_77946_l());
                        if (index == 70) NativeAccess.call(type("item.ItemPattern"), "setOutputSlot", pattern, 1, domain.get(1).func_77946_l());
                    }
                    if (!(Boolean) NativeAccess.call(type("apiimpl.network.node.NetworkNodeCrafter"), "isValidPatternInSlot", world, pattern)) throw new IllegalStateException("Invalid native pattern " + index);
                    patterns.setStackInSlot(slot, pattern);
                }
            }
            IItemHandlerModifiable disks = (IItemHandlerModifiable) NativeAccess.call(node(world, drive), "getDisks");
            for (int index = 0; index < 2; index++) {
                Item provider = item(index == 0 ? "STORAGE_DISK" : "FLUID_STORAGE_DISK");
                ItemStack disk = new ItemStack(provider, 1, (Integer) NativeAccess.get(provider.getClass(), "TYPE_CREATIVE"));
                provider.func_77663_a(disk, world, player, index, false);
                if (!(Boolean) NativeAccess.call(provider, "isValid", disk) || NativeAccess.call(NativeAccess.call(api(), "getStorageDiskManager", world), "getByStack", disk) == null) throw new IllegalStateException("Disk was not registered");
                disks.setStackInSlot(index, disk);
            }
        }
        Object network = node(world, controller);
        if (operation.equals("insert")) {
            if (inserted) throw new IllegalStateException("Fixture already populated");
            for (ItemStack stack : domain) insert(network, stack.func_77946_l(), stack.func_77973_b() == Items.field_151045_i && !stack.func_77942_o() && more() ? 2147483664L : 64L);
            insert(network, new FluidStack(FluidRegistry.WATER, 1), more() ? 2147484671L : 23456L);
            insert(network, new FluidStack(FluidRegistry.LAVA, 1), 8192L); inserted = true;
        }
        if (operation.equals("delta")) insert(network, new ItemStack(Items.field_151045_i), 7);
        if (operation.equals("open")) {
            String kind = params.get("kind").getAsString();
            BlockPos pos = kind.equals("manager") ? manager : kind.equals("fluid") ? fluidGrid : itemGrid;
            if (kind.equals("manager")) {
                IBlockState state = world.func_180495_p(pos);
                if (!state.func_177230_c().func_180639_a(world, pos, state, player, EnumHand.MAIN_HAND, EnumFacing.SOUTH, .5F, .5F, .5F)) throw new IllegalStateException("Manager activation rejected");
            } else NativeAccess.call(NativeAccess.call(api(), "getGridManager"), "openGrid", NativeAccess.get(type("apiimpl.network.node.NetworkNodeGrid"), "FACTORY_ID"), player, pos);
        }
        JsonObject result = new JsonObject(); result.addProperty("more", more()); result.addProperty("inserted", inserted);
        result.addProperty("canRun", (Boolean) NativeAccess.call(network, "canRun")); result.addProperty("containerWindow", player.field_71070_bA.field_75152_c);
        for (String kind : new String[]{"Item", "Fluid"}) {
            Object cache = NativeAccess.call(network, "get" + kind + "StorageCache");
            result.addProperty(kind.toLowerCase() + "Storages", ((Collection<?>) NativeAccess.call(cache, "getStorages")).size());
            JsonArray stored = new JsonArray();
            for (Object value : (Collection<?>) NativeAccess.call(NativeAccess.call(cache, "getList"), "getStacks")) {
                Object stack = more() ? NativeAccess.call(value, "getStack") : value;
                JsonObject row = ingredient(stack);
                row.addProperty("quantity", more() ? ((Number) NativeAccess.call(value, "getCount")).longValue() : stack instanceof ItemStack ? ((ItemStack) stack).func_190916_E() : ((FluidStack) stack).amount);
                stored.add(row);
            }
            result.add(kind.toLowerCase() + "Stored", stored);
        }
        result.addProperty("itemActive", (Boolean) NativeAccess.call(node(world, itemGrid), "isActive"));
        result.addProperty("fluidActive", (Boolean) NativeAccess.call(node(world, fluidGrid), "isActive"));
        for (String kind : new String[]{"item", "fluid"}) {
            Object grid = node(world, kind.equals("item") ? itemGrid : fluidGrid);
            result.addProperty(kind + "CanUpdate", (Boolean) NativeAccess.call(grid, "canUpdate"));
            result.addProperty(kind + "Connected", NativeAccess.call(grid, "getNetwork") == network);
        }
        Map<?, ?> named = (Map<?, ?>) NativeAccess.call(NativeAccess.call(network, "getCraftingManager"), "getNamedContainers");
        JsonObject groups = new JsonObject();
        for (Map.Entry<?, ?> entry : named.entrySet()) {
            int slots = 0; for (Object inventory : (Iterable<?>) entry.getValue()) slots += ((IItemHandlerModifiable) inventory).getSlots();
            groups.addProperty(entry.getKey().toString(), slots);
        }
        result.add("groups", groups); JsonArray inputs = new JsonArray(); for (ItemStack stack : domain) inputs.add(ingredient(stack)); result.add("domain", inputs); return result;
    }

    private static void insert(Object network, Object stack, long count) throws Exception {
        Object amount = more() ? (Object) Long.valueOf(count) : Integer.valueOf(Math.toIntExact(count));
        Object remainder = NativeAccess.call(network, stack instanceof ItemStack ? "insertItem" : "insertFluid", stack, amount, NativeAccess.get(type("api.util.Action"), "PERFORM"));
        if (remainder != null && (!(remainder instanceof ItemStack) || !((ItemStack) remainder).func_190926_b())) throw new IllegalStateException("Native storage refused insertion");
    }

    private static JsonObject ingredient(Object stack) {
        return ingredient(stack, true);
    }

    private static JsonObject ingredient(Object stack, boolean localized) {
        JsonObject result = new JsonObject();
        if (stack instanceof ItemStack) {
            ItemStack item = (ItemStack) stack;
            result.addProperty("id", String.valueOf(item.func_77973_b().getRegistryName())); result.addProperty("damage", item.func_77952_i());
            result.addProperty("nbt", String.valueOf(item.func_77978_p())); result.addProperty("literal", item.func_82837_s());
            if (localized) { result.addProperty("rawKey", item.func_77973_b().func_77667_c(item) + ".name"); result.addProperty("nativeName", item.func_82833_r()); }
        } else {
            FluidStack fluid = (FluidStack) stack; result.addProperty("id", fluid.getFluid().getName()); result.addProperty("nbt", String.valueOf(fluid.tag)); if (localized) result.addProperty("nativeName", fluid.getLocalizedName());
        }
        return result;
    }

    public static JsonObject ui(JsonObject params) throws Exception {
        GuiScreen screen = Minecraft.func_71410_x().field_71462_r;
        if (screen == null || (!screen.getClass().getName().equals(R + "gui.grid.GuiGrid") && !screen.getClass().getName().equals(R + "gui.GuiCrafterManager"))) throw new IllegalStateException("Expected real RS screen");
        boolean manager = screen.getClass().getName().endsWith("GuiCrafterManager");
        GuiTextField field = (GuiTextField) NativeAccess.call(screen, "getSearchField");
        if (params.has("query")) {
            String query = params.get("query").getAsString(); if (query.length() > 128) throw new IllegalArgumentException("Query too long");
            field.func_146195_b(true); field.func_146190_e(0); field.func_146199_i(field.func_146179_b().length());
            field.func_146201_a('\b', 14);
            for (char letter : query.toCharArray()) field.func_146201_a(letter, 0);
        }
        Object bar = NativeAccess.call(screen, "getScrollbar");
        if (params.has("scrollEnd")) NativeAccess.call(bar, "setOffset", Math.max(0, (Integer) NativeAccess.get(bar, "maxOffset")));
        if (params.has("scroll")) NativeAccess.call(bar, "setOffset", params.get("scroll").getAsInt());
        if (params.has("cursor")) field.func_146190_e(params.get("cursor").getAsInt());
        if (params.has("selection")) field.func_146199_i(params.get("selection").getAsInt());
        if (params.has("focused")) field.func_146195_b(params.get("focused").getAsBoolean());
        JsonObject result = new JsonObject(); result.addProperty("class", screen.getClass().getName());
        result.addProperty("screen", System.identityHashCode(screen)); result.addProperty("container", System.identityHashCode(((GuiContainer) screen).field_147002_h));
        result.addProperty("containerWindow", ((GuiContainer) screen).field_147002_h.field_75152_c);
        result.addProperty("query", field.func_146179_b()); result.addProperty("cursor", field.func_146198_h()); result.addProperty("selection", field.func_146186_n()); result.addProperty("focused", field.func_146206_l());
        result.addProperty("rows", (Integer) NativeAccess.call(screen, "getRows")); result.addProperty("visibleRows", (Integer) NativeAccess.call(screen, "getVisibleRows"));
        result.addProperty("topHeight", (Integer) NativeAccess.call(screen, "getTopHeight"));
        result.addProperty("scroll", (Integer) NativeAccess.call(bar, "getOffset")); result.addProperty("maxScroll", (Integer) NativeAccess.get(bar, "maxOffset"));
        if (manager) manager(screen, params.has("raw") && params.get("raw").getAsBoolean(), result); else grid(screen, params, result);
        return result;
    }

    private static void grid(GuiScreen screen, JsonObject params, JsonObject result) throws Exception {
        boolean raw = params.has("raw") && params.get("raw").getAsBoolean();
        Object grid = NativeAccess.call(screen, "getGrid"), view = NativeAccess.call(screen, "getView");
        result.addProperty("active", (Boolean) NativeAccess.call(grid, "isActive")); result.addProperty("view", System.identityHashCode(view));
        for (String option : new String[]{"ViewType", "SortingType", "SortingDirection", "TabSelected", "TabPage"}) result.addProperty(option, (Integer) NativeAccess.call(grid, "get" + option));
        if (more()) result.addProperty("lockedUUID", String.valueOf(NativeAccess.call(screen, "getLockedStackUUID")));
        if (view == null) return;
        Map<?, ?> map = (Map<?, ?>) NativeAccess.get(view, "map"); List<?> visible = (List<?>) NativeAccess.call(view, "getStacks");
        JsonArray retained = new JsonArray(), rows = new JsonArray();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object value = entry.getValue(), source = NativeAccess.call(value, "getStack"); JsonObject row = ingredient(source, !raw);
            row.addProperty("key", String.valueOf(entry.getKey())); row.addProperty("identity", System.identityHashCode(value));
            row.addProperty("quantity", ((Number) NativeAccess.call(value, "getQuantity")).longValue());
            row.addProperty("quantityText", String.valueOf(NativeAccess.call(value, "getQuantity")));
            row.addProperty("craftable", (Boolean) NativeAccess.call(value, "isCraftable"));
            if (!raw) row.addProperty("name", (String) NativeAccess.call(value, "getName"));
            if (more()) row.addProperty("otherId", String.valueOf(NativeAccess.call(value, "getOtherId")));
            if (source instanceof ItemStack) {
                row.add("cachedName", gson.toJsonTree(NativeAccess.get(value, "cachedName")));
                row.add("cachedTooltipBefore", gson.toJsonTree(NativeAccess.get(value, "tooltip")));
                if (!raw && params.has("warm")) {
                    row.addProperty("tooltip", (String) (more() ? NativeAccess.call(value, "getTooltip", true) : NativeAccess.call(value, "getTooltip")));
                    if (more()) row.addProperty("freshTooltip", (String) NativeAccess.call(value, "getTooltip", false));
                }
            }
            retained.add(row);
        }
        for (Object value : visible) rows.add(String.valueOf(NativeAccess.call(value, more() ? "getId" : "getHash")));
        result.add("retained", retained); result.add("visible", rows);
        if (params.has("hover")) {
            String id = params.get("hover").getAsString(); int index = -1;
            for (int n = 0; n < visible.size(); n++) {
                JsonObject candidate = ingredient(NativeAccess.call(visible.get(n), "getStack"));
                if (candidate.get("id").getAsString().equals(id)
                        && (!params.has("hoverNbt") || candidate.get("nbt").equals(params.get("hoverNbt")))) { index = n; break; }
            }
            if (index < 0) throw new IllegalArgumentException("No visible ingredient " + id);
            int offset = (Integer) NativeAccess.call(bar(screen), "getOffset"), relative = index - offset * 9;
            if (relative < 0 || relative >= (Integer) NativeAccess.call(screen, "getVisibleRows") * 9) throw new IllegalArgumentException("Ingredient outside viewport");
            result.addProperty("x", (Integer) NativeAccess.call(screen, "getGuiLeft") + 7 + (relative % 9) * 18 + 8);
            result.addProperty("y", (Integer) NativeAccess.call(screen, "getGuiTop") + 19 + (relative / 9) * 18 + 8);
        }
    }

    private static Object bar(GuiScreen screen) throws Exception { return NativeAccess.call(screen, "getScrollbar"); }

    public static void capture(List<String> main, List<String> small, boolean detailed, ItemStack stack, int x, int y, int width, int height, net.minecraft.client.gui.FontRenderer font) {
        if (!TooltipProbe.capturing) return;
        JsonObject result = new JsonObject(); result.add("main", gson.toJsonTree(new ArrayList<>(main))); result.add("small", gson.toJsonTree(new ArrayList<>(small)));
        result.addProperty("detailed", detailed); result.add("item", ingredient(stack));
        result.addProperty("x", x); result.addProperty("y", y); result.addProperty("width", width); result.addProperty("height", height);
        result.addProperty("fontIdentity", System.identityHashCode(font)); TooltipProbe.refined = result;
    }

    private static void manager(GuiScreen screen, boolean raw, JsonObject result) throws Exception {
        Object container = NativeAccess.get(screen, "container");
        result.addProperty("active", (Boolean) NativeAccess.call(NativeAccess.call(screen, "getCrafterManager"), "isActive"));
        result.add("groups", gson.toJsonTree(NativeAccess.get(container, "containerData")));
        result.add("headings", gson.toJsonTree(NativeAccess.call(container, "getHeadings")));
        Map<?, ?> inventories = (Map<?, ?>) NativeAccess.get(container, "dummyInventories"); JsonArray all = new JsonArray(), slots = new JsonArray();
        for (Map.Entry<?, ?> entry : inventories.entrySet()) {
            IItemHandlerModifiable inventory = (IItemHandlerModifiable) entry.getValue();
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                JsonObject row = pattern(inventory.getStackInSlot(slot), !raw); row.addProperty("group", entry.getKey().toString()); row.addProperty("index", slot); all.add(row);
            }
        }
        for (Slot slot : ((GuiContainer) screen).field_147002_h.field_75151_b) if (slot.getClass().getName().equals(R + "container.slot.SlotCrafterManager")) {
            JsonObject row = pattern(slot.func_75211_c(), !raw); row.addProperty("x", slot.field_75223_e); row.addProperty("y", slot.field_75221_f); row.addProperty("selected", (Boolean) NativeAccess.get(slot, "visible")); row.addProperty("enabled", slot.func_111238_b()); slots.add(row);
        }
        result.add("patterns", all); result.add("slots", slots);
    }

    private static JsonObject pattern(ItemStack stack, boolean localized) throws Exception {
        JsonObject result = new JsonObject(); result.addProperty("empty", stack.func_190926_b());
        result.addProperty("nbt", String.valueOf(stack.func_77978_p())); JsonArray outputs = new JsonArray();
        if (!stack.func_190926_b()) for (int index = 0; index < 9; index++) {
            ItemStack output = (ItemStack) NativeAccess.call(type("item.ItemPattern"), "getOutputSlot", stack, index);
            if (output != null && !output.func_190926_b()) outputs.add(ingredient(output, localized));
        }
        result.add("outputs", outputs); return result;
    }
}
