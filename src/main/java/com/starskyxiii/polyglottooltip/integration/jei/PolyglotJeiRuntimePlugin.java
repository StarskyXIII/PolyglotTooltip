package com.starskyxiii.polyglottooltip.integration.jei;
//~ minecraft_identifier

import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

@JeiPlugin
public final class PolyglotJeiRuntimePlugin implements IModPlugin {
    private static volatile IJeiRuntime activeRuntime;

    @Override
    public ResourceLocation getPluginUid() {
        //? if <=1.20.1 {
        /*return new ResourceLocation(PolyglotTooltip.MODID, "runtime");
        *///?} else {
        return ResourceLocation.fromNamespaceAndPath(PolyglotTooltip.MODID, "runtime");
        //?}
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        activeRuntime = jeiRuntime;
        boolean jemiRuntime = isJemiIngredientFilter(jeiRuntime.getIngredientFilter());
        if (jemiRuntime) {
            OptionalEmiSearchRefresh.register();
        }
        JeiSearchRefreshBridge.setRefreshActions(
                () -> refreshIngredientFilter(jeiRuntime, false),
                () -> refreshIngredientFilter(jeiRuntime, true),
                jemiRuntime
        );
    }

    private static boolean isJemiIngredientFilter(Object filterApi) {
        return filterApi.getClass().getName().startsWith("dev.emi.emi.jemi.");
    }

    private static boolean isDummyIngredientFilter(Object filterApi) {
        return filterApi.getClass().getName().equals("mezz.jei.library.ingredients.IngredientFilterApiDummy");
    }

    private static void refreshIngredientFilter(IJeiRuntime expectedRuntime, boolean rebuildSearchIndex) {
        Minecraft.getInstance().execute(() -> {
            if (activeRuntime != expectedRuntime) {
                return;
            }
            try {
                Object filterApi = expectedRuntime.getIngredientFilter();
                if (isJemiIngredientFilter(filterApi)) {
                    OptionalEmiSearchRefresh.refreshOnClientThread(rebuildSearchIndex);
                    return;
                }
                if (isDummyIngredientFilter(filterApi)) {
                    return;
                }
                Field filterField = filterApi.getClass().getDeclaredField("ingredientFilter");
                filterField.setAccessible(true);
                Object filter = filterField.get(filterApi);
                //? if <=1.18.2 {
                /*if (rebuildSearchIndex) {
                    rebuildLegacyElementSearch(filter);
                }
                Method refreshMethod = filter.getClass().getDeclaredMethod("invalidateCache");
                *///?} else {
                Method refreshMethod = filter.getClass().getDeclaredMethod(
                        rebuildSearchIndex ? "rebuildItemFilter" : "invalidateCache"
                );
                //?}
                Method notifyMethod = filter.getClass().getDeclaredMethod("notifyListenersOfChange");
                refreshMethod.setAccessible(true);
                notifyMethod.setAccessible(true);
                refreshMethod.invoke(filter);
                notifyMethod.invoke(filter);

                Object ingredientListOverlay = expectedRuntime.getIngredientListOverlay();
                //? if <=1.18.2 {
                /*Field searchField = ingredientListOverlay.getClass().getDeclaredField("textFieldFilter");
                *///?} else {
                Field searchField = ingredientListOverlay.getClass().getDeclaredField("searchField");
                //?}
                searchField.setAccessible(true);
                EditBox searchBox = (EditBox) searchField.get(ingredientListOverlay);
                searchBox.setValue(searchBox.getValue());
            } catch (ReflectiveOperationException | RuntimeException e) {
                PolyglotTooltip.LOGGER.warn("Unable to refresh JEI search results", e);
            }
        });
    }

    //? if <=1.18.2 {
    /*private static void rebuildLegacyElementSearch(Object filter) throws ReflectiveOperationException {
        Field searchField = filter.getClass().getDeclaredField("elementSearch");
        Field parserField = filter.getClass().getDeclaredField("elementPrefixParser");
        searchField.setAccessible(true);
        parserField.setAccessible(true);
        Object oldSearch = searchField.get(filter);
        Method getAllIngredients = oldSearch.getClass().getMethod("getAllIngredients");
        Collection<?> elements = List.copyOf((Collection<?>) getAllIngredients.invoke(oldSearch));
        Object replacement;
        if (oldSearch.getClass().getName().endsWith("ElementSearchLowMem")) {
            Constructor<?> constructor = oldSearch.getClass().getConstructor();
            replacement = constructor.newInstance();
        } else {
            Object parser = parserField.get(filter);
            Constructor<?> constructor = oldSearch.getClass().getConstructor(parser.getClass());
            replacement = constructor.newInstance(parser);
        }
        Method add = replacement.getClass().getMethod(
                "add", Class.forName("mezz.jei.common.ingredients.IListElementInfo"));
        for (Object element : elements) {
            add.invoke(replacement, element);
        }
        searchField.set(filter, replacement);
    }
    *///?}

    //? if >1.18.2 {
    @Override
    public void onRuntimeUnavailable() {
        activeRuntime = null;
        JeiSearchRefreshBridge.clearRefreshActions();
    }
    //?}
}
