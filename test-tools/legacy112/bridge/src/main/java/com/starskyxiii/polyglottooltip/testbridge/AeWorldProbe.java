package com.starskyxiii.polyglottooltip.testbridge;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.me.FluidRepo;
import appeng.client.me.ItemRepo;
import appeng.me.helpers.BaseActionSource;
import appeng.tile.storage.TileChest;
import appeng.util.Platform;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AeWorldProbe {
    private static final BlockPos position = new BlockPos(0, 4, 0);
    private AeWorldProbe() { }

    public static void world(JsonObject params, CompletableFuture<JsonElement> future) {
        Minecraft client = Minecraft.func_71410_x();
        MinecraftServer server = client.func_71401_C();
        if (server == null || client.field_71439_g == null) throw new IllegalStateException("No integrated player");
        UUID playerId = client.field_71439_g.func_110124_au();
        server.func_152344_a(() -> {
            if (future.isDone()) return;
            try {
                WorldServer world = server.func_71218_a(0);
                if (!world.func_72912_H().func_76065_j().startsWith("pt112-")) throw new IllegalStateException("Not a bridge fixture world");
                EntityPlayerMP player = server.func_184103_al().func_177451_a(playerId);
                if (player == null) throw new IllegalStateException("Player left world");
                if (params.has("mode")) {
                    future.complete(AeInterfaceProbe.world(world, player, params));
                    return;
                }
                if (!params.has("open") || !params.get("open").getAsBoolean()) {
                    world.func_180501_a(position, AEApi.instance().definitions().blocks().chest().maybeBlock().get().func_176223_P(), 3);
                    world.func_180501_a(position.func_177982_a(1, 0, 0), AEApi.instance().definitions().blocks().energyCellCreative().maybeBlock().get().func_176223_P(), 3);
                    TileChest chest = (TileChest) world.func_175625_s(position);
                    boolean fluid = params.has("fluid") && params.get("fluid").getAsBoolean();
                    ItemStack cell = (fluid ? AEApi.instance().definitions().items().fluidCell64k() : AEApi.instance().definitions().items().cell64k()).maybeStack(1).get();
                    if (fluid) {
                        IFluidStorageChannel channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
                        ICellInventoryHandler<IAEFluidStack> inventory = AEApi.instance().registries().cell().getCellInventory(cell, null, channel);
                        for (String name : new String[]{"water", "lava"}) inventory.injectItems(channel.createStack(new FluidStack(FluidRegistry.getFluid(name), name.equals("water") ? 16000 : 8000)), Actionable.MODULATE, new BaseActionSource());
                    } else {
                        IItemStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
                        ICellInventoryHandler<IAEItemStack> inventory = AEApi.instance().registries().cell().getCellInventory(cell, null, channel);
                        int count = 0;
                        for (Item item : Item.field_150901_e) {
                            if (!item.getRegistryName().func_110624_b().equals("minecraft")) continue;
                            IAEItemStack stack = channel.createStack(new ItemStack(item));
                            if (stack == null) continue;
                            stack.setStackSize(64);
                            if (inventory.injectItems(stack, Actionable.MODULATE, new BaseActionSource()) == null) count++;
                            if (count >= 60) break;
                        }
                        IAEItemStack diamond = channel.createStack(new ItemStack(net.minecraft.init.Items.field_151045_i));
                        diamond.setStackSize(64);
                        if (inventory.injectItems(diamond, Actionable.MODULATE, new BaseActionSource()) != null) throw new IllegalStateException("Cell did not accept diamond");
                    }
                    ((IItemHandlerModifiable) field(chest, "cellInventory").get(chest)).setStackInSlot(0, cell);
                    player.field_71135_a.func_147364_a(0.5, 5, 2.5, 180, 30);
                }
                TileChest chest = (TileChest) world.func_175625_s(position);
                JsonObject result = new JsonObject();
                result.addProperty("powered", chest.isPowered());
                result.addProperty("cell", String.valueOf(chest.getCell().func_77973_b().getRegistryName()));
                if (params.has("open") && params.get("open").getAsBoolean()) result.addProperty("opened", chest.openGui(player));
                future.complete(result);
            } catch (Throwable error) { future.completeExceptionally(error); }
        });
    }

    public static JsonObject ui(JsonObject params) throws Exception {
        GuiScreen screen = Minecraft.func_71410_x().field_71462_r;
        if (screen == null || !screen.getClass().getName().startsWith("appeng.")) throw new IllegalStateException("Not an AE screen");
        GuiTextField search = (GuiTextField) field(screen, "searchField").get(screen);
        if (params.has("focused")) search.func_146195_b(params.get("focused").getAsBoolean());
        JsonObject result = new JsonObject();
        result.addProperty("class", screen.getClass().getName());
        result.addProperty("query", search.func_146179_b());
        result.addProperty("focused", search.func_146206_l());
        result.addProperty("cursor", search.func_146198_h());
        result.addProperty("selection", search.func_146186_n());
        Object repo = field(screen, "repo").get(screen);
        Object scrollbar = field(screen, "myScrollBar").get(screen);
        JsonObject scroll = new JsonObject();
        for (String name : new String[]{"currentScroll", "minScroll", "maxScroll"}) scroll.addProperty(name, field(scrollbar, name).getInt(scrollbar));
        result.add("scroll", scroll);
        JsonArray rows = new JsonArray();
        if (repo instanceof ItemRepo) {
            for (Object value : (Iterable<?>) field(repo, "view").get(repo)) {
                IAEItemStack stack = (IAEItemStack) value;
                JsonObject row = new JsonObject();
                row.addProperty("id", String.valueOf(stack.getItem().getRegistryName()));
                row.addProperty("name", Platform.getItemDisplayName(stack));
                row.addProperty("count", stack.getStackSize());
                rows.add(row);
            }
        } else if (repo instanceof FluidRepo) {
            for (Object value : (Iterable<?>) field(repo, "view").get(repo)) {
                IAEFluidStack stack = (IAEFluidStack) value;
                JsonObject row = new JsonObject();
                row.addProperty("id", stack.getFluidStack().getFluid().getName());
                row.addProperty("name", Platform.getFluidDisplayName(stack));
                row.addProperty("count", stack.getStackSize());
                rows.add(row);
            }
        } else throw new IllegalStateException("Unknown AE repository");
        result.add("rows", rows);
        return result;
    }

    private static Field field(Object owner, String name) throws NoSuchFieldException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
