package com.starskyxiii.polyglottooltip.legacy.integration.notenoughenergistics;

import com.starskyxiii.polyglottooltip.legacy.integration.Integration;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.integration.StorageSearchText;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ItemNames;

import net.minecraft.item.ItemStack;

public final class NotEnoughEnergisticsIntegration implements Integration {
    public static final String ID = "notenoughenergistics";
    public static final String MODID = "neenergistics";

    static final StorageSearchText TEXT = new StorageSearchText();
    static volatile IntegrationGuard active;

    private final IntegrationGuard guard;

    public NotEnoughEnergisticsIntegration(IntegrationGuard guard) {
        this.guard = guard;
    }

    public static String searchText(ItemStack stack, String primary) {
        IntegrationGuard guard = active;
        if (guard == null || primary == null || stack == null) {
            return primary;
        }
        return guard.call(() -> TEXT.text(primary, ItemNames.of(stack)), primary);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String modid() {
        return MODID;
    }

    @Override
    public void start() {
        active = guard;
    }

    @Override
    public void invalidate() {
        TEXT.clear();
    }
}
