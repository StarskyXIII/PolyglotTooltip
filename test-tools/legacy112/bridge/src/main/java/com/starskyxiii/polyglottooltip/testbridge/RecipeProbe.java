package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mezz.jei.Internal;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.ingredients.IngredientBlacklistInternal;
import mezz.jei.ingredients.IngredientFilter;
import mezz.jei.ingredients.IngredientRegistry;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;

public final class RecipeProbe {
    private RecipeProbe() { }

    public static JsonObject search(JsonObject params) throws Exception {
        String query = params.get("query").getAsString();
        if (query.length() > 1024) throw new IllegalArgumentException("Query too long");
        Internal.getIngredientFilter().setFilterText(query);
        return observe();
    }

    public static JsonObject observe() throws Exception {
        IngredientFilter filter = Internal.getIngredientFilter();
        if (filter == null) throw new IllegalStateException("Recipe filter not ready");
        JsonObject result = new JsonObject();
        result.addProperty("query", filter.getFilterText());
        result.addProperty("identity", System.identityHashCode(filter));
        result.addProperty("class", filter.getClass().getName());
        result.addProperty("source", String.valueOf(filter.getClass().getProtectionDomain().getCodeSource().getLocation()));
        JsonArray indexes = new JsonArray();
        for (String name : new String[]{"searchTree", "elementSearch", "backgroundBuilder"}) {
            try {
                Field field = filter.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(filter);
                JsonObject index = new JsonObject();
                index.addProperty("field", name);
                index.addProperty("class", value.getClass().getName());
                index.addProperty("identity", System.identityHashCode(value));
                indexes.add(index);
            } catch (NoSuchFieldException ignored) { }
        }
        result.add("indexes", indexes);
        List<IIngredientListElement> elements = filter.getIngredientList();
        result.addProperty("count", elements.size());
        JsonArray rows = new JsonArray();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (IIngredientListElement<?> element : elements) {
            Object ingredient = element.getIngredient();
            JsonObject row = new JsonObject();
            row.addProperty("type", ingredient.getClass().getName());
            row.addProperty("name", element.getDisplayName());
            if (ingredient instanceof ItemStack) {
                ItemStack stack = (ItemStack) ingredient;
                row.addProperty("id", String.valueOf(stack.func_77973_b().getRegistryName()));
                row.addProperty("metadata", stack.func_77960_j());
                row.addProperty("nbt", String.valueOf(stack.func_77978_p()));
            } else if (ingredient instanceof FluidStack) row.addProperty("id", ((FluidStack) ingredient).getFluid().getName());
            digest.update(row.toString().getBytes(StandardCharsets.UTF_8));
            if (rows.size() < 500) rows.add(row);
        }
        result.add("rows", rows);
        StringBuilder hash = new StringBuilder();
        for (byte part : digest.digest()) hash.append(String.format("%02x", part & 255));
        result.addProperty("digest", hash.toString());
        if (Internal.getRuntime() != null) result.add("ui", ui(new JsonObject()));
        return result;
    }

    public static JsonObject ui(JsonObject params) throws Exception {
        Object overlay = Internal.getRuntime().getIngredientListOverlay();
        GuiTextField search = (GuiTextField) field(overlay, "searchField").get(overlay);
        Object contents = field(overlay, "contents").get(overlay);
        if (params.has("cursor")) search.func_146190_e(params.get("cursor").getAsInt());
        if (params.has("selection")) search.func_146199_i(params.get("selection").getAsInt());
        if (params.has("focused")) search.func_146195_b(params.get("focused").getAsBoolean());
        if (params.has("nextPage") && params.get("nextPage").getAsBoolean()) {
            Object pages = field(contents, "pageDelegate").get(contents);
            Class.forName("mezz.jei.input.IPaged").getMethod("nextPage").invoke(pages);
        }
        JsonObject result = new JsonObject();
        result.addProperty("query", search.func_146179_b());
        result.addProperty("cursor", search.func_146198_h());
        result.addProperty("selection", search.func_146186_n());
        result.addProperty("focused", search.func_146206_l());
        result.addProperty("firstItemIndex", field(contents, "firstItemIndex").getInt(contents));
        return result;
    }

    public static JsonObject input(JsonObject params) throws Exception {
        if (params.has("text") == params.has("key")) throw new IllegalArgumentException("Choose text or key input");
        Object overlay = Internal.getRuntime().getIngredientListOverlay();
        if (!(Boolean) overlay.getClass().getMethod("hasKeyboardFocus").invoke(overlay)) {
            throw new IllegalStateException("Native recipe search is not focused");
        }
        Method press = overlay.getClass().getMethod("onKeyPressed", char.class, int.class);
        JsonObject result = new JsonObject();
        result.addProperty("class", overlay.getClass().getName());
        result.addProperty("source", String.valueOf(overlay.getClass().getProtectionDomain().getCodeSource().getLocation()));
        result.addProperty("method", "onKeyPressed(char,int)");
        result.add("before", ui(new JsonObject()));
        JsonArray handled = new JsonArray();
        if (params.has("text")) {
            String text = params.get("text").getAsString();
            if (text.length() > 1024) throw new IllegalArgumentException("Text too long");
            for (char character : text.toCharArray()) handled.add((Boolean) press.invoke(overlay, character, 0));
        } else {
            int key = params.get("key").getAsInt();
            int repeat = params.has("repeat") ? params.get("repeat").getAsInt() : 1;
            if (key != 14 || repeat < 1 || repeat > 1024) throw new IllegalArgumentException("Invalid recipe backspace input");
            for (int index = 0; index < repeat; index++) handled.add((Boolean) press.invoke(overlay, '\0', key));
        }
        result.add("handled", handled);
        result.add("after", ui(new JsonObject()));
        result.addProperty("query", Internal.getIngredientFilter().getFilterText());
        return result;
    }

    private static Field field(Object owner, String name) throws NoSuchFieldException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    public static JsonObject hide(JsonObject params) throws Exception {
        ItemStack stack = item(params);
        IngredientFilter filter = Internal.getIngredientFilter();
        Field field = filter.getClass().getDeclaredField("blacklist");
        field.setAccessible(true);
        IngredientBlacklistInternal blacklist = (IngredientBlacklistInternal) field.get(filter);
        if (params.get("hidden").getAsBoolean()) blacklist.addIngredientToBlacklist(stack, Internal.getIngredientRegistry().getIngredientHelper(stack));
        else blacklist.removeIngredientFromBlacklist(stack, Internal.getIngredientRegistry().getIngredientHelper(stack));
        filter.updateHidden();
        filter.invalidateCache();
        return observe();
    }

    public static JsonObject dynamic(JsonObject params) throws Exception {
        ItemStack stack = item(params);
        IngredientRegistry registry = Internal.getIngredientRegistry();
        if (params.get("add").getAsBoolean()) registry.addIngredientsAtRuntime(ItemStack.class, Collections.singletonList(stack));
        else registry.removeIngredientsAtRuntime(ItemStack.class, Collections.singletonList(stack));
        return observe();
    }

    private static ItemStack item(JsonObject params) throws Exception {
        Item item = Item.field_150901_e.func_82594_a(new ResourceLocation(params.get("item").getAsString()));
        if (item == null) throw new IllegalArgumentException("Unknown item");
        ItemStack stack = new ItemStack(item, 1, params.has("metadata") ? params.get("metadata").getAsInt() : 0);
        if (params.has("nbt")) stack.func_77982_d(JsonToNBT.func_180713_a(params.get("nbt").getAsString()));
        return stack;
    }
}
