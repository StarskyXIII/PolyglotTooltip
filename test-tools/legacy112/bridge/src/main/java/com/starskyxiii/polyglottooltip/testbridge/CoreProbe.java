package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemPotion;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.lang.reflect.Method;

public final class CoreProbe {
    private static final Gson gson = new Gson();
    private static Fluid composedFluid;
    private static ItemPotion composedPotion;
    private CoreProbe() { }

    public static void registerFixtures(net.minecraftforge.registries.IForgeRegistry<Item> registry) {
        composedFluid = new Fluid("pt112-probe", new ResourceLocation("blocks/water_still"),
                new ResourceLocation("blocks/water_flow")) {
            @Override public String getUnlocalizedName(FluidStack stack) { return "fluid.water"; }
            @Override public String getLocalizedName(FluidStack stack) { return I18n.func_135052_a("fluid.water") + " 42"; }
        };
        net.minecraftforge.fluids.FluidRegistry.registerFluid(composedFluid);
        composedPotion = new ItemPotion() {
            @Override public String func_77653_i(ItemStack stack) { return "Custom potion 42"; }
        };
        registry.register(composedPotion.setRegistryName("polyglotdevbridge", "composed_potion"));
        registry.register(new EmiProviderProbe());
    }
}
