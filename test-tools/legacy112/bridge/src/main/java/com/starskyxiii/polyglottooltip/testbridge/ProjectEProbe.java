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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ProjectEProbe {
    private static final BlockPos position = new BlockPos(70, 4, 0);
    private ProjectEProbe() { }
    private static Object provider(EntityPlayer player) throws Exception {
        Capability<?> capability = (Capability<?>) NativeAccess.get(NativeAccess.type("moze_intel.projecte.api.ProjectEAPI"), "KNOWLEDGE_CAPABILITY");
        Object value = player.getCapability(capability, null); if (value == null) throw new IllegalStateException("Native knowledge capability missing"); return value;
    }
    public static void world(JsonObject params, CompletableFuture<JsonElement> future) throws Exception {
        Minecraft client = Minecraft.func_71410_x(); MinecraftServer server = client.func_71401_C();
        if (server == null || client.field_71439_g == null) throw new IllegalStateException("No integrated player");
        boolean prepare = params.has("prepare") && params.get("prepare").getAsBoolean();
        if (prepare && params.has("open") && params.get("open").getAsBoolean()) throw new IllegalArgumentException("Prepare and open require separate knowledge sync steps");
        if (!server.func_71218_a(0).func_72912_H().func_76065_j().startsWith("pt112-")) throw new IllegalStateException("Not an owned ProjectE world");
        if (prepare) { client.func_147108_a(null); NativeAccess.call(provider(client.field_71439_g), "clearKnowledge"); }
        UUID id = client.field_71439_g.func_110124_au();
        net.minecraft.world.World initialWorld = client.field_71441_e; EntityPlayer initialPlayer = client.field_71439_g;
        server.func_152344_a(() -> {
            if (future.isDone()) return;
            try {
                if (client.func_71401_C() != server || client.field_71441_e != initialWorld || client.field_71439_g != initialPlayer) throw new IllegalStateException("ProjectE fixture context changed");
                EntityPlayerMP player = server.func_184103_al().func_177451_a(id); WorldServer world = server.func_71218_a(0);
                if (player == null || player.field_70170_p != world || !world.func_72912_H().func_76065_j().startsWith("pt112-")) throw new IllegalStateException("Not an owned ProjectE world");
                Object provider = provider(player); JsonObject result = new JsonObject();
                if (prepare) {
                    NativeAccess.call(provider, "clearKnowledge"); NativeAccess.call(provider, "setFullKnowledge", false);
                    NativeAccess.call(provider, "setEmc", params.has("emc") ? params.get("emc").getAsLong() : 1000000000L);
                    IItemHandlerModifiable inputs = (IItemHandlerModifiable) NativeAccess.call(provider, "getInputAndLocks");
                    for (int index = 0; index < inputs.getSlots(); index++) inputs.setStackInSlot(index, ItemStack.field_190927_a);
                    Class<?> emc = NativeAccess.type("moze_intel.projecte.utils.EMCHelper"); int added = 0;
                    for (String key : new String[]{"stone", "dirt", "cobblestone", "planks", "sand", "gravel", "gold_ore", "iron_ore", "coal_ore", "log", "sponge", "glass", "lapis_ore", "lapis_block", "sandstone", "wool", "gold_block", "iron_block", "brick_block", "bookshelf", "mossy_cobblestone", "obsidian", "diamond_ore", "diamond_block", "redstone_ore", "ice", "snow", "clay", "pumpkin", "netherrack", "soul_sand", "glowstone", "melon_block", "stonebrick", "emerald_ore", "emerald_block", "quartz_ore", "quartz_block", "coal", "diamond", "iron_ingot", "gold_ingot", "stick", "redstone", "emerald", "quartz", "dye"}) {
                        Item item = Item.field_150901_e.func_82594_a(new ResourceLocation("minecraft", key));
                        if (item == null) throw new IllegalStateException("Unknown fixture item " + key);
                        int variants = key.equals("wool") || key.equals("dye") ? 16 : key.equals("stone") ? 7 : key.equals("planks") ? 6 : 1;
                        for (int meta = 0; meta < variants; meta++) {
                            ItemStack stack = new ItemStack(item, 1, meta);
                            if ((Boolean) NativeAccess.call(emc, "doesItemHaveEmc", stack) && ((Number) NativeAccess.call(emc, "getEmcValue", stack)).longValue() > 0
                                    && (Boolean) NativeAccess.call(provider, "addKnowledge", stack)) added++;
                        }
                    }
                    if (added < 25) throw new IllegalStateException("Insufficient native EMC candidates " + added);
                    if (params.has("lock")) inputs.setStackInSlot(8, new ItemStack(Item.field_150901_e.func_82594_a(new ResourceLocation(params.get("lock").getAsString()))));
                    NativeAccess.call(provider, "sync", player);
                    Block block = (Block) NativeAccess.get(NativeAccess.type("moze_intel.projecte.gameObjs.ObjHandler"), "transmuteStone");
                    world.func_180501_a(position, block.func_176223_P(), 3); player.field_71135_a.func_147364_a(70.5, 4, 2.5, 180, 20);
                    result.addProperty("added", added);
                }
                if (params.has("open") && params.get("open").getAsBoolean()) result.addProperty("opened", world.func_180495_p(position).func_177230_c().func_180639_a(world, position, world.func_180495_p(position), player, EnumHand.MAIN_HAND, EnumFacing.SOUTH, 0.5f, 0.5f, 1f));
                result.add("provider", describeProvider(provider)); future.complete(result);
            } catch (Throwable error) { future.completeExceptionally(error); }
        });
    }
    public static JsonObject ui(JsonObject params) throws Exception {
        GuiScreen screen = Minecraft.func_71410_x().field_71462_r;
        if (screen == null || !screen.getClass().getName().equals("moze_intel.projecte.gameObjs.gui.GUITransmutation")) throw new IllegalStateException("No native transmutation GUI");
        GuiTextField field = (GuiTextField) NativeAccess.get(screen, "textBoxFilter");
        if (params.has("query")) {
            field.func_146195_b(true); field.func_146190_e(0); field.func_146199_i(field.func_146179_b().length());
            NativeAccess.call(screen, "func_73869_a", '\0', 14);
            for (char c : params.get("query").getAsString().toCharArray()) NativeAccess.call(screen, "func_73869_a", c, 0);
        }
        if (params.has("next")) {
            for (Object button : (List<?>) NativeAccess.get(screen, "field_146292_n")) if ((Integer) NativeAccess.get(button, "field_146127_k") == (params.get("next").getAsBoolean() ? 2 : 1)) NativeAccess.call(screen, "func_146284_a", button);
        }
        JsonObject result = observe();
        if (params.has("predicate")) {
            ItemStack stack = new ItemStack(Item.field_150901_e.func_82594_a(new ResourceLocation(params.get("predicate").getAsString())));
            if (params.has("custom")) stack.func_151001_c(params.get("custom").getAsString());
            result.addProperty("predicate", (Boolean) NativeAccess.call(NativeAccess.get(screen, "inv"), "doesItemMatchFilter", stack));
        }
        return result;
    }
    public static JsonObject observe() throws Exception {
        Minecraft client = Minecraft.func_71410_x(); JsonObject result = new JsonObject();
        if (client.field_71439_g == null) return result;
        result.add("provider", describeProvider(provider(client.field_71439_g)));
        GuiScreen screen = client.field_71462_r;
        if (screen == null || !screen.getClass().getName().equals("moze_intel.projecte.gameObjs.gui.GUITransmutation")) return result;
        Object inventory = NativeAccess.get(screen, "inv");
        result.addProperty("screen", System.identityHashCode(screen)); result.addProperty("inventory", System.identityHashCode(inventory));
        result.addProperty("query", ((GuiTextField) NativeAccess.get(screen, "textBoxFilter")).func_146179_b());
        result.addProperty("filter", (String) NativeAccess.get(inventory, "filter")); result.addProperty("page", (Integer) NativeAccess.get(inventory, "searchpage"));
        result.addProperty("available", (Number) NativeAccess.call(inventory, "getAvailableEMC"));
        JsonArray knowledge = new JsonArray(); for (Object value : (List<?>) NativeAccess.get(inventory, "knowledge")) knowledge.add(stack((ItemStack) value)); result.add("knowledge", knowledge);
        result.add("outputs", handler((IItemHandler) NativeAccess.get(inventory, "outputs"))); return result;
    }
    private static JsonObject describeProvider(Object provider) throws Exception {
        JsonObject result = new JsonObject(); result.addProperty("identity", System.identityHashCode(provider));
        result.addProperty("full", (Boolean) NativeAccess.call(provider, "hasFullKnowledge")); result.addProperty("emc", (Number) NativeAccess.call(provider, "getEmc"));
        JsonArray knowledge = new JsonArray(); for (Object value : (List<?>) NativeAccess.call(provider, "getKnowledge")) knowledge.add(stack((ItemStack) value)); result.add("knowledge", knowledge);
        result.add("inputs", handler((IItemHandler) NativeAccess.call(provider, "getInputAndLocks"))); return result;
    }
    private static JsonArray handler(IItemHandler handler) throws Exception { JsonArray rows = new JsonArray(); for (int index = 0; index < handler.getSlots(); index++) rows.add(stack(handler.getStackInSlot(index))); return rows; }
    private static JsonObject stack(ItemStack stack) throws Exception {
        JsonObject row = new JsonObject(); row.addProperty("empty", stack.func_190926_b());
        if (!stack.func_190926_b()) { row.addProperty("key", stack.func_77973_b().getRegistryName() + ":" + stack.func_77960_j()); row.addProperty("name", stack.func_82833_r()); row.addProperty("count", stack.func_190916_E()); row.addProperty("fuel", (Boolean) NativeAccess.call(NativeAccess.type("moze_intel.projecte.emc.FuelMapper"), "isStackFuel", stack)); }
        return row;
    }
}
