package com.starskyxiii.polyglottooltip.testbridge;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.core.AEConfig;
import appeng.api.definitions.IItemDefinition;
import appeng.api.parts.IPartHost;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.AEColor;
import appeng.api.util.AEPartLocation;
import appeng.me.helpers.BaseActionSource;
import appeng.tile.storage.TileChest;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AddonWorldProbe {
    private static final BlockPos terminal = new BlockPos(20, 4, 0), machine = new BlockPos(19, 4, 0);
    private static String mode;
    private AddonWorldProbe() { }

    public static void world(JsonObject params, CompletableFuture<JsonElement> future) {
        Minecraft client = Minecraft.func_71410_x(); MinecraftServer server = client.func_71401_C();
        if (server == null || client.field_71439_g == null) throw new IllegalStateException("No integrated player");
        UUID id = client.field_71439_g.func_110124_au();
        server.func_152344_a(() -> {
            if (future.isDone()) return;
            try {
                WorldServer world = server.func_71218_a(0); EntityPlayerMP player = server.func_184103_al().func_177451_a(id);
                if (player == null || !world.func_72912_H().func_76065_j().startsWith("pt112-")) throw new IllegalStateException("Not an owned fixture world");
                future.complete(prepare(world, player, params));
            } catch (Throwable error) { future.completeExceptionally(error); }
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static JsonObject prepare(WorldServer world, EntityPlayerMP player, JsonObject params) throws Exception {
        String requested = params.get("mode").getAsString();
        if (!requested.equals("ec") && !requested.equals("the") && !requested.equals("gas") && !requested.equals("gasConfig")) throw new IllegalArgumentException("Unknown addon terminal");
        mode = requested;
        boolean gas = mode.startsWith("gas"), config = mode.equals("gasConfig");
        if (params.has("rename")) NativeAccess.call(world.func_175625_s(machine), "setName", params.get("rename").getAsString());
        else if (!params.has("open") || !params.get("open").getAsBoolean()) {
            world.func_175698_g(terminal); world.func_175698_g(machine);
            world.func_180501_a(terminal, AEApi.instance().definitions().blocks().multiPart().maybeBlock().get().func_176223_P(), 3);
            world.func_180501_a(terminal.func_177982_a(1, 0, 0), AEApi.instance().definitions().blocks().energyCellCreative().maybeBlock().get().func_176223_P(), 3);
            ItemStack part;
            if (gas) part = new ItemStack((Item) NativeAccess.get(NativeAccess.type("com.mekeng.github.common.ItemAndBlocks"), config ? "GAS_INTERFACE_TERMINAL" : "GAS_TERMINAL"));
            else if (mode.equals("ec")) {
                int metadata = (Integer) NativeAccess.call(NativeAccess.type("extracells.registries.PartEnum"), "getPartID", NativeAccess.type("extracells.part.fluid.PartFluidTerminal"));
                part = (ItemStack) NativeAccess.call(NativeAccess.get(NativeAccess.type("extracells.registries.ItemEnum"), "PARTITEM"), "getDamagedStack", metadata);
            } else {
                Object api = NativeAccess.call(NativeAccess.type("thaumicenergistics.api.ThEApi"), "instance");
                part = ((IItemDefinition) NativeAccess.call(NativeAccess.call(api, "items"), "arcaneTerminal")).maybeStack(1).get();
            }
            IPartHost host = (IPartHost) world.func_175625_s(terminal);
            if (host.addPart(AEApi.instance().definitions().parts().cableGlass().stack(AEColor.TRANSPARENT, 1), AEPartLocation.INTERNAL, player, EnumHand.MAIN_HAND) == null
                    || host.addPart(part, AEPartLocation.SOUTH, player, EnumHand.MAIN_HAND) == null) throw new IllegalStateException("Could not place native addon terminal");
            if (config) {
                world.func_180501_a(machine, ((Block) NativeAccess.get(NativeAccess.type("com.mekeng.github.common.ItemAndBlocks"), "GAS_INTERFACE")).func_176223_P(), 3);
                Object duality = NativeAccess.call(world.func_175625_s(machine), "getDualityGasInterface");
                NativeAccess.call(NativeAccess.call(duality, "getConfig"), "setGas", 0, AddonNameProbe.gas("hydrogen", 1000));
            } else {
                world.func_180501_a(machine, AEApi.instance().definitions().blocks().chest().maybeBlock().get().func_176223_P(), 3);
                ItemStack cell = gas ? new ItemStack((Item) NativeAccess.get(NativeAccess.type("com.mekeng.github.common.ItemAndBlocks"), "GAS_CELL_64k"))
                        : (mode.equals("ec") ? AEApi.instance().definitions().items().fluidCell64k() : AEApi.instance().definitions().items().cell64k()).maybeStack(1).get();
                Class channelType = gas ? NativeAccess.type("com.mekeng.github.common.me.storage.IGasStorageChannel") : mode.equals("ec") ? IFluidStorageChannel.class : IItemStorageChannel.class;
                IStorageChannel channel = AEApi.instance().storage().getStorageChannel(channelType);
                ICellInventoryHandler inventory = AEApi.instance().registries().cell().getCellInventory(cell, null, channel);
                if (gas || mode.equals("ec")) {
                    for (String name : gas ? new String[]{"hydrogen", "oxygen"} : new String[]{"water", "lava"}) {
                        Object content = gas ? AddonNameProbe.gas(name, 16000) : new FluidStack(FluidRegistry.getFluid(name), 16000);
                        IAEStack record = (IAEStack) NativeAccess.call(channel, "createStack", content);
                        if (inventory.injectItems(record, Actionable.MODULATE, new BaseActionSource()) != null) throw new IllegalStateException("Native cell refused content");
                    }
                } else {
                    for (Item item : new Item[]{net.minecraft.init.Items.field_151045_i, net.minecraft.init.Items.field_151042_j, net.minecraft.init.Items.field_151034_e}) {
                        IAEStack record = (IAEStack) NativeAccess.call(channel, "createStack", new ItemStack(item, 64));
                        if (inventory.injectItems(record, Actionable.MODULATE, new BaseActionSource()) != null) throw new IllegalStateException("Native cell refused item");
                    }
                }
                ((IItemHandlerModifiable) NativeAccess.get(world.func_175625_s(machine), "cellInventory")).setStackInSlot(0, cell);
            }
            player.field_71135_a.func_147364_a(20.5, 5, 2.5, 180, 30);
        }
        JsonObject result = new JsonObject(); result.addProperty("mode", mode);
        if (!config) result.addProperty("powered", ((TileChest) world.func_175625_s(machine)).isPowered());
        if (params.has("open") && params.get("open").getAsBoolean()) result.addProperty("opened", ((IPartHost) world.func_175625_s(terminal)).getPart(AEPartLocation.SOUTH).onActivate(player, EnumHand.MAIN_HAND, new Vec3d(0.5, 0.5, 1)));
        return result;
    }

    public static JsonObject ui(JsonObject params) throws Exception {
        GuiScreen screen = current();
        if (params.has("tooltips")) AEConfig.instance().getConfigManager().putSetting(Settings.SEARCH_TOOLTIPS, params.get("tooltips").getAsBoolean() ? YesNo.YES : YesNo.NO);
        GuiTextField field = (GuiTextField) NativeAccess.get(screen, mode.equals("ec") ? "searchbar" : mode.equals("gasConfig") ? "searchFieldInputs" : "searchField");
        if (params.has("query")) {
            field.func_146180_a(params.get("query").getAsString());
            if (mode.equals("ec")) NativeAccess.call(screen, "updateFluids");
            else if (mode.equals("gasConfig")) NativeAccess.call(screen, "refreshList");
            else {
                Object repo = NativeAccess.get(screen, "repo"); NativeAccess.call(repo, "setSearchString", field.func_146179_b()); NativeAccess.call(repo, "updateView");
                NativeAccess.call(screen, mode.equals("the") ? "updateScroll" : "setScrollBar");
            }
        }
        if (params.has("select") && mode.equals("ec")) {
            NativeAccess.call(NativeAccess.get(screen, "containerTerminal"), "setSelectedFluid", FluidRegistry.getFluid(params.get("select").getAsString()));
            NativeAccess.call(screen, "updateSelectedFluid");
        }
        if (params.has("type") && mode.equals("ec")) {
            String type = params.get("type").getAsString(); if (!type.equals("FLUID") && !type.equals("GAS")) throw new IllegalArgumentException("Unknown storage type");
            NativeAccess.set(screen, "type", NativeAccess.get(NativeAccess.type("extracells.container.StorageType"), type)); NativeAccess.call(screen, "updateFluids");
        }
        if (params.has("cursor")) field.func_146190_e(params.get("cursor").getAsInt());
        if (params.has("selection")) field.func_146199_i(params.get("selection").getAsInt());
        if (params.has("focused")) field.func_146195_b(params.get("focused").getAsBoolean());
        JsonObject result = new JsonObject(); result.addProperty("class", screen.getClass().getName());
        if (mode.equals("the")) {
            boolean optimized;
            try { NativeAccess.field(NativeAccess.get(screen, "repo"), "changed"); optimized = true; } catch (NoSuchFieldException ignored) { optimized = false; }
            result.addProperty("optimized", optimized);
        }
        result.addProperty("nativeName", mode.equals("ec") ? new FluidStack(FluidRegistry.WATER, 1000).getLocalizedName()
                : mode.equals("the") ? new ItemStack(net.minecraft.init.Items.field_151045_i).func_82833_r()
                : (String) NativeAccess.call(NativeAccess.call(AddonNameProbe.gas("hydrogen", 1), "getGas"), "getLocalizedName"));
        result.addProperty("query", field.func_146179_b()); result.addProperty("cursor", field.func_146198_h());
        result.addProperty("selection", field.func_146186_n()); result.addProperty("focused", field.func_146206_l());
        JsonArray rows = new JsonArray();
        if (mode.equals("ec")) {
            for (Object widget : (Iterable<?>) NativeAccess.get(screen, "fluidWidgets")) rows.add(((Fluid) NativeAccess.call(widget, "getFluid")).getName());
            result.addProperty("scroll", ((Number) NativeAccess.get(screen, "currentScroll")).intValue());
            Object selected = NativeAccess.get(screen, "currentFluid");
            result.addProperty("selected", selected == null ? "" : ((IAEFluidStack) selected).getFluidStack().getFluid().getName());
        } else if (mode.equals("gasConfig")) {
            for (Object row : (Iterable<?>) NativeAccess.get(screen, "lines")) rows.add(row instanceof String ? (String) row : (String) NativeAccess.call(row, "getName"));
            result.addProperty("available", ((Map<?, ?>) NativeAccess.get(screen, "byId")).size());
        } else {
            for (Object stack : (Iterable<?>) NativeAccess.get(NativeAccess.get(screen, "repo"), "view")) rows.add(stack instanceof IAEItemStack ? String.valueOf(((IAEItemStack) stack).getItem().getRegistryName())
                    : (String) NativeAccess.call(NativeAccess.call(stack, "getGas"), "getName"));
            Object scroll = NativeAccess.get(screen, mode.equals("the") ? "scrollBar" : "myScrollBar");
            result.addProperty("scroll", ((Number) NativeAccess.get(scroll, mode.equals("the") ? "currentPosition" : "currentScroll")).intValue());
        }
        result.add("rows", rows); return result;
    }

    private static GuiScreen current() {
        GuiScreen screen = Minecraft.func_71410_x().field_71462_r;
        String expected = mode == null ? "" : mode.equals("ec") ? "extracells.gui.GuiTerminal" : mode.equals("the") ? "thaumicenergistics.client.gui.part.GuiArcaneTerminal"
                : "com.mekeng.github.client.gui.GuiGas" + (mode.equals("gasConfig") ? "InterfaceConfigurationTerminal" : "Terminal");
        if (screen == null || !screen.getClass().getName().equals(expected)) throw new IllegalStateException("Expected native addon screen " + expected);
        return screen;
    }

    public static JsonObject hover(JsonObject params) throws Exception {
        GuiScreen screen = current();
        if (mode.equals("ec")) return fluidHover(screen, params);
        if (!mode.equals("gas")) throw new IllegalStateException("Expected gas terminal");
        int left = ((Number) NativeAccess.get(screen, "field_147003_i")).intValue(), top = ((Number) NativeAccess.get(screen, "field_147009_r")).intValue();
        for (Slot slot : ((GuiContainer) screen).field_147002_h.field_75151_b) {
            if (!slot.getClass().getName().equals("com.mekeng.github.client.slots.SlotGasME")) continue;
            Object stack = NativeAccess.call(slot, "getAEGasStack");
            if (stack == null || !Boolean.TRUE.equals(NativeAccess.call(slot, "shouldRenderAsGas"))) continue;
            int x = left + slot.field_75223_e + 8, y = top + slot.field_75221_f + 8;
            TooltipProbe.capturing = true; TooltipProbe.captured = null;
            try { NativeAccess.call(screen, "func_191948_b", x, y); } finally { TooltipProbe.capturing = false; }
            if (TooltipProbe.captured == null) throw new IllegalStateException("Native gas tooltip absent");
            JsonObject result = new JsonObject(); JsonArray lines = new JsonArray(); for (String line : TooltipProbe.captured) lines.add(line);
            result.add("lines", lines); result.addProperty("x", x); result.addProperty("y", y); result.addProperty("name", (String) NativeAccess.call(NativeAccess.call(stack, "getGas"), "getLocalizedName")); return result;
        }
        throw new IllegalStateException("No visible native gas slot");
    }

    private static JsonObject fluidHover(GuiScreen screen, JsonObject params) throws Exception {
        boolean request = params.has("request") && params.get("request").getAsBoolean();
        Object widget;
        if (request) widget = NativeAccess.create("extracells.gui.widget.fluid.WidgetFluidRequest", screen, FluidRegistry.WATER);
        else {
            IAEFluidStack stack = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class).createStack(new FluidStack(FluidRegistry.WATER, 16000));
            widget = NativeAccess.create("extracells.gui.widget.fluid.WidgetFluidSelector", screen, stack);
            if (params.has("zero") && params.get("zero").getAsBoolean()) NativeAccess.call(widget, "setAmount", 0L);
        }
        int left = (Integer) NativeAccess.call(screen, "guiLeft"), top = (Integer) NativeAccess.call(screen, "guiTop");
        int x = params.has("outside") && params.get("outside").getAsBoolean() ? left - 40 : left + 1, y = top + 19;
        TooltipProbe.capturing = true; TooltipProbe.captured = null;
        boolean handled;
        try { handled = (Boolean) NativeAccess.call(widget, "drawTooltip", 0, 0, x, y); } finally { TooltipProbe.capturing = false; }
        if (handled && (TooltipProbe.captured == null || TooltipProbe.captured.isEmpty())) throw new IllegalStateException("Native ExtraCells draw was not captured");
        JsonObject result = new JsonObject(); JsonArray lines = new JsonArray();
        if (TooltipProbe.captured != null) for (String line : TooltipProbe.captured) lines.add(line);
        result.add("lines", lines); result.addProperty("handled", handled); result.addProperty("x", x); result.addProperty("y", y); return result;
    }
}
