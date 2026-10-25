package com.starskyxiii.polyglottooltip.legacy.integration.projecte;

import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.integration.StorageSearchText;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ItemNames;

import net.minecraft.item.ItemStack;

public final class ProjectESearch {
    static final StorageSearchText TEXT = new StorageSearchText();

    private ProjectESearch() {}

    public static String searchText(ItemStack stack, String primary) {
        IntegrationGuard guard = ProjectEIntegration.active;
        if (guard == null || stack == null || primary == null) {
            return primary;
        }
        return guard.call(() -> TEXT.text(primary, ItemNames.of(stack)), primary);
    }
}
