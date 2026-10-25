package com.starskyxiii.polyglottooltip.legacy.integration.controlling;

import java.util.Collections;
import java.util.List;

import com.starskyxiii.polyglottooltip.legacy.Config;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.integration.StorageSearchText;
import com.starskyxiii.polyglottooltip.legacy.minecraft.KeyedNames;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import us.getfluxed.controlsearch.client.gui.GuiNewControls;

public final class ControllingSearch {
    private ControllingSearch() {}

    public static String searchText(String key, String shown) {
        IntegrationGuard guard = ControllingIntegration.active;
        if (guard == null || key == null || shown == null) {
            return shown;
        }
        return guard.call(() -> text(shown, KeyedNames.of(shown, Collections.singletonList(key)).names(),
                Config.settings().chineseScriptMatching()), shown);
    }

    static String text(String shown, List<String> names, boolean chineseScriptMatching) {
        return names.isEmpty() ? shown : StorageSearchText.compose(shown, names, chineseScriptMatching);
    }

    static void refreshOpenScreen() {
        GuiScreen screen = Minecraft.func_71410_x().field_71462_r;
        if (screen instanceof GuiNewControls) {
            ((GuiNewControls) screen).refreshKeys();
        }
    }
}
