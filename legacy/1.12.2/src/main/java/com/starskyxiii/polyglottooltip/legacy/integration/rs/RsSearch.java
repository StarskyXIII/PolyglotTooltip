package com.starskyxiii.polyglottooltip.legacy.integration.rs;

import com.raoulvdberge.refinedstorage.gui.grid.GuiGrid;
import com.raoulvdberge.refinedstorage.gui.grid.stack.GridStackItem;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.integration.StorageSearchText;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ItemNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryLanguages;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

public final class RsSearch {
    static final StorageSearchText TEXT = new StorageSearchText();

    private RsSearch() {}

    public static boolean enabled() {
        IntegrationGuard guard = RsIntegration.active;
        return guard != null && guard.active();
    }

    public static String searchText(Object stack, String primary) {
        IntegrationGuard guard = RsIntegration.active;
        if (guard == null || primary == null || !(stack instanceof GridStackItem)) {
            return primary;
        }
        return guard.call(() -> TEXT.text(primary, ItemNames.of(((GridStackItem) stack).getStack())), primary);
    }

    public static int generation() {
        return SecondaryLanguages.generation();
    }

    static void refreshOpenGrid() {
        GuiScreen screen = Minecraft.func_71410_x().field_71462_r;
        if (screen instanceof GuiGrid && ((GuiGrid) screen).getView() != null) {
            ((GuiGrid) screen).getView().sort();
        }
    }
}
