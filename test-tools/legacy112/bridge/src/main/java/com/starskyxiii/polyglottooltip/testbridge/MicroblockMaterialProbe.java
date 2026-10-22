package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Bounded dependency diagnostics for supplied microblock materials. Does not run a catalog or tooltip. */
final class MicroblockMaterialProbe {
    private static final String PRODUCT = "com.starskyxiii.polyglottooltip.legacy.";
    private MicroblockMaterialProbe() { }

    static JsonObject inspect(JsonObject params) throws Exception {
        JsonArray inputs = params.getAsJsonArray("materials");
        if (inputs == null || inputs.size() < 1 || inputs.size() > 64)
            throw new IllegalArgumentException("Supply 1..64 material IDs");
        Object registry = NativeAccess.get(NativeAccess.type("codechicken.microblock.MicroMaterialRegistry$"), "MODULE$");
        Class<?> materialType = NativeAccess.type("codechicken.microblock.BlockMicroMaterial");
        JsonArray rows = new JsonArray();
        for (JsonElement input : inputs) {
            String id = input.getAsString();
            if (id.length() > 1024) throw new IllegalArgumentException("Material ID too long");
            JsonObject row = new JsonObject(); row.addProperty("material", id); rows.add(row);
            try {
                Object material = NativeAccess.call(registry, "getMaterial", id);
                if (material == null || !materialType.isInstance(material)) {
                    row.addProperty("stage", "unsupported-material"); continue;
                }
                row.addProperty("materialClass", material.getClass().getName());
                IBlockState state = (IBlockState) NativeAccess.call(material, "state");
                Block block = state.func_177230_c();
                ItemStack child = (ItemStack) NativeAccess.call(material, "getItem");
                Item item = child.func_77973_b();
                row.addProperty("block", String.valueOf(block.getRegistryName()));
                row.addProperty("blockClass", block.getClass().getName());
                row.addProperty("item", String.valueOf(item.getRegistryName()));
                row.addProperty("itemClass", item.getClass().getName());
                row.addProperty("metadata", child.func_77952_i());
                row.add("displayMethod", method(item.getClass().getMethod("func_77653_i", ItemStack.class)));
                row.add("rawMethod", method(item.getClass().getMethod("func_77667_c", ItemStack.class)));
                row.add("metadataMethod", method(block.getClass().getMethod("func_176201_c", IBlockState.class)));
                Object budget = NativeAccess.create(PRODUCT + "minecraft.NameEvaluationBudget");
                Object receipt = NativeAccess.call(NativeAccess.type(PRODUCT + "minecraft.ItemNameObservation"), "beginReceipt", child, budget);
                if (receipt == null) receipt = NativeAccess.call(NativeAccess.type(PRODUCT + "minecraft.ConsumedVanillaNameReceipt"), "begin", child, budget);
                boolean childSupported = receipt != null && (Boolean) NativeAccess.call(receipt, "capturing");
                row.addProperty("childCapturing", childSupported);
                if (receipt != null) row.addProperty("childReceipt", receipt.getClass().getSimpleName());
                if (childSupported) row.addProperty("childBindsDamage", (Boolean) NativeAccess.call(receipt, "bindsDamage"));
                try {
                    Object metadataBudget = NativeAccess.create(PRODUCT + "minecraft.NameEvaluationBudget");
                    Object facts = NativeAccess.create(PRODUCT + "minecraft.NameFacts", metadataBudget);
                    int actual = (Integer) NativeAccess.call(NativeAccess.type(PRODUCT + "minecraft.ConsumedBlockState"),
                            "metadata", block, state, facts, metadataBudget);
                    row.addProperty("metadataSupported", actual == child.func_77952_i());
                    row.addProperty("consumedMetadata", actual);
                } catch (Exception error) { row.addProperty("metadataSupported", false); }
                row.addProperty("stage", !childSupported ? "child-name"
                        : !row.get("childBindsDamage").getAsBoolean() || row.get("metadataSupported").getAsBoolean()
                        ? "capture-ready" : "state-metadata");
            } catch (Exception error) {
                row.addProperty("stage", "diagnostic-error"); row.addProperty("error", error.toString());
            }
        }
        JsonObject result = new JsonObject(); result.add("rows", rows); return result;
    }

    private static JsonObject method(Method method) throws Exception {
        JsonObject result = new JsonObject(); result.addProperty("owner", method.getDeclaringClass().getName());
        result.addProperty("name", method.getName());
        result.addProperty("admitted", (Boolean) NativeAccess.call(NativeAccess.type(PRODUCT + "bootstrap.NameTransformObserver"), "admits", method));
        return result;
    }

    static JsonObject woodGuards(JsonObject params) throws Exception {
        if (!Boolean.getBoolean("clientdevbridge.nameAudit")) throw new IllegalStateException("Name audit required");
        ItemStack stack = (ItemStack) NativeAccess.call(NameAuditProbe.class, "decode", params.getAsJsonObject("sample"));
        if (!String.valueOf(stack.func_77973_b().getRegistryName()).equals("microblockcbe:microblock") || stack.func_77978_p() == null)
            throw new IllegalArgumentException("A microblock sample is required");
        Object registry = NativeAccess.get(NativeAccess.type("codechicken.microblock.MicroMaterialRegistry$"), "MODULE$");
        Object material = NativeAccess.call(registry, "getMaterial", stack.func_77978_p().func_74779_i("mat"));
        Block block = ((IBlockState) NativeAccess.call(material, "state")).func_177230_c();
        String name = block.getClass().getName();
        if (!(Boolean) NativeAccess.call(NativeAccess.type(PRODUCT + "minecraft.ConsumedWoodVariant"), "handles", name))
            throw new IllegalArgumentException("A reviewed wood family is required");
        Class<?> base = block.getClass().getSuperclass().getSuperclass();
        String before = stack.func_77955_b(new net.minecraft.nbt.NBTTagCompound()).toString();
        JsonObject result = new JsonObject(); JsonArray checks = new JsonArray(); result.add("checks", checks);
        for (String fieldName : new String[]{"fireproof", "blockNumber"}) {
            Object observation = capture(stack);
            Field field = base.getDeclaredField(fieldName); field.setAccessible(true); Object original = field.get(block);
            JsonObject check = new JsonObject(); check.addProperty("field", fieldName); checks.add(check);
            try {
                field.set(block, fieldName.equals("fireproof") ? !(Boolean) original : (Integer) original + 1);
                check.addProperty("changedCurrent", (Boolean) NativeAccess.call(observation, "current", stack));
            } finally { field.set(block, original); }
            check.addProperty("restoredCurrent", (Boolean) NativeAccess.call(observation, "current", stack));
            Object fresh = capture(stack); check.addProperty("freshCurrent", (Boolean) NativeAccess.call(fresh, "current", stack));
            check.addProperty("fieldRestored", original.equals(field.get(block)));
        }
        result.addProperty("stackUnchanged", before.equals(stack.func_77955_b(new net.minecraft.nbt.NBTTagCompound()).toString()));
        return result;
    }

    private static Object capture(ItemStack stack) throws Exception {
        NameAuditCapture.Frame frame = NameAuditCapture.begin(stack);
        try { stack.func_82840_a(Minecraft.func_71410_x().field_71439_g, ITooltipFlag.TooltipFlags.NORMAL); }
        finally { NameAuditCapture.end(); }
        if (frame.failed || frame.productObservation == null || !(Boolean) NativeAccess.call(frame.productObservation, "current", stack))
            throw new IllegalStateException("A current wood observation is required");
        return frame.productObservation;
    }
}
