package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Builds only the requested tool or stack; does not enumerate creative items or materials. */
final class TinkerProbe {
    private static final Gson GSON = new Gson();
    private static final String PRODUCT = "com.starskyxiii.polyglottooltip.legacy.";
    private TinkerProbe() { }

    static JsonObject inspect(JsonObject params) throws Exception {
        JsonObject result = new JsonObject();
        Class<?> resolver = NativeAccess.type(PRODUCT + "minecraft.SecondaryNameResolver");
        if (params.has("fluid")) {
            Fluid fluid = FluidRegistry.getFluid(params.get("fluid").getAsString());
            if (fluid == null) throw new IllegalArgumentException("Unknown fluid");
            FluidStack stack = new FluidStack(fluid, 1000);
            result.addProperty("class", fluid.getClass().getName());
            result.addProperty("nativeName", stack.getLocalizedName());
            result.add("aliases", GSON.toJsonTree(NativeAccess.call(resolver, "resolveFluidNames", stack)));
        } else {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(params.get("item").getAsString()));
            if (item == null) throw new IllegalArgumentException("Unknown item");
            ItemStack stack;
            if (params.has("materials")) {
                if (params.getAsJsonArray("materials").size() > 8) throw new IllegalArgumentException("Too many materials");
                List<Object> materials = new ArrayList<>();
                Class<?> registry = NativeAccess.type("slimeknights.tconstruct.library.TinkerRegistry");
                for (JsonElement value : params.getAsJsonArray("materials"))
                    materials.add(NativeAccess.call(registry, "getMaterial", value.getAsString()));
                stack = (ItemStack) NativeAccess.call(item, "buildItem", materials);
            } else {
                stack = new ItemStack(item, 1, params.has("metadata") ? params.get("metadata").getAsInt() : 0);
            }
            if (params.has("nbt")) stack.func_77982_d(JsonToNBT.func_180713_a(params.get("nbt").getAsString()));
            if (params.has("name")) stack.func_151001_c(params.get("name").getAsString());
            NBTTagCompound before = stack.func_77955_b(new NBTTagCompound());
            ItemStack nativeStack = stack.func_77946_l();
            Object nativeTooltip = NativeAccess.call(NativeAccess.type(PRODUCT + "minecraft.TooltipSuppressionScope"),
                    "withoutSecondary", (java.util.function.Supplier<List<String>>) () -> nativeStack.func_82840_a(
                            Minecraft.func_71410_x().field_71439_g, ITooltipFlag.TooltipFlags.NORMAL));
            NBTTagCompound nativeAfter = nativeStack.func_77955_b(new NBTTagCompound());
            NameAuditCapture.Frame frame = NameAuditCapture.begin(stack, true);
            List<String> tooltip;
            try { tooltip = stack.func_82840_a(Minecraft.func_71410_x().field_71439_g, ITooltipFlag.TooltipFlags.NORMAL); }
            finally { NameAuditCapture.end(); }
            result.addProperty("class", item.getClass().getName());
            result.addProperty("nativeName", stack.func_82833_r());
            result.add("tooltip", GSON.toJsonTree(tooltip));
            result.add("nativeBody", GSON.toJsonTree(frame.nativeBodyBeforeAppend));
            result.add("aliases", GSON.toJsonTree(NativeAccess.call(resolver, "resolveItemNames", stack)));
            result.addProperty("unchanged", before.equals(stack.func_77955_b(new NBTTagCompound())));
            result.addProperty("nativeUnchanged", before.equals(nativeAfter));
            result.addProperty("sameStateAsNative", nativeAfter.equals(stack.func_77955_b(new NBTTagCompound())));
            result.add("nativeTooltipWithoutSecondary", GSON.toJsonTree(nativeTooltip));
            result.addProperty("nativeNbtAfter", nativeAfter.toString());
            result.addProperty("nbtAfter", stack.func_77955_b(new NBTTagCompound()).toString());
            result.addProperty("nbt", before.toString());
        }
        Class<?> observer = NativeAccess.type(PRODUCT + "bootstrap.NameTransformObserver");
        Properties profiles = (Properties) NativeAccess.get(observer, "profiles");
        JsonObject admissions = new JsonObject();
        for (String key : profiles.stringPropertyNames()) {
            if (!(key.startsWith("slimeknights.tconstruct.") || key.startsWith("landmaster.plustic.")
                    || key.startsWith("com.teamacronymcoders.contenttweaker.modules.tinkers.materials.")
                    || key.startsWith("appeng.") || key.startsWith("zmaster587.libVulpes.")
                    || key.startsWith("com.blakebr0."))) continue;
            int separator = key.indexOf('#');
            admissions.addProperty(key, (Boolean) NativeAccess.call(observer, "admits", key.substring(0, separator), key.substring(separator + 1)));
        }
        result.add("methodAdmissions", admissions);
        return result;
    }
}
