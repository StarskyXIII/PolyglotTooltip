package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public final class IntegratedTerminalProbe {
    private static final BlockPos terminal = new BlockPos(80, 4, 0);
    private static final String HELPERS = "org.cyclops.integrateddynamics.core.helper.";
    private static final String COMPONENT = "org.cyclops.commoncapabilities.api.ingredient.IngredientComponent";
    private static final String GUI = "org.cyclops.integratedterminals.client.gui.container.GuiTerminalStorage";
    private IntegratedTerminalProbe() { }

    public static void world(JsonObject params, CompletableFuture<JsonElement> future) throws Exception {
        Minecraft client = Minecraft.func_71410_x(); MinecraftServer server = client.func_71401_C();
        if (server == null || client.field_71439_g == null) throw new IllegalStateException("No integrated player");
        UUID id = client.field_71439_g.func_110124_au();
        World initialWorld = client.field_71441_e; EntityPlayer initialPlayer = client.field_71439_g;
        boolean open = params.has("open") && params.get("open").getAsBoolean();
        if (open && params.has("prepare") && params.get("prepare").getAsBoolean()) throw new IllegalArgumentException("Prepare and open require separate native sync steps");
        if (!server.func_71218_a(0).func_72912_H().func_76065_j().startsWith("pt112-")) throw new IllegalStateException("Not an owned terminal world");
        if (open) activate(client.field_71441_e, client.field_71439_g);
        server.func_152344_a(() -> {
            if (future.isDone()) return;
            try {
                if (client.func_71401_C() != server || client.field_71441_e != initialWorld || client.field_71439_g != initialPlayer) throw new IllegalStateException("Terminal fixture context changed");
                EntityPlayerMP player = server.func_184103_al().func_177451_a(id); WorldServer world = server.func_71218_a(0);
                if (player == null || player.field_70170_p != world || !world.func_72912_H().func_76065_j().startsWith("pt112-")) throw new IllegalStateException("Not an owned terminal world");
                if (params.has("prepare") && params.get("prepare").getAsBoolean()) prepare(world, player);
                JsonObject result = network(world);
                if (open) { activate(world, player); result.addProperty("opened", true); }
                future.complete(result);
            } catch (Throwable error) { future.completeExceptionally(error); }
        });
    }
    private static Object partContainer(World world, BlockPos pos, EnumFacing side) throws Exception {
        Object container = NativeAccess.call(NativeAccess.type(HELPERS + "PartHelpers"), "getPartContainer", world, pos, side);
        if (container == null) throw new IllegalStateException("Native part container missing " + pos); return container;
    }
    private static void activate(World world, EntityPlayer player) throws Exception {
        Object container = partContainer(world, terminal, EnumFacing.SOUTH), part = NativeAccess.call(container, "getPart", EnumFacing.SOUTH);
        Object state = NativeAccess.call(container, "getPartState", EnumFacing.SOUTH);
        if (part == null || state == null) throw new IllegalStateException("Native terminal part not synced");
        if (!(Boolean) NativeAccess.call(part, "onPartActivated", world, terminal, state, player, EnumHand.MAIN_HAND, ItemStack.field_190927_a, EnumFacing.SOUTH, 0.5f, 0.5f, 1f)) throw new IllegalStateException("Native terminal activation rejected");
    }
    private static void add(WorldServer world, BlockPos pos, EnumFacing side, Object part, int channel) throws Exception {
        Object state = NativeAccess.call(part, "getDefaultState");
        if (channel >= 0) NativeAccess.call(state, "setChannelInterface", channel);
        if (!(Boolean) NativeAccess.call(NativeAccess.type(HELPERS + "PartHelpers"), "addPart", world, pos, side, part, state)) throw new IllegalStateException("Native part rejected " + pos + " " + side);
        Object container = partContainer(world, pos, side);
        if (NativeAccess.call(container, "getPartState", side) != state) throw new IllegalStateException("Native part state was replaced");
    }
    private static void prepare(WorldServer world, EntityPlayerMP player) throws Exception {
        for (int x = 79; x <= 95; x++) for (int z = -2; z <= 2; z++) for (int y = 4; y <= 6; y++) world.func_175698_g(new BlockPos(x, y, z));
        Block cable = (Block) NativeAccess.call(NativeAccess.type("org.cyclops.integrateddynamics.block.BlockCable"), "getInstance");
        Block basin = (Block) NativeAccess.call(NativeAccess.type("org.cyclops.integrateddynamics.block.BlockMechanicalDryingBasin"), "getInstance");
        Object itemPart = NativeAccess.get(NativeAccess.type("org.cyclops.integratedtunnels.part.PartTypes"), "INTERFACE_ITEM");
        Object fluidPart = NativeAccess.get(NativeAccess.type("org.cyclops.integratedtunnels.part.PartTypes"), "INTERFACE_FLUID");
        List<Item> items = new ArrayList<>();
        for (Item item : Item.field_150901_e) if (item.getRegistryName() != null && item.getRegistryName().func_110624_b().equals("minecraft") && item != net.minecraft.init.Items.field_190931_a) items.add(item);
        items.sort((a, b) -> a.getRegistryName().toString().compareTo(b.getRegistryName().toString()));
        if (items.size() < 96) throw new IllegalStateException("Insufficient native storage entries");
        for (int index = 0; index < 8; index++) {
            BlockPos chest = new BlockPos(80 + index * 2, 4, -1); world.func_180501_a(chest, Blocks.field_150486_ae.func_176223_P(), 3);
            TileEntityChest tile = (TileEntityChest) world.func_175625_s(chest);
            for (int slot = 0; slot < 12; slot++) tile.func_70299_a(slot, new ItemStack(items.get(index * 12 + slot), 64)); tile.func_70296_d();
        }
        for (int index = 0; index < 2; index++) {
            BlockPos pos = new BlockPos(82 + index * 2, 5, 0); world.func_180501_a(pos, basin.func_176223_P(), 3);
            IFluidTank tank = (IFluidTank) NativeAccess.call(world.func_175625_s(pos), "getTankOutput");
            if (tank.fill(new FluidStack(index == 0 ? FluidRegistry.WATER : FluidRegistry.LAVA, 16000), true) != 16000) throw new IllegalStateException("Native tank refused fixture fluid");
            world.func_175625_s(pos).func_70296_d();
        }
        for (int x = 80; x <= 94; x++) world.func_180501_a(new BlockPos(x, 4, 0), cable.func_176223_P(), 3);
        for (int index = 0; index < 8; index++) add(world, new BlockPos(80 + index * 2, 4, 0), EnumFacing.NORTH, itemPart, index % 2);
        for (int index = 0; index < 2; index++) add(world, new BlockPos(82 + index * 2, 4, 0), EnumFacing.UP, fluidPart, index);
        add(world, terminal, EnumFacing.SOUTH, NativeAccess.get(NativeAccess.type("org.cyclops.integratedterminals.part.PartTypes"), "TERMINAL_STORAGE"), -1);
        player.func_70095_a(false); player.field_71135_a.func_147364_a(80.5, 4, 2.5, 180, 20);
    }
    private static JsonObject network(World world) throws Exception {
        JsonObject result = new JsonObject(); Object network = NativeAccess.call(NativeAccess.type(HELPERS + "NetworkHelpers"), "getNetwork", world, terminal, EnumFacing.SOUTH);
        result.addProperty("present", network != null); if (network == null) return result;
        result.addProperty("identity", System.identityHashCode(network));
        for (String key : new String[]{"isInitialized", "isKilled", "isCrashed"}) result.addProperty(key, (Boolean) NativeAccess.call(network, key));
        JsonArray states = new JsonArray();
        for (int index = 0; index < 10; index++) {
            BlockPos pos = index < 8 ? new BlockPos(80 + index * 2, 4, 0) : new BlockPos(82 + (index - 8) * 2, 4, 0);
            EnumFacing face = index < 8 ? EnumFacing.NORTH : EnumFacing.UP;
            Object state = NativeAccess.call(partContainer(world, pos, face), "getPartState", face);
            JsonObject row = new JsonObject(); row.addProperty("class", state.getClass().getName()); row.addProperty("id", String.valueOf(NativeAccess.call(state, "getId")));
            row.addProperty("valid", (Boolean) NativeAccess.call(state, "isValidTargetCapability")); row.addProperty("channel", (Integer) NativeAccess.call(state, "getChannelInterface"));
            row.addProperty("position", String.valueOf(NativeAccess.call(state, "getPos"))); row.addProperty("network", System.identityHashCode(NativeAccess.call(state, "getPositionedAddonsNetwork"))); states.add(row);
        }
        result.add("states", states); return result;
    }
    public static JsonObject ui(JsonObject params) throws Exception {
        GuiScreen screen = current(); Object container = NativeAccess.call(screen, "getContainer");
        if (params.has("tab")) NativeAccess.call(screen, "setTabByIndex", params.get("tab").getAsInt());
        if (params.has("channel")) {
            Object field = NativeAccess.get(screen, "fieldChannel"); List<?> elements = (List<?>) NativeAccess.get(field, "elements");
            int channel = params.get("channel").getAsInt(), desired = channel == -1 ? 0 : elements.indexOf(Integer.toString(channel));
            if (desired < 0) {
                clickChannel(screen, field, true);
                elements = (List<?>) NativeAccess.get(field, "elements"); desired = elements.indexOf(Integer.toString(channel));
            }
            if (desired < 0) throw new IllegalArgumentException("Native terminal channel unavailable");
            for (int attempt = 0; attempt <= elements.size(); attempt++) {
                int index = (Integer) NativeAccess.get(field, "activeElement"); if (index == desired) break;
                if (attempt == elements.size()) throw new IllegalStateException("Native channel control did not reach target");
                clickChannel(screen, field, index < desired);
            }
            if ((Integer) NativeAccess.call(container, "getSelectedChannel") != channel) throw new IllegalStateException("Native channel selection disagrees with control");
        }
        if (params.has("query")) {
            GuiTextField field = (GuiTextField) NativeAccess.call(screen, "getFieldSearch"); field.func_146195_b(true); field.func_146190_e(0); field.func_146199_i(field.func_146179_b().length());
            NativeAccess.call(screen, "func_73869_a", '\0', 14); for (char c : params.get("query").getAsString().toCharArray()) NativeAccess.call(screen, "func_73869_a", c, 0);
        }
        if (params.has("scroll")) NativeAccess.call(NativeAccess.get(screen, "scrollBar"), "scrollTo", params.get("scroll").getAsFloat());
        JsonObject input = null;
        if (params.has("select")) {
            if (!Minecraft.func_71410_x().field_71439_g.field_71071_by.func_70445_o().func_190926_b()) throw new IllegalStateException("Native selection requires empty carried stack");
            Object selected = ((Map<?, ?>) NativeAccess.get(container, "tabsClient")).get(NativeAccess.call(container, "getSelectedTab"));
            if (selected == null || (Integer) NativeAccess.get(selected, "activeSlotId") != -1) throw new IllegalStateException("Native selection requires a tab with no active slot");
            int index = params.get("select").getAsInt(), first = (Integer) NativeAccess.call(screen, "getSelectedFirstRow"), columns = (Integer) NativeAccess.call(screen, "getSlotRowLength"), rows = (Integer) NativeAccess.call(screen, "getSlotVisibleRows");
            int visible = index - first * columns; if (visible < 0 || visible >= rows * columns) throw new IllegalArgumentException("Native requested slot is not visible");
            int size = (Integer) NativeAccess.get(NativeAccess.type("org.cyclops.cyclopscore.helper.GuiHelpers"), "SLOT_SIZE");
            int x = (Integer) NativeAccess.call(screen, "getGuiLeftTotal") + (Integer) NativeAccess.call(screen, "getSlotsOffsetX") + visible % columns * size + 8;
            int y = (Integer) NativeAccess.call(screen, "getGuiTopTotal") + (Integer) NativeAccess.call(screen, "getSlotsOffsetY") + visible / columns * size + 8;
            if ((Integer) NativeAccess.call(screen, "getStorageSlotIndexAtPosition", x, y) != index) throw new IllegalStateException("Native storage geometry maps to another slot");
            NativeAccess.call(screen, "func_73864_a", x, y, 0); NativeAccess.call(screen, "func_146286_b", x, y, 0);
            input = new JsonObject(); input.addProperty("index", index); input.addProperty("x", x); input.addProperty("y", y);
        }
        if (params.has("prime") && params.get("prime").getAsBoolean()) for (Object tab : ((Map<?, ?>) NativeAccess.get(container, "tabsClient")).values()) for (int channel : new int[]{-1, 0, 1}) NativeAccess.call(tab, "getSlotCount", channel);
        JsonObject result = observe(); if (input != null) result.add("input", input); return result;
    }
    private static void clickChannel(GuiScreen screen, Object field, boolean right) throws Exception {
        net.minecraft.client.gui.GuiButton arrow = (net.minecraft.client.gui.GuiButton) NativeAccess.get(field, right ? "arrowRight" : "arrowLeft");
        if (!arrow.field_146124_l || !arrow.field_146125_m) throw new IllegalStateException("Native channel arrow unavailable");
        int x = arrow.field_146128_h + arrow.field_146120_f / 2, y = arrow.field_146129_i + arrow.field_146121_g / 2;
        NativeAccess.call(screen, "func_73864_a", x, y, 0); NativeAccess.call(screen, "func_146286_b", x, y, 0);
    }
    public static JsonObject observe() throws Exception {
        GuiScreen screen = current(); Object container = NativeAccess.call(screen, "getContainer"); JsonObject result = new JsonObject();
        result.addProperty("screen", System.identityHashCode(screen)); result.addProperty("container", System.identityHashCode(container)); result.addProperty("tab", String.valueOf(NativeAccess.call(container, "getSelectedTab")));
        result.addProperty("channel", (Integer) NativeAccess.call(container, "getSelectedChannel")); result.addProperty("query", ((GuiTextField) NativeAccess.call(screen, "getFieldSearch")).func_146179_b());
        result.addProperty("channelText", ((GuiTextField) NativeAccess.get(screen, "fieldChannel")).func_146179_b());
        JsonArray options = new JsonArray(); for (Object option : (List<?>) NativeAccess.get(NativeAccess.get(screen, "fieldChannel"), "elements")) options.add(String.valueOf(option)); result.add("channelOptions", options);
        result.addProperty("firstRow", (Integer) NativeAccess.get(screen, "firstRow")); result.addProperty("scroll", (Number) NativeAccess.get(NativeAccess.get(screen, "scrollBar"), "currentScroll"));
        JsonArray tabs = new JsonArray();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) NativeAccess.get(container, "tabsClient")).entrySet()) {
            Object tab = entry.getValue(); JsonObject row = new JsonObject(); row.addProperty("key", entry.getKey().toString()); row.addProperty("identity", System.identityHashCode(tab));
            for (String key : new String[]{"enabled", "activeSlotId", "activeSlotQuantity", "activeChannel"}) row.addProperty(key, String.valueOf(NativeAccess.get(tab, key)));
            List<?> activeView = (List<?>) ((Map<?, ?>) NativeAccess.get(tab, "filteredIngredientsViews")).get((Integer) NativeAccess.get(tab, "activeChannel"));
            int active = (Integer) NativeAccess.get(tab, "activeSlotId");
            if (activeView != null && active >= 0 && active < activeView.size()) row.add("active", ingredient(NativeAccess.call(activeView.get(active), "getInstance")));
            JsonObject queries = new JsonObject(); for (int channel : new int[]{-1, 0, 1}) queries.addProperty(Integer.toString(channel), (String) NativeAccess.call(tab, "getInstanceFilter", channel)); row.add("queries", queries);
            row.add("views", views((Map<?, ?>) NativeAccess.get(tab, "ingredientsViews"))); row.add("filtered", views((Map<?, ?>) NativeAccess.get(tab, "filteredIngredientsViews"))); tabs.add(row);
        }
        result.add("tabs", tabs); return result;
    }
    private static JsonArray views(Map<?, ?> views) throws Exception {
        JsonArray result = new JsonArray(); for (Map.Entry<?, ?> entry : views.entrySet()) {
            JsonObject row = new JsonObject(); row.addProperty("channel", ((Number) entry.getKey()).intValue()); row.addProperty("identity", System.identityHashCode(entry.getValue()));
            JsonArray values = new JsonArray(); for (Object wrapper : (List<?>) entry.getValue()) values.add(ingredient(NativeAccess.call(wrapper, "getInstance"))); row.add("rows", values); result.add(row);
        } return result;
    }
    private static JsonObject ingredient(Object value) {
        JsonObject result = new JsonObject();
        if (value instanceof ItemStack) { ItemStack stack = (ItemStack) value; result.addProperty("key", stack.func_77973_b().getRegistryName() + ":" + stack.func_77960_j()); result.addProperty("quantity", stack.func_190916_E()); result.addProperty("nbt", String.valueOf(stack.func_77978_p())); result.addProperty("nameKey", stack.func_77973_b().func_77667_c(stack) + ".name"); result.addProperty("nativeName", stack.func_82833_r()); }
        else if (value instanceof FluidStack) { FluidStack stack = (FluidStack) value; result.addProperty("key", stack.getFluid().getName()); result.addProperty("quantity", stack.amount); result.addProperty("nbt", String.valueOf(stack.tag)); result.addProperty("nameKey", stack.getFluid().getUnlocalizedName(stack)); result.addProperty("nativeName", stack.getLocalizedName()); }
        else throw new IllegalStateException("Unexpected native ingredient " + value.getClass().getName());
        return result;
    }
    @SuppressWarnings("unchecked")
    public static JsonObject predicate(JsonObject params) throws Exception {
        boolean fluid = params.has("fluid"); Object component = NativeAccess.get(NativeAccess.type(COMPONENT), fluid ? "FLUIDSTACK" : "ITEMSTACK");
        Object stack = fluid ? new FluidStack(FluidRegistry.getFluid(params.get("fluid").getAsString()), 1000) : new ItemStack(Item.field_150901_e.func_82594_a(new ResourceLocation(params.get("item").getAsString())));
        if (!fluid && params.has("custom")) ((ItemStack) stack).func_151001_c(params.get("custom").getAsString());
        if (!fluid && params.has("lore")) {
            net.minecraft.nbt.NBTTagList lore = new net.minecraft.nbt.NBTTagList();
            lore.func_74742_a(new net.minecraft.nbt.NBTTagString(params.get("lore").getAsString()));
            ((ItemStack) stack).func_190925_c("display").func_74782_a("Lore", lore);
        }
        Object query = NativeAccess.call(NativeAccess.type("org.cyclops.integratedterminals.core.terminalstorage.query.IIngredientQuery"), "parse", component, params.get("query").getAsString().toLowerCase(Locale.ENGLISH));
        JsonObject result = new JsonObject(); result.addProperty("class", query.getClass().getName()); result.addProperty("result", ((Predicate<Object>) query).test(stack)); result.add("ingredient", ingredient(stack)); return result;
    }
    private static GuiScreen current() { GuiScreen screen = Minecraft.func_71410_x().field_71462_r; if (screen == null || !screen.getClass().getName().equals(GUI)) throw new IllegalStateException("No native IT screen"); return screen; }
}
