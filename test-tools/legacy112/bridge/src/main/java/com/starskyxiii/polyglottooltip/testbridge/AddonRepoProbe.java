package com.starskyxiii.polyglottooltip.testbridge;

import appeng.api.AEApi;
import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.config.YesNo;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.widgets.IScrollSource;
import appeng.client.gui.widgets.ISortSource;
import appeng.core.AEConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class AddonRepoProbe {
    private static final Map<String, Object> repositories = new LinkedHashMap<>();
    private static ViewItems view = ViewItems.ALL;
    private static final ISortSource sorting = new ISortSource() {
        @Override public Enum<?> getSortBy() { return SortOrder.NAME; }
        @Override public Enum<?> getSortDir() { return SortDir.ASCENDING; }
        @Override public Enum<?> getSortDisplay() { return view; }
    };
    private AddonRepoProbe() { }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static JsonObject setup() throws Exception {
        repositories.clear(); view = ViewItems.ALL;
        AEConfig.instance().getConfigManager().putSetting(Settings.SEARCH_MODE, SearchBoxMode.MANUAL_SEARCH);
        AEConfig.instance().getConfigManager().putSetting(Settings.SEARCH_TOOLTIPS, YesNo.NO);
        if (Loader.isModLoaded("mekeng")) {
            Object repo = NativeAccess.create("com.mekeng.github.common.me.client.GasRepo", (IScrollSource) () -> 0, sorting);
            Object channel = AEApi.instance().storage().getStorageChannel((Class) NativeAccess.type("com.mekeng.github.common.me.storage.IGasStorageChannel"));
            for (String gas : new String[]{"hydrogen", "oxygen"}) {
                Object stack = NativeAccess.call(channel, "createStack", AddonNameProbe.gas(gas, 16000));
                NativeAccess.call(stack, "setCraftable", gas.equals("oxygen"));
                NativeAccess.call(repo, "postUpdate", stack);
            }
            NativeAccess.call(repo, "setPower", true);
            repositories.put("gas", repo);
        }
        if (Loader.isModLoaded("thaumicenergistics")) {
            Object repo = NativeAccess.create("thaumicenergistics.client.gui.helpers.MERepo", IItemStorageChannel.class);
            NativeAccess.call(repo, "setScrollBar", NativeAccess.create("thaumicenergistics.client.gui.helpers.GuiScrollBar", 0, 0, 100));
            NativeAccess.call(repo, "setSearchBoxMode", SearchBoxMode.MANUAL_SEARCH);
            for (ItemStack item : new ItemStack[]{new ItemStack(Items.field_151045_i), new ItemStack(Items.field_151042_j), new ItemStack(Items.field_151166_bC)}) {
                IAEItemStack stack = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createStack(item);
                stack.setStackSize(64); stack.setCraftable(item.func_77973_b() == Items.field_151042_j);
                NativeAccess.call(repo, "postUpdate", stack);
            }
            repositories.put("the", repo);
        }
        return search(new JsonObject());
    }

    public static JsonObject search(JsonObject params) throws Exception {
        if (repositories.isEmpty()) throw new IllegalStateException("Call addon.repo.setup first");
        if (params.has("tooltips")) AEConfig.instance().getConfigManager().putSetting(Settings.SEARCH_TOOLTIPS, params.get("tooltips").getAsBoolean() ? YesNo.YES : YesNo.NO);
        if (params.has("viewMode")) view = ViewItems.valueOf(params.get("viewMode").getAsString());
        for (Map.Entry<String, Object> entry : repositories.entrySet()) {
            Object repo = entry.getValue();
            if (params.has("query")) NativeAccess.call(repo, "setSearchString", params.get("query").getAsString());
            if (entry.getKey().equals("the")) NativeAccess.call(repo, "setViewMode", view);
            if (entry.getKey().equals("the") && params.has("searchMode")) NativeAccess.call(repo, "setSearchBoxMode", SearchBoxMode.valueOf(params.get("searchMode").getAsString()));
            NativeAccess.call(repo, "updateView");
        }
        return observe();
    }

    public static JsonObject observe() throws Exception {
        JsonObject result = new JsonObject();
        for (Map.Entry<String, Object> entry : repositories.entrySet()) {
            JsonObject state = new JsonObject(); Object repo = entry.getValue();
            state.addProperty("class", repo.getClass().getName());
            if (entry.getKey().equals("the")) {
                boolean optimized;
                try { NativeAccess.field(repo, "changed"); optimized = true; } catch (NoSuchFieldException ignored) { optimized = false; }
                state.addProperty("optimized", optimized);
            }
            state.addProperty("query", (String) NativeAccess.call(repo, "getSearchString"));
            JsonArray rows = new JsonArray();
            for (Object stack : (Iterable<?>) NativeAccess.get(repo, "view")) {
                JsonObject row = new JsonObject();
                if (stack instanceof IAEItemStack) {
                    IAEItemStack item = (IAEItemStack) stack;
                    row.addProperty("id", String.valueOf(item.getItem().getRegistryName()));
                    row.addProperty("name", item.getDefinition().func_82833_r());
                } else {
                    Object gas = NativeAccess.call(stack, "getGas");
                    row.addProperty("id", (String) NativeAccess.call(gas, "getName"));
                    row.addProperty("name", (String) NativeAccess.call(gas, "getLocalizedName"));
                }
                row.addProperty("count", ((Number) NativeAccess.call(stack, "getStackSize")).longValue());
                row.addProperty("craftable", (Boolean) NativeAccess.call(stack, "isCraftable"));
                rows.add(row);
            }
            state.add("rows", rows); result.add(entry.getKey(), state);
        }
        return result;
    }

    public static JsonObject nameLeaf(JsonObject params) throws Exception {
        Object repo = repositories.get("the");
        if (repo == null) throw new IllegalStateException("No ThE repository");
        String expression = params.get("query").getAsString(); Pattern pattern;
        try { NativeAccess.field(repo, "changed"); expression = expression.toLowerCase(); } catch (NoSuchFieldException ignored) { }
        try { pattern = Pattern.compile(expression, 66); }
        catch (PatternSyntaxException ignored) { pattern = Pattern.compile(Pattern.quote(expression), 66); }
        JsonArray rows = new JsonArray();
        for (Object value : (Iterable<?>) NativeAccess.get(repo, "list")) {
            if ((Boolean) NativeAccess.call(repo, "polyglotDevBridge$searchName", value, pattern)) rows.add(String.valueOf(((IAEItemStack) value).getItem().getRegistryName()));
        }
        JsonObject result = new JsonObject(); result.add("rows", rows); return result;
    }
}
