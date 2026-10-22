package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mezz.jei.Internal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class LifecycleProbe {
    private LifecycleProbe() { }

    public static void request(JsonObject params, CompletableFuture<JsonElement> future) throws Exception {
        Minecraft client = Minecraft.func_71410_x();
        GuiScreen screen = client.field_71462_r;
        if (screen == null) throw new IllegalStateException("A retained screen is required");
        String method = params.get("method").getAsString();
        if (!method.equals("language.gui") && !method.equals("language.set")
                && !method.equals("resources.reload") && !method.equals("resources.select")
                && !method.equals("probe.resourceScenario")) throw new IllegalArgumentException("Unsupported transition");
        JsonObject result = new JsonObject();
        result.addProperty("method", method);
        result.add("before", snapshot());
        long started = System.nanoTime();
        if (method.equals("language.gui")) result.add("operation", selectLanguage(params.getAsJsonObject("params"), screen));
        else result.add("operation", BridgeDispatch.dispatch(method, params.getAsJsonObject("params")));
        result.addProperty("operationNanos", System.nanoTime() - started);
        result.add("returned", snapshot());
        if (client.field_71462_r != screen) throw new IllegalStateException("Transition replaced retained screen");
        JsonObject render = params.getAsJsonObject("render");
        if (render.has("configuration") || render.has("inherited")
                || !render.has("nativeEvents") || !render.get("nativeEvents").getAsBoolean())
            throw new IllegalArgumentException("Transition requires unmodified native render");
        CompletableFuture<JsonElement> rendered = new CompletableFuture<>();
        rendered.whenComplete((frame, error) -> {
            if (error != null) { future.completeExceptionally(error); return; }
            try {
                result.add("firstFrame", frame);
                result.add("afterFrame", snapshot());
                result.addProperty("firstFrameNanos", System.nanoTime() - started);
                future.complete(result);
            } catch (Throwable failure) { future.completeExceptionally(failure); }
        });
        ScreenProbe.request(render, rendered);
    }

    private static JsonObject selectLanguage(JsonObject params, GuiScreen parent) throws Exception {
        Minecraft client = Minecraft.func_71410_x();
        GuiLanguage language = new GuiLanguage(parent, client.field_71474_y, client.func_135016_M());
        client.func_147108_a(language);
        Object list = NativeAccess.get(language, "field_146450_f");
        List<?> codes = (List<?>) NativeAccess.get(list, "field_148176_l");
        String code = params.get("language").getAsString();
        int index = codes.indexOf(code);
        if (index < 0) throw new IllegalArgumentException("Unknown language " + code);
        NativeAccess.call(list, "func_148144_a", index, false, 0, 0);
        JsonObject result = new JsonObject();
        result.addProperty("class", list.getClass().getName());
        result.addProperty("callback", "func_148144_a(int,boolean,int,int)");
        result.addProperty("index", index);
        result.addProperty("language", code);
        result.add("screen", BridgeDispatch.dispatch("screen.snapshot", new JsonObject()));
        NativeAccess.call(language, "func_146284_a", NativeAccess.get(language, "field_146452_r"));
        return result;
    }

    public static JsonObject snapshot() throws Exception {
        JsonObject result = new JsonObject();
        result.add("status", BridgeDispatch.dispatch("status", new JsonObject()));
        result.add("resources", ResourceProbe.snapshot());
        if (Loader.isModLoaded("polyglottooltip")) {
            Object value = NativeAccess.call(NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.minecraft.MinecraftLanguageAdapter"), "snapshot");
            JsonObject language = new JsonObject();
            language.addProperty("primary", (String) NativeAccess.get(value, "primary"));
            language.addProperty("generation", (Number) NativeAccess.get(value, "generation"));
            language.addProperty("nativeNameEpoch", (Number) NativeAccess.get(value, "nativeNameEpoch"));
            language.addProperty("enabled", (Boolean) NativeAccess.call(value, "enabled"));
            language.addProperty("ready", (Boolean) NativeAccess.call(value, "ready"));
            Object settings = NativeAccess.get(value, "settings");
            language.addProperty("alwaysShow", (Boolean) NativeAccess.get(settings, "alwaysShow"));
            JsonArray codes = new JsonArray();
            for (Object code : (List<?>) NativeAccess.get(settings, "languages")) codes.add((String) code);
            language.add("secondaryLanguages", codes);
            result.add("language", language);
            result.add("enchantment", enchantment());
        }
        if (Loader.isModLoaded("jei") && Internal.getRuntime() != null) result.add("jei", recipeVisible());
        if (Loader.isModLoaded("emi")) result.add("emi", EmiProbe.state());
        if (LanguageStateProbe.active()) result.add("translations", LanguageStateProbe.capture());
        return result;
    }

    /** The enchantment name capture as the product sees it: transform-time review, runtime admission, providers. */
    private static JsonObject enchantment() {
        JsonObject result = new JsonObject();
        try {
            String product = "com.starskyxiii.polyglottooltip.legacy.";
            Class<?> patch = NativeAccess.type(product + "bootstrap.EnchantmentCapturePatch");
            Class<?> observer = NativeAccess.type(product + "bootstrap.NameTransformObserver");
            for (java.util.Map.Entry<?, ?> entry : ((java.util.Map<?, ?>) NativeAccess.call(patch, "diagnostics")).entrySet()) {
                result.addProperty(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
            result.addProperty("formatterAdmitted", (Boolean) NativeAccess.call(observer, "admits",
                    "net.minecraft.enchantment.Enchantment", "func_77316_c(I)Ljava/lang/String;"));
            java.util.function.BiPredicate<String, String> admits = (owner, method) -> {
                try { return (Boolean) NativeAccess.call(observer, "admits", owner, method); }
                catch (Exception error) { return false; }
            };
            result.addProperty("handlerAdmitted", (Boolean) NativeAccess.call(patch, "levelKeyHandlerAdmitted",
                    net.minecraft.enchantment.Enchantment.class, admits));
            result.add("providers", new com.google.gson.Gson().toJsonTree(NativeAccess.call(
                    NativeAccess.type(product + "minecraft.NativeEnchantmentScope"), "providerFailures")));
        } catch (Exception error) {
            result.addProperty("error", error.toString());
        }
        return result;
    }

    public static JsonObject recipeVisible() throws Exception {
        Object overlay = Internal.getRuntime().getIngredientListOverlay();
        Object contents = NativeAccess.get(overlay, "contents");
        Object filter = Internal.getIngredientFilter();
        JsonObject result = RecipeProbe.ui(new JsonObject());
        result.addProperty("displayed", (Boolean) NativeAccess.call(overlay, "isListDisplayed"));
        result.addProperty("filterIdentity", System.identityHashCode(filter));
        result.addProperty("filterCached", (String) NativeAccess.get(filter, "filterCached"));
        Object cached = NativeAccess.get(filter, "ingredientListCached");
        result.addProperty("cachedCount", cached == null ? -1 : ((List<?>) cached).size());
        JsonArray rows = new JsonArray();
        for (Object element : (List<?>) NativeAccess.call(contents, "getVisibleElements")) {
            Object ingredient = NativeAccess.get(element, "ingredient");
            JsonObject row = new JsonObject();
            row.addProperty("type", ingredient.getClass().getName());
            if (ingredient instanceof ItemStack) {
                ItemStack stack = (ItemStack) ingredient;
                row.addProperty("item", String.valueOf(stack.func_77973_b().getRegistryName()));
                row.addProperty("damage", stack.func_77960_j());
                row.addProperty("count", stack.func_190916_E());
            }
            rows.add(row);
        }
        result.add("visible", rows);
        return result;
    }
}
