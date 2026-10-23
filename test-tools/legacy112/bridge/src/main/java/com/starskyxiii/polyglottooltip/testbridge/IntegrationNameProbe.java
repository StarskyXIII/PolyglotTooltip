package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import java.util.LinkedHashMap;
import java.util.Map;

public final class IntegrationNameProbe {
    private static final Gson gson = new Gson();
    private static final Map<String, ItemStack> items = new LinkedHashMap<>();
    private IntegrationNameProbe() { }

    public static JsonObject inspect(JsonObject params) throws Exception {
        if (items.isEmpty() || params.has("reset")) setup();
        if (params.has("mutate")) {
            ItemStack stack = items.get(params.get("mutate").getAsString());
            if (stack == null) throw new IllegalArgumentException("Unknown retained stack");
            if (params.has("damage")) stack.func_77964_b(params.get("damage").getAsInt());
            if (params.has("entity")) stack.func_77978_p().func_74778_a("entity", params.get("entity").getAsString());
        }
        JsonObject result = new JsonObject();
        JsonArray rows = new JsonArray();
        for (Map.Entry<String, ItemStack> entry : items.entrySet()) rows.add(item(entry.getKey(), entry.getValue()));
        result.add("items", rows);
        JsonArray fluids = new JsonArray();
        for (Fluid fluid : FluidRegistry.getRegisteredFluids().values()) {
            if (!fluid.getClass().getName().equals("gregtech.api.fluids.GTFluid$GTMaterialFluid")) continue;
            FluidStack stack = new FluidStack(fluid, 12345);
            JsonObject row = fluid(stack);
            Object material = NativeAccess.call(fluid, "getMaterial");
            row.addProperty("materialKey", (String) NativeAccess.call(material, "getUnlocalizedName"));
            row.addProperty("wrapper", (String) NativeAccess.get(fluid, "translationKey"));
            fluids.add(row);
        }
        result.add("fluids", fluids);
        result.addProperty("language", Minecraft.func_71410_x().field_71474_y.field_74363_ab);
        return result;
    }

    private static void setup() throws Exception {
        items.clear();
        if (Loader.isModLoaded("industrialforegoing")) {
            Class<?> registry = NativeAccess.type("com.buuz135.industrial.proxy.ItemRegistry");
            for (String type : new String[]{"laserLensItem", "laserLensItem_inverted"}) {
                Item item = (Item) NativeAccess.get(registry, type);
                for (int damage = 0; damage < 16; damage++) items.put(type + "-" + damage, new ItemStack(item, 1, damage));
            }
            Item mob = (Item) NativeAccess.get(registry, "mobImprisonmentToolItem");
            items.put("mob-empty", new ItemStack(mob));
            for (String entity : new String[]{"pig", "cow", "unregistered"}) {
                ItemStack stack = new ItemStack(mob); NBTTagCompound tag = new NBTTagCompound();
                tag.func_74778_a("entity", "minecraft:" + entity); tag.func_74780_a("Health", 7.5);
                tag.func_74778_a("CustomName", "Inner literal 42"); stack.func_77982_d(tag);
                items.put("mob-" + entity, stack);
            }
        }
        if (Loader.isModLoaded("storagedrawers")) {
            Class<?> blocks = NativeAccess.type("com.jaquadro.minecraft.storagedrawers.core.ModBlocks");
            Block drawer = (Block) NativeAccess.get(blocks, "basicDrawers"), trim = (Block) NativeAccess.get(blocks, "trim");
            for (int damage = 0; damage < 5; damage++) for (String material : new String[]{"oak", "spruce"}) {
                ItemStack stack = new ItemStack(drawer, 1, damage); NBTTagCompound tag = new NBTTagCompound();
                tag.func_74778_a("material", material); stack.func_77982_d(tag);
                items.put("drawer-" + damage + "-" + material, stack);
            }
            for (int damage = 0; damage < 6; damage++) items.put("trim-" + damage, new ItemStack(trim, 1, damage));
            ItemStack sealed = items.get("drawer-0-oak").func_77946_l(); sealed.func_77978_p().func_74782_a("tile", new NBTTagCompound());
            items.put("drawer-sealed", sealed);
        }
        for (Map.Entry<String, ItemStack> entry : new LinkedHashMap<>(items).entrySet()) {
            if (!entry.getKey().equals("laserLensItem-0") && !entry.getKey().equals("mob-pig") && !entry.getKey().equals("drawer-sealed")) continue;
            ItemStack custom = entry.getValue().func_77946_l(); custom.func_151001_c("Literal addon name 42");
            items.put(entry.getKey() + "-custom", custom);
        }
    }

