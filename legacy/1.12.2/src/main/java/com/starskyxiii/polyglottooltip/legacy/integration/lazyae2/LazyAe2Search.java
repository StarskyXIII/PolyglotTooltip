package com.starskyxiii.polyglottooltip.legacy.integration.lazyae2;

import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.integration.StorageSearchText;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ItemNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryLanguages;

import appeng.api.storage.data.IAEItemStack;

public final class LazyAe2Search {
    static final StorageSearchText TEXT = new StorageSearchText();

    private LazyAe2Search() {}

    public static boolean enabled() {
        IntegrationGuard guard = LazyAe2Integration.active;
        return guard != null && guard.active();
    }

    public static String searchText(Object stack, String primary) {
        IntegrationGuard guard = LazyAe2Integration.active;
        if (guard == null || primary == null || !(stack instanceof IAEItemStack)) {
            return primary;
        }
        return guard.call(() -> TEXT.text(primary, ItemNames.of(((IAEItemStack) stack).getDefinition())), primary);
    }

    public static int generation() {
        return SecondaryLanguages.generation();
    }
}
