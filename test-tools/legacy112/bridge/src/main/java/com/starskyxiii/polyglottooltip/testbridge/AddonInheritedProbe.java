package com.starskyxiii.polyglottooltip.testbridge;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.parts.IPartHost;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AEColor;
import appeng.api.util.AEPartLocation;
import appeng.me.helpers.BaseActionSource;
import appeng.tile.misc.TileSecurityStation;
import appeng.tile.networking.TileWireless;
import appeng.tile.storage.TileChest;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AddonInheritedProbe {
    private static final BlockPos center = new BlockPos(40, 4, 0);
    private static String mode;
    private static JsonObject names;
    private AddonInheritedProbe() { }

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

    private static JsonObject prepare(WorldServer world, EntityPlayerMP player, JsonObject params) throws Exception {
        String requested = params.get("mode").getAsString();
        if (!requested.equals("wct") && !requested.equals("ext") && !requested.equals("fc") && !requested.equals("fcExpanded") && !requested.equals("fcWireless")) throw new IllegalArgumentException("Unknown inherited terminal");
        mode = requested; boolean wireless = mode.equals("wct") || mode.equals("fcWireless");
        if (!params.has("action")) {
            player.field_71071_by.func_70299_a(0, ItemStack.field_190927_a); player.field_71071_by.field_70461_c = 0;
            for (BlockPos pos : new BlockPos[]{center, center.func_177982_a(-1, 0, 0), center.func_177982_a(-2, 0, 0), center.func_177982_a(1, 0, 0), center.func_177982_a(0, 0, -1), center.func_177982_a(0, 0, 1)}) world.func_175698_g(pos);
            world.func_180501_a(center, AEApi.instance().definitions().blocks().multiPart().maybeBlock().get().func_176223_P(), 3);
            world.func_180501_a(center.func_177982_a(1, 0, 0), AEApi.instance().definitions().blocks().energyCellCreative().maybeBlock().get().func_176223_P(), 3);
            NativeAccess.call(NativeAccess.call(world.func_175625_s(center.func_177982_a(1, 0, 0)), "getProxy"), "setOwner", player);
            IPartHost host = (IPartHost) world.func_175625_s(center);
            if (host.addPart(AEApi.instance().definitions().parts().cableGlass().stack(AEColor.TRANSPARENT, 1), AEPartLocation.INTERNAL, player, EnumHand.MAIN_HAND) == null) throw new IllegalStateException("No native cable");
            if (!wireless) {
                String type = mode.equals("ext") ? "com._0xc4de.ae2exttable.items.ItemRegistry" : "com.glodblock.github.loader.FCItems";
                String field = mode.equals("ext") ? "BASIC_TERMINAL" : mode.equals("fc") ? "PART_FLUID_PATTERN_TERMINAL" : "PART_EXTENDED_FLUID_PATTERN_TERMINAL";
                ItemStack part = new ItemStack((Item) NativeAccess.get(NativeAccess.type(type), field));
                if (host.addPart(part, AEPartLocation.SOUTH, player, EnumHand.MAIN_HAND) == null) throw new IllegalStateException("No native inherited part");
            }
            List<ItemStack> entries = new ArrayList<>(); entries.add(new ItemStack(net.minecraft.init.Items.field_151045_i));
            names = new JsonObject();
            for (Item item : Item.field_150901_e) {
                if (!item.getRegistryName().func_110624_b().equals("minecraft") || item == net.minecraft.init.Items.field_151045_i) continue;
                ItemStack stack = new ItemStack(item);
                if (stack.func_190926_b()) continue;
                entries.add(stack); if (entries.size() == 120) break;
            }
            IItemStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            for (int cellIndex = 0; cellIndex < 2; cellIndex++) {
                BlockPos pos = center.func_177982_a(-1 - cellIndex, 0, 0);
                world.func_180501_a(pos, AEApi.instance().definitions().blocks().chest().maybeBlock().get().func_176223_P(), 3);
                NativeAccess.call(NativeAccess.call(world.func_175625_s(pos), "getProxy"), "setOwner", player);
                ItemStack cell = AEApi.instance().definitions().items().cell64k().maybeStack(1).get();
                ICellInventoryHandler<IAEItemStack> inventory = AEApi.instance().registries().cell().getCellInventory(cell, null, channel);
                for (int index = cellIndex * 60; index < Math.min(entries.size(), (cellIndex + 1) * 60); index++) {
                    ItemStack item = entries.get(index); IAEItemStack record = channel.createStack(item); record.setStackSize(64);
                    if (inventory.injectItems(record, Actionable.MODULATE, new BaseActionSource()) != null) throw new IllegalStateException("Native cell refused entry");
                    names.addProperty(item.func_77977_a() + ".name", "PTWide " + index);
                }
                ((IItemHandlerModifiable) NativeAccess.get(world.func_175625_s(pos), "cellInventory")).setStackInSlot(0, cell);
            }
            if (wireless) {
                BlockPos securityPos = center.func_177982_a(0, 0, -1), accessPos = center.func_177982_a(0, 0, 1);
                world.func_180501_a(securityPos, AEApi.instance().definitions().blocks().securityStation().maybeBlock().get().func_176223_P(), 3);
                ((TileSecurityStation) world.func_175625_s(securityPos)).getProxy().setOwner(player);
                world.func_180501_a(accessPos, AEApi.instance().definitions().blocks().wirelessAccessPoint().maybeBlock().get().func_176223_P(), 3);
                TileWireless access = (TileWireless) world.func_175625_s(accessPos);
                access.getProxy().setOwner(player); access.setOrientation(EnumFacing.SOUTH, EnumFacing.UP);
            }
            player.field_71135_a.func_147364_a(40.5, 5, 3.5, 180, 30);
        }
        JsonObject result = new JsonObject(); result.addProperty("mode", mode); result.add("names", names);
        result.addProperty("powered", ((TileChest) world.func_175625_s(center.func_177982_a(-1, 0, 0))).isPowered());
        if (params.has("action")) {
            if (wireless) {
                TileSecurityStation security = (TileSecurityStation) world.func_175625_s(center.func_177982_a(0, 0, -1));
                TileWireless access = (TileWireless) world.func_175625_s(center.func_177982_a(0, 0, 1));
                result.addProperty("accessActive", access.isActive());
                result.addProperty("sameGrid", access.getGrid() == security.getProxy().getGrid());
                result.addProperty("registered", AEApi.instance().registries().locatable().getLocatableBy(security.getSecurityKey()) == security);
                result.addProperty("owner", security.getOwner());
                result.addProperty("playerId", AEApi.instance().registries().players().getID(player));
                if (params.get("action").getAsString().equals("link")) {
                    String itemClass = mode.equals("wct") ? "p455w0rd.wct.init.ModItems" : "com.glodblock.github.loader.FCItems";
                    Item item = (Item) NativeAccess.get(NativeAccess.type(itemClass), mode.equals("wct") ? "WCT" : "WIRELESS_FLUID_PATTERN_TERMINAL");
                    ItemStack stack = new ItemStack(item);
                    IWirelessTermHandler handler = AEApi.instance().registries().wireless().getWirelessTerminalHandler(stack);
                    if (handler == null) throw new IllegalStateException("Native wireless handler absent");
                    handler.setEncryptionKey(stack, Long.toString(security.getSecurityKey()), "PT fixture");
                    NativeAccess.call(item, "injectAEPower", stack, 1000000D, Actionable.MODULATE);
                    result.addProperty("charged", handler.hasPower(player, .5D, stack));
                    player.field_71071_by.func_70299_a(0, stack); player.field_71069_bz.func_75142_b();
                } else if (mode.equals("fcWireless")) {
                    player.field_71071_by.func_70301_a(0).func_77973_b().func_77659_a(world, player, EnumHand.MAIN_HAND);
                    result.addProperty("opened", true);
                }
            } else {
                result.addProperty("opened", ((IPartHost) world.func_175625_s(center)).getPart(AEPartLocation.SOUTH).onActivate(player, EnumHand.MAIN_HAND, new Vec3d(.5, .5, 1)));
            }
        }
        return result;
    }

    public static JsonObject openWireless() throws Exception {
        Minecraft client = Minecraft.func_71410_x();
        if (!"wct".equals(mode)) throw new IllegalStateException("Not a WCT fixture");
        ItemStack stack = client.field_71439_g.field_71071_by.func_70301_a(0);
        if (stack.func_190926_b()) throw new IllegalStateException("Terminal not synchronized");
        Object api = NativeAccess.call(NativeAccess.type("p455w0rd.wct.api.WCTApi"), "instance");
        NativeAccess.call(api, "openWCTGui", client.field_71439_g, false, 0);
        JsonObject result = new JsonObject(); result.addProperty("item", String.valueOf(stack.func_77973_b().getRegistryName())); return result;
    }

    public static JsonObject ui(JsonObject params) throws Exception {
        GuiScreen screen = Minecraft.func_71410_x().field_71462_r;
        String suffix = "wct".equals(mode) ? ".GuiWCT" : "ext".equals(mode) ? ".GuiBasicCraftingTerminal" : "fc".equals(mode) ? ".GuiFluidPatternTerminal" : "fcExpanded".equals(mode) ? ".GuiExtendedFluidPatternTerminal" : ".GuiWirelessFluidPatternTerminal";
        if (screen == null || !screen.getClass().getName().endsWith(suffix)) throw new IllegalStateException("Expected native inherited screen " + suffix);
        GuiTextField field = (GuiTextField) NativeAccess.get(screen, "searchField");
        if (params.has("focused")) field.func_146195_b(params.get("focused").getAsBoolean());
        if (params.has("cursor")) field.func_146190_e(params.get("cursor").getAsInt());
        if (params.has("selection")) field.func_146199_i(params.get("selection").getAsInt());
        Object repo = NativeAccess.get(screen, "repo"), scroll = NativeAccess.get(screen, "wct".equals(mode) ? "scrollBar" : "myScrollBar");
        if (params.has("scrollToEnd")) {
            for (int index = 0; index < 1000 && ((Number) NativeAccess.get(scroll, "currentScroll")).intValue() < ((Number) NativeAccess.get(scroll, "maxScroll")).intValue(); index++) NativeAccess.call(scroll, "wheel", -1);
        }
        JsonObject result = new JsonObject(); result.addProperty("class", screen.getClass().getName()); result.addProperty("repoClass", repo.getClass().getName());
        result.addProperty("query", field.func_146179_b()); result.addProperty("cursor", field.func_146198_h()); result.addProperty("selection", field.func_146186_n()); result.addProperty("focused", field.func_146206_l());
        result.addProperty("capacity", ((Number) NativeAccess.get(screen, "rows")).intValue() * ((Number) NativeAccess.get(screen, "perRow")).intValue());
        JsonObject scrollbar = new JsonObject();
        for (String name : new String[]{"currentScroll", "minScroll", "maxScroll"}) scrollbar.addProperty(name, ((Number) NativeAccess.get(scroll, name)).intValue());
        result.add("scroll", scrollbar); JsonArray rows = new JsonArray();
        for (Object value : (Iterable<?>) NativeAccess.get(repo, "view")) {
            IAEItemStack stack = (IAEItemStack) value; JsonObject row = new JsonObject();
            row.addProperty("id", String.valueOf(stack.getItem().getRegistryName())); row.addProperty("name", stack.getDefinition().func_82833_r()); row.addProperty("count", stack.getStackSize()); rows.add(row);
        }
        result.add("rows", rows); return result;
    }
}
