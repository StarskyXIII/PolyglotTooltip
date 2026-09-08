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
import java.lang.reflect.Method;

@JeiPlugin
public final class PolyglotJeiRuntimePlugin implements IModPlugin {
    private static volatile IJeiRuntime activeRuntime;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(PolyglotTooltip.MODID, "runtime");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        activeRuntime = jeiRuntime;
        JeiSearchRefreshBridge.setRefreshActions(
                () -> refreshIngredientFilter(jeiRuntime, false),
                () -> refreshIngredientFilter(jeiRuntime, true)
        );
    }

    private static void refreshIngredientFilter(IJeiRuntime expectedRuntime, boolean rebuildSearchIndex) {
        Minecraft.getInstance().execute(() -> {
            if (activeRuntime != expectedRuntime) {
                return;
            }
            try {
                Object filterApi = expectedRuntime.getIngredientFilter();
                Field filterField = filterApi.getClass().getDeclaredField("ingredientFilter");
                filterField.setAccessible(true);
                Object filter = filterField.get(filterApi);
                Method refreshMethod = filter.getClass().getDeclaredMethod(
                        rebuildSearchIndex ? "rebuildItemFilter" : "invalidateCache"
                );
                Method notifyMethod = filter.getClass().getDeclaredMethod("notifyListenersOfChange");
                refreshMethod.setAccessible(true);
                notifyMethod.setAccessible(true);
                refreshMethod.invoke(filter);
                notifyMethod.invoke(filter);

                Object ingredientListOverlay = expectedRuntime.getIngredientListOverlay();
                Field searchField = ingredientListOverlay.getClass().getDeclaredField("searchField");
                searchField.setAccessible(true);
                EditBox searchBox = (EditBox) searchField.get(ingredientListOverlay);
                searchBox.setValue(searchBox.getValue());
            } catch (ReflectiveOperationException | RuntimeException e) {
                PolyglotTooltip.LOGGER.warn("Unable to refresh JEI search results", e);
            }
        });
    }

    @Override
    public void onRuntimeUnavailable() {
        activeRuntime = null;
        JeiSearchRefreshBridge.clearRefreshActions();
    }
}
