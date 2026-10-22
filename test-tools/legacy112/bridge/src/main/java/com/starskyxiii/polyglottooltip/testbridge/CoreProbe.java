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

    public static JsonObject inspect(JsonObject params) throws Exception {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(params.get("item").getAsString()));
        if (item == null) throw new IllegalArgumentException("Unknown item");
        ItemStack stack = new ItemStack(item, params.has("count") ? params.get("count").getAsInt() : 1,
                params.has("metadata") ? params.get("metadata").getAsInt() : 0);
        if (params.has("nbt")) stack.func_77982_d(JsonToNBT.func_180713_a(params.get("nbt").getAsString()));
        JsonObject result = new JsonObject();
        result.addProperty("item", item.getRegistryName().toString());
        result.addProperty("class", item.getClass().getName());
        result.addProperty("metadata", stack.func_77960_j());
        result.addProperty("nativeName", stack.func_82833_r());
        List<String> tooltip = stack.func_82840_a(Minecraft.func_71410_x().field_71439_g, ITooltipFlag.TooltipFlags.NORMAL);
        result.add("tooltip", gson.toJsonTree(tooltip));
        result.addProperty("product", Loader.isModLoaded("polyglottooltip"));
        if (Loader.isModLoaded("polyglottooltip")) {
            result.add("aliases", gson.toJsonTree(Class.forName("com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryNameResolver")
                    .getMethod("resolveItemNames", ItemStack.class).invoke(null, stack)));
            result.add("generation", gson.toJsonTree(Class.forName("com.starskyxiii.polyglottooltip.legacy.minecraft.MinecraftLanguageAdapter")
                    .getMethod("generation").invoke(null)));
            result.add("secondaryProcessing", gson.toJsonTree(Class.forName("com.starskyxiii.polyglottooltip.legacy.minecraft.MinecraftLanguageAdapter")
                    .getMethod("enabled").invoke(null)));
            if (params.has("search")) {
                Object aliases = Class.forName("com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryNameResolver")
                        .getMethod("resolveItemNames", ItemStack.class).invoke(null, stack);
                result.add("searchMatch", gson.toJsonTree(Class.forName("com.starskyxiii.polyglottooltip.legacy.search.SearchNameCandidates")
                        .getMethod("containsRootLowercase", String.class, List.class, String.class)
                        .invoke(null, stack.func_82833_r(), aliases, params.get("search").getAsString())));
            }
        }
        return result;
    }

    public static JsonObject configure(JsonObject params) throws Exception {
        Class<?> type = Class.forName("com.starskyxiii.polyglottooltip.legacy.Config");
        Configuration config = (Configuration) type.getMethod("configuration").invoke(null);
        if (params.has("languages")) {
            JsonArray values = params.getAsJsonArray("languages");
            String[] languages = new String[values.size()];
            for (int index = 0; index < values.size(); index++) languages[index] = values.get(index).getAsString();
            config.get("general", "displayLanguage", new String[]{"en_us"}).set(languages);
        }
        for (String key : new String[]{"alwaysShow", "enableChineseScriptMatching"}) {
            if (params.has(key)) config.get("general", key, false).set(params.get(key).getAsBoolean());
        }
        for (String key : new String[]{"tooltipSecondaryNameFormat", "hudSecondaryNameFormat"}) {
            if (params.has(key)) config.get("general", key, "&7").set(params.get(key).getAsString());
        }
        config.save();
        type.getMethod("reload").invoke(null);
        JsonObject result = new JsonObject();
        result.addProperty("saved", true);
        result.addProperty("path", config.getConfigFile().getAbsolutePath());
        return result;
    }

    public static JsonObject contracts() throws Exception {
        Class<?> tableType = Class.forName("com.starskyxiii.polyglottooltip.legacy.minecraft.LanguageTable");
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("chat.type.text", net.minecraft.util.text.translation.I18n.func_74838_a("chat.type.text"));
        entries.put("fluid.water", "Secondary water");
        entries.put("potion.effect.water", "Secondary water bottle");
        Object table = tableType.getConstructor(String.class, Map.class).newInstance("en_us", entries);
        Method resolve = Class.forName("com.starskyxiii.polyglottooltip.legacy.minecraft.ComponentLanguageResolver")
                .getMethod("resolve", ITextComponent.class, tableType);
        JsonObject result = new JsonObject();
        TextComponentString parent = new TextComponentString("A");
        parent.func_150256_b().func_150227_a(true);
        TextComponentString sibling = new TextComponentString("B");
        sibling.func_150256_b().func_150227_a(false);
        parent.func_150257_a(sibling);
        check(result, "componentSiblingStyle", parent.func_150254_d(), resolve.invoke(null, parent, table));
        TextComponentString colored = new TextComponentString("name");
        colored.func_150256_b().func_150238_a(TextFormatting.RED);
        TextComponentTranslation translated = new TextComponentTranslation("chat.type.text", colored, "message");
        translated.func_150256_b().func_150238_a(TextFormatting.GREEN).func_150227_a(true);
        check(result, "componentArgumentStyle", translated.func_150254_d(), resolve.invoke(null, translated, table));
        Class<?> names = Class.forName("com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryNameResolver");
        check(result, "composedFluidRejected", null,
                names.getMethod("resolveFluidName", FluidStack.class, tableType).invoke(null, new FluidStack(composedFluid, 1000), table));
        check(result, "potionSubclassRejected", null,
                names.getMethod("resolveItemName", ItemStack.class, tableType, int.class).invoke(null, new ItemStack(composedPotion), table, 0));
        ItemStack wool = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:wool")));
        Method aliases = names.getMethod("resolveItemNames", ItemStack.class);
        Object white = aliases.invoke(null, wool);
        wool.func_77964_b(14);
        Object red = aliases.invoke(null, wool);
        JsonObject mutation = new JsonObject();
        mutation.add("before", gson.toJsonTree(white));
        mutation.add("after", gson.toJsonTree(red));
        mutation.addProperty("pass", !white.equals(red));
        result.add("sameStackMetadataMutation", mutation);
        ItemStack potion = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:potion")));
        potion.func_77982_d(JsonToNBT.func_180713_a("{Potion:\"minecraft:healing\"}"));
        Object healing = aliases.invoke(null, potion);
        potion.func_77982_d(JsonToNBT.func_180713_a("{Potion:\"minecraft:swiftness\"}"));
        Object swift = aliases.invoke(null, potion);
        JsonObject nbt = new JsonObject();
        nbt.add("before", gson.toJsonTree(healing));
        nbt.add("after", gson.toJsonTree(swift));
        nbt.addProperty("pass", !healing.equals(swift));
        result.add("sameStackNbtMutation", nbt);
        return result;
    }

    private static void check(JsonObject result, String name, Object expected, Object actual) {
        JsonObject check = new JsonObject();
        check.add("expected", gson.toJsonTree(expected));
        check.add("actual", gson.toJsonTree(actual));
        check.addProperty("pass", java.util.Objects.equals(expected, actual));
        result.add(name, check);
    }
}
