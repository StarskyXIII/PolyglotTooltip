package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Focused export acceptance only: at most 16 supplied stacks or one exact catalog ID, limit <= 64. */
final class NameExportProbe {
    private static final String PRODUCT = "com.starskyxiii.polyglottooltip.legacy.";
    private NameExportProbe() { }

    static JsonObject inspect(JsonObject params) throws Exception {
        Class<?> manager = NativeAccess.type(PRODUCT + "export.NameExportManager");
        String action = params.get("action").getAsString();
        if (action.equals("microblockMaterials")) return MicroblockMaterialProbe.inspect(params);
        if (action.equals("microblockWoodGuards")) return MicroblockMaterialProbe.woodGuards(params);
        if (action.equals("status")) return (JsonObject) NativeAccess.call(manager, "status");
        if (action.equals("pause") || action.equals("resume") || action.equals("cancel")) {
            NativeAccess.call(manager, "action", action);
        } else if (action.equals("catalog")) {
            String source = params.get("source").getAsString(), item = params.get("item").getAsString();
            if (!source.matches("defaults|creative|jei") || !item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("One exact item ID required");
            int limit = limit(params);
            int command = ClientCommandHandler.instance.func_71556_a(Minecraft.func_71410_x().field_71439_g,
                    "/polyglotdump start " + source + " " + item
                            + (params.has("omitLimit") && params.get("omitLimit").getAsBoolean() ? "" : " " + limit)
                            + (params.has("mode") ? " " + mode(params) : ""));
            JsonObject result = (JsonObject) NativeAccess.call(manager, "status");
            result.addProperty("commandResult", command); return result;
        } else if (action.equals("start") || action.equals("native")) {
            JsonArray samples = params.getAsJsonArray("samples");
            if (samples == null || samples.size() < 1 || samples.size() > 16) throw new IllegalArgumentException("Supply 1..16 stacks");
            List<ItemStack> stacks = new ArrayList<>();
            for (JsonElement value : samples) stacks.add(stack(value.getAsJsonObject()));
            if (action.equals("native")) {
                JsonObject result = new JsonObject(); JsonArray rows = new JsonArray();
                for (ItemStack stack : stacks) {
                    JsonObject row = new JsonObject();
                    row.addProperty("before", stack.func_77955_b(new NBTTagCompound()).toString());
                    row.addProperty("name", stack.func_82833_r());
                    row.addProperty("after", stack.func_77955_b(new NBTTagCompound()).toString()); rows.add(row);
                }
                result.add("rows", rows); return result;
            }
            NativeAccess.call(manager, "startSamples", stacks, limit(params), mode(params));
            if (params.has("paused") && params.get("paused").getAsBoolean()) NativeAccess.call(manager, "action", "pause");
        } else throw new IllegalArgumentException("Unknown export probe action");
        return (JsonObject) NativeAccess.call(manager, "status");
    }

    private static int limit(JsonObject params) {
        int value = params.has("limit") ? params.get("limit").getAsInt() : 64;
        if (value < 1 || value > 64) throw new IllegalArgumentException("Focused export limit must be 1..64");
        return value;
    }

    private static String mode(JsonObject params) {
        String value = params.has("mode") ? params.get("mode").getAsString() : "interactive";
        if (!value.equals("interactive") && !value.equals("batch")) throw new IllegalArgumentException("Unknown export mode");
        return value;
    }

    private static ItemStack stack(JsonObject params) throws Exception {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(params.get("item").getAsString()));
        if (item == null) throw new IllegalArgumentException("Unknown item");
        ItemStack stack;
        if (params.has("materials")) {
            JsonArray values = params.getAsJsonArray("materials");
            if (values.size() > 8) throw new IllegalArgumentException("Too many materials");
            List<Object> materials = new ArrayList<>();
            Class<?> registry = NativeAccess.type("slimeknights.tconstruct.library.TinkerRegistry");
            for (JsonElement value : values) materials.add(NativeAccess.call(registry, "getMaterial", value.getAsString()));
            stack = (ItemStack) NativeAccess.call(item, "buildItem", materials);
        } else stack = new ItemStack(item, 1, params.has("metadata") ? params.get("metadata").getAsInt() : 0);
        if (params.has("nbt")) stack.func_77982_d(JsonToNBT.func_180713_a(params.get("nbt").getAsString()));
        if (params.has("name")) stack.func_151001_c(params.get("name").getAsString());
        if (params.has("count")) stack.func_190920_e(params.get("count").getAsInt());
        if (params.has("invalidNbt")) {
            if (!params.get("invalidNbt").getAsString().equals("cycle")) throw new IllegalArgumentException("Unsupported negative NBT fixture");
            NBTTagCompound tag = new NBTTagCompound(); tag.func_74782_a("self", tag); stack.func_77982_d(tag);
        }
        return stack;
    }
}
