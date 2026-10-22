package com.starskyxiii.polyglottooltip.testbridge;

import appeng.api.AEApi;
import appeng.api.definitions.IItemDefinition;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEColor;
import appeng.api.util.AEPartLocation;
import appeng.tile.misc.TileInterface;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.lang.reflect.Field;
import java.util.Map;

public final class AeInterfaceProbe {
    private static final BlockPos terminal = new BlockPos(4, 4, 0);
    private static final BlockPos machine = new BlockPos(3, 4, 0);
    private AeInterfaceProbe() { }

    public static JsonObject world(WorldServer world, EntityPlayerMP player, JsonObject params) throws Exception {
        String mode = params.get("mode").getAsString();
        if (!mode.equals("interface") && !mode.equals("itemConfig") && !mode.equals("fluidConfig")) throw new IllegalArgumentException("Invalid interface mode");
        boolean fluid = mode.equals("fluidConfig");
        if (params.has("rename")) {
            String label = params.get("rename").getAsString();
            if (label.length() > 100) throw new IllegalArgumentException("Label too long");
            ((appeng.tile.AEBaseTile) world.func_175625_s(machine)).setName(label);
        } else if (!params.has("open") || !params.get("open").getAsBoolean()) {
            world.func_175698_g(terminal);
            world.func_175698_g(machine);
            world.func_180501_a(terminal, AEApi.instance().definitions().blocks().multiPart().maybeBlock().get().func_176223_P(), 3);
            world.func_180501_a(terminal.func_177982_a(1, 0, 0), AEApi.instance().definitions().blocks().energyCellCreative().maybeBlock().get().func_176223_P(), 3);
            world.func_180501_a(machine, (fluid ? AEApi.instance().definitions().blocks().fluidIface() : AEApi.instance().definitions().blocks().iface()).maybeBlock().get().func_176223_P(), 3);
            world.func_180501_a(machine.func_177982_a(-1, 0, 0), Block.field_149771_c.func_82594_a(new ResourceLocation("minecraft:furnace")).func_176223_P(), 3);
            Object parts = AEApi.instance().definitions().parts();
            String definition = mode.equals("interface") ? "interfaceTerminal" : mode.equals("itemConfig") ? "interfaceConfigurationTerminal" : "fluidInterfaceConfigurationTerminal";
            ItemStack part = ((IItemDefinition) parts.getClass().getMethod(definition).invoke(parts)).maybeStack(1).get();
            IPartHost host = (IPartHost) world.func_175625_s(terminal);
            if (host.addPart(AEApi.instance().definitions().parts().cableGlass().stack(AEColor.TRANSPARENT, 1), AEPartLocation.INTERNAL, player, EnumHand.MAIN_HAND) == null
                    || host.addPart(part, AEPartLocation.SOUTH, player, EnumHand.MAIN_HAND) == null) throw new IllegalStateException("Could not place terminal parts");
            if (fluid) {
                Object tile = world.func_175625_s(machine);
                Object duality = tile.getClass().getMethod("getDualityFluidInterface").invoke(tile);
                Object config = duality.getClass().getMethod("getConfig").invoke(duality);
                config.getClass().getMethod("setFluidInSlot", int.class, appeng.api.storage.data.IAEFluidStack.class).invoke(config, 0,
                        AEApi.instance().storage().getStorageChannel(appeng.api.storage.channels.IFluidStorageChannel.class).createStack(new FluidStack(FluidRegistry.WATER, 1000)));
            } else {
                TileInterface tile = (TileInterface) world.func_175625_s(machine);
                tile.setSide(EnumFacing.EAST);
                ItemStack pattern = AEApi.instance().definitions().items().encodedPattern().maybeStack(1).get();
                pattern.func_77982_d(JsonToNBT.func_180713_a("{crafting:0b,in:[{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}],out:[{id:\"minecraft:diamond\",Count:1b,Damage:0s}]}"));
                ((IItemHandlerModifiable) tile.getInterfaceDuality().getPatterns()).setStackInSlot(0, pattern);
                ((IItemHandlerModifiable) tile.getInterfaceDuality().getConfig()).setStackInSlot(0, new ItemStack(Items.field_151045_i));
            }
            player.field_71135_a.func_147364_a(4.5, 5, 2.5, 180, 30);
        }
        JsonObject result = new JsonObject();
        result.addProperty("mode", mode);
        if (params.has("open") && params.get("open").getAsBoolean()) {
            IPartHost host = (IPartHost) world.func_175625_s(terminal);
            result.addProperty("opened", host.getPart(AEPartLocation.SOUTH).onActivate(player, EnumHand.MAIN_HAND, new Vec3d(0.5, 0.5, 1)));
        }
        return result;
    }

    public static JsonObject observe(JsonObject params) throws Exception {
        GuiScreen screen = Minecraft.func_71410_x().field_71462_r;
        if (screen == null || !screen.getClass().getName().startsWith("appeng.")) throw new IllegalStateException("Not an AE screen");
        JsonObject queries = new JsonObject();
        for (String name : new String[]{"searchField", "searchFieldInputs", "searchFieldOutputs", "searchFieldNames"}) {
            try {
                Object input = field(screen, name).get(screen);
                if (params.has("focus")) {
                    boolean focused = params.get("focus").getAsString().equals(name);
                    if (input instanceof GuiTextField) ((GuiTextField) input).func_146195_b(focused);
                    else input.getClass().getMethod("setFocused", boolean.class).invoke(input, focused);
                }
                queries.addProperty(name, input instanceof GuiTextField ? ((GuiTextField) input).func_146179_b()
                        : (String) input.getClass().getMethod("getText").invoke(input));
            } catch (NoSuchFieldException ignored) { }
        }
        JsonArray rows = new JsonArray();
        for (Object line : (Iterable<?>) field(screen, "lines").get(screen)) {
            JsonObject row = new JsonObject();
            row.addProperty("class", line.getClass().getName());
            if (line instanceof String) row.addProperty("label", (String) line);
            else {
                row.addProperty("label", (String) line.getClass().getMethod("getName").invoke(line));
                row.addProperty("raw", (String) field(line, "unlocalizedName").get(line));
                row.addProperty("id", field(line, "id").getLong(line));
            }
            rows.add(row);
        }
        JsonObject result = new JsonObject();
        result.addProperty("class", screen.getClass().getName());
        result.add("queries", queries);
        result.add("rows", rows);
        result.addProperty("available", ((Map<?, ?>) field(screen, "byId").get(screen)).size());
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
