package com.starskyxiii.polyglottooltip.testbridge;

import appeng.api.AEApi;
import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.config.YesNo;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.me.FluidRepo;
import appeng.client.me.ItemRepo;
import appeng.core.AEConfig;
import appeng.items.storage.ItemViewCell;
import appeng.util.Platform;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;

public final class AeProbe {
    private static ItemRepo items;
    private static FluidRepo fluids;
    private static ViewItems viewMode = ViewItems.ALL;
    private static final ISortSource sorting = new ISortSource() {
        @Override public Enum<?> getSortBy() { return SortOrder.NAME; }
        @Override public Enum<?> getSortDir() { return SortDir.ASCENDING; }
        @Override public Enum<?> getSortDisplay() { return viewMode; }
    };

    private AeProbe() { }

    public static JsonObject setup(JsonObject params) throws Exception {
        AEConfig.instance().getConfigManager().putSetting(Settings.SEARCH_MODE, SearchBoxMode.MANUAL_SEARCH);
        AEConfig.instance().getConfigManager().putSetting(Settings.SEARCH_TOOLTIPS, YesNo.NO);
        viewMode = ViewItems.ALL;
        items = new ItemRepo(() -> 0, sorting);
        fluids = new FluidRepo(() -> 0, sorting);
        JsonArray entries = params.getAsJsonArray("items");
        if (entries == null || entries.size() > 256) throw new IllegalArgumentException("Expected at most 256 items");
        for (JsonElement entry : entries) {
            JsonObject value = entry.getAsJsonObject();
            ItemStack stack = item(value);
            IAEItemStack ae = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createStack(stack);
            ae.setStackSize(value.has("count") ? value.get("count").getAsLong() : 64);
            ae.setCraftable(value.has("craftable") && value.get("craftable").getAsBoolean());
            items.postUpdate(ae);
        }
        for (String fluid : new String[]{"water", "lava"}) {
            IAEFluidStack ae = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class)
                    .createStack(new FluidStack(FluidRegistry.getFluid(fluid), 1000));
            ae.setStackSize(fluid.equals("water") ? 16000 : 8000);
            fluids.postUpdate(ae);
        }
        items.updateView();
        fluids.updateView();
        return observe();
    }

    public static JsonObject search(JsonObject params) throws Exception {
        if (items == null) throw new IllegalStateException("Call ae.setup first");
        if (params.has("viewMode")) viewMode = ViewItems.valueOf(params.get("viewMode").getAsString());
        if (params.has("tooltips")) AEConfig.instance().getConfigManager().putSetting(Settings.SEARCH_TOOLTIPS,
                params.get("tooltips").getAsBoolean() ? YesNo.YES : YesNo.NO);
        if (params.has("partition")) {
            JsonElement partition = params.get("partition");
            if (partition.isJsonNull()) items.setViewCell(new ItemStack[0]);
            else {
                ItemStack cell = AEApi.instance().definitions().items().viewCell().maybeStack(1).get();
                IItemHandlerModifiable inventory = (IItemHandlerModifiable) ((ItemViewCell) cell.func_77973_b()).getConfigInventory(cell);
                inventory.setStackInSlot(0, item(partition.getAsJsonObject()));
                items.setViewCell(new ItemStack[]{cell});
            }
        }
        if (params.has("query")) {
            String query = params.get("query").getAsString();
            if (query.length() > 1024) throw new IllegalArgumentException("Query too long");
            items.setSearchString(query);
            fluids.setSearchString(query);
        }
        items.updateView();
        fluids.updateView();
        return observe();
    }

    public static JsonObject observe() {
        if (items == null) throw new IllegalStateException("Call ae.setup first");
        JsonObject result = new JsonObject();
        result.addProperty("itemRepo", items.getClass().getName());
        result.addProperty("itemSource", String.valueOf(items.getClass().getProtectionDomain().getCodeSource().getLocation()));
        result.addProperty("query", items.getSearchString());
        JsonArray itemRows = new JsonArray();
        for (int index = 0; index < items.size(); index++) {
            IAEItemStack stack = items.getReferenceItem(index);
            JsonObject row = new JsonObject();
            row.addProperty("id", String.valueOf(stack.getItem().getRegistryName()));
            row.addProperty("metadata", stack.getItemDamage());
            row.addProperty("name", Platform.getItemDisplayName(stack));
            row.addProperty("count", stack.getStackSize());
            row.addProperty("craftable", stack.isCraftable());
            itemRows.add(row);
        }
        result.add("items", itemRows);
        JsonArray fluidRows = new JsonArray();
        for (int index = 0; index < fluids.size(); index++) {
            IAEFluidStack stack = fluids.getReferenceFluid(index);
            JsonObject row = new JsonObject();
            row.addProperty("id", stack.getFluidStack().getFluid().getName());
            row.addProperty("name", Platform.getFluidDisplayName(stack));
            row.addProperty("count", stack.getStackSize());
            fluidRows.add(row);
        }
        result.add("fluids", fluidRows);
        return result;
    }

    private static ItemStack item(JsonObject value) throws Exception {
        Item item = Item.field_150901_e.func_82594_a(new ResourceLocation(value.get("item").getAsString()));
        if (item == null) throw new IllegalArgumentException("Unknown item");
        ItemStack stack = new ItemStack(item, 1, value.has("metadata") ? value.get("metadata").getAsInt() : 0);
        if (value.has("nbt")) stack.func_77982_d(JsonToNBT.func_180713_a(value.get("nbt").getAsString()));
        return stack;
    }
}
