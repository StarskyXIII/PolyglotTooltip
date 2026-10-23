package com.starskyxiii.polyglottooltip.legacy.integration.ae2;

import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.integration.StorageSearchText;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ItemNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryLanguages;
import com.starskyxiii.polyglottooltip.legacy.mixin.ae2.Ae2TerminalAccessor;

import appeng.api.storage.data.IAEItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

public final class Ae2Search {
    static final StorageSearchText TEXT = new StorageSearchText();

    private Ae2Search() {}

    public static boolean enabled() {
        IntegrationGuard guard = Ae2Integration.active;
        return guard != null && guard.active();
    }

    public static String searchText(Object stack, String primary) {
        IntegrationGuard guard = Ae2Integration.active;
        if (guard == null || primary == null || !(stack instanceof IAEItemStack)) {
            return primary;
        }
        return guard.call(() -> TEXT.text(primary, ItemNames.of(((IAEItemStack) stack).getDefinition())), primary);
    }

    public static int generation() {
        return SecondaryLanguages.generation();
    }

    static void refreshOpenTerminal() {
        GuiScreen screen = Minecraft.func_71410_x().field_71462_r;
        if (screen instanceof Ae2TerminalAccessor) {
            Ae2TerminalAccessor terminal = (Ae2TerminalAccessor) screen;
            terminal.polyglot$repo().updateView();
            terminal.polyglot$setScrollBar();
        }
    }
}
