package com.starskyxiii.polyglottooltip.testbridge;

import appeng.api.storage.data.IAEItemStack;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AddonCraftingProbe {
    private static final Gson gson = new Gson();
    private static final String FC = "com.glodblock.github.";
    private static final Map<String, IAEItemStack> drops = new LinkedHashMap<>();
    private static GuiInventory renderer;
    private static GuiScreen packaged, encoder;
    private AddonCraftingProbe() { }

    public static JsonObject inspect(JsonObject params) throws Exception {
        Minecraft client = Minecraft.func_71410_x();
        if (!Loader.isModLoaded("ae2fc") || client.field_71439_g == null) throw new IllegalStateException("Expected Fluid Crafting and live player");
        if (params.has("render")) return render(params);
        JsonObject result = new JsonObject();
        if (Loader.isModLoaded("packagedauto")) {
            if (packaged == null) {
                TileEntity tile = (TileEntity) NativeAccess.create("thelm.packagedauto.tile.TileEncoder");
                tile.func_145834_a(client.field_71441_e);
                Object container = NativeAccess.create("thelm.packagedauto.container.ContainerEncoder", client.field_71439_g.field_71071_by, tile);
                packaged = (GuiScreen) NativeAccess.create("thelm.packagedauto.client.gui.GuiEncoder", container);
                initialize(packaged);
            }
            result.add("packaged", tooltips(packaged, false));
        }
        try {
            NativeAccess.type(FC + "client.GuiFluidPatternEncoder");
            if (encoder == null) {
                TileEntity tile = (TileEntity) NativeAccess.create(FC + "common.tile.TileFluidPatternEncoder");
                tile.func_145834_a(client.field_71441_e);
                encoder = (GuiScreen) NativeAccess.create(FC + "client.GuiFluidPatternEncoder", client.field_71439_g.field_71071_by, tile);
                initialize(encoder);
            }
            result.add("encoder", tooltips(encoder, true));
        } catch (ClassNotFoundException ignored) { }
        return result;
    }

    private static void initialize(GuiScreen screen) {
        Minecraft client = Minecraft.func_71410_x(); ScaledResolution resolution = new ScaledResolution(client);
        screen.func_146280_a(client, resolution.func_78326_a(), resolution.func_78328_b());
    }

    private static JsonArray tooltips(GuiScreen screen, boolean override) throws Exception {
        Map<String, ItemStack> packets = new LinkedHashMap<>();
        Class<?> fluids = NativeAccess.type(FC + "common.item.fake.FakeFluids");
        for (String name : new String[]{"water", "lava"}) {
            FluidStack fluid = new FluidStack(FluidRegistry.getFluid(name), 12345);
            packets.put(name, (ItemStack) NativeAccess.call(fluids, "packFluid2Packet", fluid));
            packets.put(name + "-display", (ItemStack) NativeAccess.call(fluids, "displayFluid", fluid));
        }
        ItemStack zero = packets.get("water").func_77946_l(); zero.func_77978_p().func_74775_l("FluidStack").func_74768_a("Amount", 0); packets.put("zero", zero);
        ItemStack custom = packets.get("water").func_77946_l(); custom.func_151001_c("Literal packet 42"); packets.put("custom", custom);
        packets.put("invalid", new ItemStack(packets.get("water").func_77973_b()));
        packets.put("ordinary", new ItemStack(Items.field_151045_i));
        if (Loader.isModLoaded("mekeng")) packets.put("gas", (ItemStack) NativeAccess.call(NativeAccess.type(FC + "integration.mek.FakeGases"), "packGas2Packet", AddonNameProbe.gas("hydrogen", 12345)));
        Minecraft client = Minecraft.func_71410_x(); GuiScreen previous = client.field_71462_r;
        JsonArray rows = new JsonArray();
        try {
            client.field_71462_r = screen;
            for (Map.Entry<String, ItemStack> entry : packets.entrySet()) {
                ItemStack item = entry.getValue(); NBTTagCompound before = item.func_77955_b(new NBTTagCompound());
                List<?> lines = override ? (List<?>) NativeAccess.call(screen, "func_191927_a", item) : item.func_82840_a(client.field_71439_g, ITooltipFlag.TooltipFlags.NORMAL);
                JsonObject row = new JsonObject(); row.addProperty("key", entry.getKey()); row.add("lines", gson.toJsonTree(lines));
                Object content = NativeAccess.call(NativeAccess.type(FC + "common.item.fake.FakeItemRegister"), "getStack", item.func_77946_l());
                if (content instanceof FluidStack) {
                    row.addProperty("contentName", ((FluidStack) content).getLocalizedName());
                    row.addProperty("contentAmount", ((FluidStack) content).amount);
                    row.addProperty("expectedAmountLine", String.format("%,d mB", ((FluidStack) content).amount));
                }
                row.addProperty("unchanged", before.equals(item.func_77955_b(new NBTTagCompound()))); rows.add(row);
            }
        } finally { client.field_71462_r = previous; }
        return rows;
    }

    private static JsonObject render(JsonObject params) throws Exception {
        String content = params.get("render").getAsString();
        boolean gas = content.equals("hydrogen") || content.equals("oxygen");
        if (!gas && !content.equals("water") && !content.equals("lava")) throw new IllegalArgumentException("Unknown content");
        long amount = params.has("amount") ? params.get("amount").getAsLong() : 12345;
        boolean storage = !params.has("storage") || params.get("storage").getAsBoolean();
        if (amount < 0 || amount > 1000000000000L) throw new IllegalArgumentException("Invalid amount");
        String key = content + ":" + amount;
        IAEItemStack drop = drops.get(key);
        if (drop == null) {
            drop = (IAEItemStack) NativeAccess.call(NativeAccess.type(FC + (gas ? "integration.mek.FakeGases" : "common.item.fake.FakeFluids")),
                    gas ? "packGas2AEDrops" : "packFluid2AEDrops", gas ? AddonNameProbe.gas(content, 12345) : new FluidStack(FluidRegistry.getFluid(content), 12345));
            drop.setStackSize(amount); drop.setCraftable(amount == 0); drops.put(key, drop);
        }
        if (renderer == null) { renderer = new GuiInventory(Minecraft.func_71410_x().field_71439_g); initialize(renderer); }
        Class<?> util = NativeAccess.type(FC + "util.UtilClient");
        Object cache = NativeAccess.get(util, "cacheTooltip");
        Object before = NativeAccess.call(cache, "getLeft");
        Object previousList = NativeAccess.call(cache, "getRight");
        JsonObject result = new JsonObject(); result.addProperty("previousMatches", before instanceof IAEItemStack && before.equals(drop)
                && ((IAEItemStack) before).getStackSize() == amount && Boolean.valueOf(storage).equals(NativeAccess.get(util, "cacheIsStorage")));
        TooltipProbe.captured = null; TooltipProbe.capturing = true;
        try { result.addProperty("handled", (Boolean) NativeAccess.call(util, gas ? "rendererGas" : "rendererFluid", renderer, drop, 30, 30, storage)); }
        finally { TooltipProbe.capturing = false; }
        if (TooltipProbe.captured == null) throw new IllegalStateException("Native renderer emitted no tooltip");
        result.add("lines", gson.toJsonTree(TooltipProbe.captured));
        result.add("cachedLines", gson.toJsonTree(NativeAccess.call(cache, "getRight")));
        result.addProperty("reusedNativeList", previousList != null && previousList == NativeAccess.call(cache, "getRight"));
        result.addProperty("amount", drop.getStackSize());
        result.addProperty("nativeName", gas ? (String) NativeAccess.call(NativeAccess.call(AddonNameProbe.gas(content, 1), "getGas"), "getLocalizedName") : new FluidStack(FluidRegistry.getFluid(content), (int) Math.min(amount, Integer.MAX_VALUE)).getLocalizedName());
        return result;
    }
}
