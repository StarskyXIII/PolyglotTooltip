package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.objectweb.asm.Type;

/** One requested stack and enchantment; records the actual admission and surviving row evidence. */
final class EnchantmentProbe {
    private static final String PRODUCT = "com.starskyxiii.polyglottooltip.legacy.";
    private static final Gson GSON = new Gson();
    private EnchantmentProbe() { }

    static JsonObject inspect(JsonObject params) throws Exception {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(params.get("item").getAsString()));
        Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(params.get("enchantment").getAsString()));
        if (item == null || enchantment == null) throw new IllegalArgumentException("Unknown item or enchantment");
        ItemStack stack = new ItemStack(item);
        if (params.has("nbt")) stack.func_77982_d(JsonToNBT.func_180713_a(params.get("nbt").getAsString()));
        if (params.has("level")) {
            int level = params.get("level").getAsInt();
            if (params.has("nbt") || level < 1 || level > Short.MAX_VALUE) throw new IllegalArgumentException("Invalid level or competing NBT");
            NBTTagCompound entry = new NBTTagCompound();
            entry.func_74777_a("id", (short) Enchantment.func_185258_b(enchantment));
            entry.func_74777_a("lvl", (short) level);
            NBTTagList entries = new NBTTagList();
            entries.func_74742_a(entry);
            NBTTagCompound tag = new NBTTagCompound();
            tag.func_74782_a(item instanceof ItemEnchantedBook ? "StoredEnchantments" : "ench", entries);
            stack.func_77982_d(tag);
        }
        NBTTagCompound before = stack.func_77955_b(new NBTTagCompound());
        ItemStack nativeStack = stack.func_77946_l();
        Object nativeTooltip = NativeAccess.call(NativeAccess.type(PRODUCT + "minecraft.TooltipSuppressionScope"),
                "withoutSecondary", (java.util.function.Supplier<List<String>>) () -> nativeStack.func_82840_a(
                        Minecraft.func_71410_x().field_71439_g, ITooltipFlag.TooltipFlags.NORMAL));
        NameAuditCapture.Frame frame = NameAuditCapture.begin(stack, true);
        List<String> tooltip;
        try { tooltip = stack.func_82840_a(Minecraft.func_71410_x().field_71439_g, ITooltipFlag.TooltipFlags.NORMAL); }
        finally { NameAuditCapture.end(); }
        JsonObject result = new JsonObject();
        result.addProperty("nbt", before.toString());
        result.addProperty("nativeUnchanged", before.equals(nativeStack.func_77955_b(new NBTTagCompound())));
        result.add("nativeTooltipWithoutSecondary", GSON.toJsonTree(nativeTooltip));
        result.add("tooltip", GSON.toJsonTree(tooltip));
        result.add("nativeBody", GSON.toJsonTree(frame.nativeBodyBeforeAppend));
        result.add("rowEvents", GSON.toJsonTree(frame.rowEvents));
        result.addProperty("captureFailed", frame.failed);
        result.addProperty("unchanged", before.equals(stack.func_77955_b(new NBTTagCompound())));
        result.addProperty("rowClass", tooltip.getClass().getName());
        if (tooltip.getClass().getName().equals(PRODUCT + "ObservedTooltipRows")) {
            Object context = NativeAccess.call(tooltip, "owner");
            result.add("outcomes", GSON.toJsonTree(NativeAccess.get(context, "outcomes")));
            JsonArray plans = new JsonArray();
            Object primary = NativeAccess.get(NativeAccess.get(context, "languages"), "primaryTable");
            for (Object entry : (List<?>) NativeAccess.call(tooltip, "surviving")) {
                Object plan = NativeAccess.get(NativeAccess.get(entry, "value"), "plan");
                JsonObject row = new JsonObject();
                row.addProperty("nativeRow", (String) NativeAccess.get(entry, "row"));
                row.addProperty("primaryRender", (String) NativeAccess.call(plan, "render", primary));
                String key = (String) NativeAccess.get(plan, "levelKey");
                if (key != null) {
                    row.addProperty("levelKey", key);
                    row.addProperty("primaryLevel", (String) NativeAccess.call(primary, "get", key));
                    row.addProperty("nativeLevel", net.minecraft.util.text.translation.I18n.func_74838_a(key));
                }
                plans.add(row);
            }
            result.add("unannotatedPlans", plans);
        }
        Class<?> patch = NativeAccess.type(PRODUCT + "bootstrap.EnchantmentCapturePatch");
        result.addProperty("mode", (Number) NativeAccess.call(patch, "mode"));
        result.addProperty("stripsLevelKey", (Boolean) NativeAccess.call(patch, "stripsLevelKey"));
        result.addProperty("tooltipCaptureSupported", (Boolean) NativeAccess.call(NativeAccess.type(PRODUCT + "bootstrap.TooltipCaptureAdmission"), "supported"));
        Class<?> observer = NativeAccess.type(PRODUCT + "bootstrap.NameTransformObserver");
        Map<?, ?> effective = (Map<?, ?>) NativeAccess.call(observer, "diagnostics");
        Map<?, ?> patched = (Map<?, ?>) NativeAccess.get(observer, "patched");
        JsonObject methods = new JsonObject();
        for (Map.Entry<?, ?> entry : effective.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!(key.startsWith("net.minecraft.enchantment.Enchantment#") || key.startsWith("net.minecraft.item.ItemEnchantedBook#")
                    || key.startsWith("net.minecraft.item.ItemStack#func_82840_a") || key.startsWith("observer@"))) continue;
            JsonObject row = new JsonObject();
            row.addProperty("digest", String.valueOf(entry.getValue()));
            int separator = key.indexOf('#');
            if (separator > 0) {
                row.addProperty("admitted", (Boolean) NativeAccess.call(observer, "admits", key.substring(0, separator), key.substring(separator + 1)));
                row.addProperty("patched", patched.containsKey(key));
            }
            methods.add(key, row);
        }
        result.add("effectiveMethods", methods);
        JsonArray providers = new JsonArray();
        for (String name : new String[]{"func_77316_c", "func_77320_a", "func_77325_b", "func_190936_d"}) {
            Method method = name.equals("func_77316_c") ? enchantment.getClass().getMethod(name, int.class) : enchantment.getClass().getMethod(name);
            JsonObject row = new JsonObject();
            row.addProperty("method", method.getDeclaringClass().getName() + "#" + method.getName() + Type.getMethodDescriptor(method));
            row.addProperty("admitted", (Boolean) NativeAccess.call(observer, "admits", method));
            providers.add(row);
        }
        result.add("providerMethods", providers);
        return result;
    }
}