    public static JsonObject open(JsonObject params) throws Exception {
        if (items.isEmpty()) setup();
        ItemStack stack = items.get(params.get("key").getAsString());
        if (stack == null) throw new IllegalArgumentException("Unknown stack");
        Minecraft client = Minecraft.func_71410_x();
        if (client.field_71439_g == null) throw new IllegalStateException("No player");
        InventoryBasic inventory = new InventoryBasic("Native item fixture", true, 27);
        inventory.func_70299_a(0, stack);
        GuiChest screen = new GuiChest(client.field_71439_g.field_71071_by, inventory);
        client.func_147108_a(screen);
        JsonObject result = item(params.get("key").getAsString(), stack);
        Slot slot = screen.field_147002_h.func_75139_a(0);
        int left = (Integer) NativeAccess.get(screen, "field_147003_i"), top = (Integer) NativeAccess.get(screen, "field_147009_r");
        result.addProperty("x", left + slot.field_75223_e + 8); result.addProperty("y", top + slot.field_75221_f + 8);
        result.addProperty("screen", screen.getClass().getName()); result.addProperty("slotSame", slot.func_75211_c() == stack);
        return result;
    }

    private static JsonObject item(String key, ItemStack stack) throws Exception {
        NBTTagCompound before = stack.func_77955_b(new NBTTagCompound());
        JsonObject row = new JsonObject();
        row.addProperty("key", key); row.addProperty("class", stack.func_77973_b().getClass().getName());
        row.addProperty("id", String.valueOf(stack.func_77973_b().getRegistryName()));
        row.addProperty("nativeName", stack.func_82833_r()); row.addProperty("rawKey", stack.func_77973_b().func_77667_c(stack));
        row.addProperty("rawNativeName", net.minecraft.client.resources.I18n.func_135052_a(stack.func_77973_b().func_77667_c(stack) + ".name"));
        row.addProperty("serialized", before.toString());
        row.add("tooltip", gson.toJsonTree(stack.func_82840_a(Minecraft.func_71410_x().field_71439_g, ITooltipFlag.TooltipFlags.NORMAL)));
        if (Loader.isModLoaded("polyglottooltip")) row.add("aliases", gson.toJsonTree(ProductNames.item(stack)));
        row.addProperty("unchanged", before.equals(stack.func_77955_b(new NBTTagCompound())));
        row.addProperty("count", stack.func_190916_E()); row.addProperty("nbt", String.valueOf(stack.func_77978_p()));
        row.addProperty("identity", System.identityHashCode(stack));
        return row;
    }

    public static JsonObject fluid(FluidStack stack) throws Exception {
        JsonObject row = new JsonObject();
        row.addProperty("id", stack.getFluid().getName()); row.addProperty("class", stack.getFluid().getClass().getName());
        row.addProperty("nativeName", stack.getLocalizedName()); row.addProperty("amount", stack.amount);
        NBTTagCompound before = stack.writeToNBT(new NBTTagCompound());
        if (Loader.isModLoaded("polyglottooltip")) row.add("aliases", gson.toJsonTree(ProductNames.fluid(stack)));
        row.addProperty("unchanged", before.equals(stack.writeToNBT(new NBTTagCompound())));
        return row;
    }
}
