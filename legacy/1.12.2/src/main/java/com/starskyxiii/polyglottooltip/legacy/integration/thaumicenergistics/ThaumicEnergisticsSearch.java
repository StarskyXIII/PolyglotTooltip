package com.starskyxiii.polyglottooltip.legacy.integration.thaumicenergistics;

import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.integration.StorageSearchText;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ItemNames;

import appeng.api.storage.data.IAEItemStack;

public final class ThaumicEnergisticsSearch {
    static final StorageSearchText TEXT = new StorageSearchText();

    private ThaumicEnergisticsSearch() {}

    public static String searchText(Object stack, String primary) {
        IntegrationGuard guard = ThaumicEnergisticsIntegration.active;
        if (guard == null || primary == null || !(stack instanceof IAEItemStack)) {
            return primary;
        }
        return guard.call(() -> TEXT.text(primary, ItemNames.of(((IAEItemStack) stack).getDefinition())), primary);
    }
}
