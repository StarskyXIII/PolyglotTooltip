package com.starskyxiii.polyglottooltip.legacy.integration.ae2fc;

import com.starskyxiii.polyglottooltip.legacy.integration.Integration;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;

public final class Ae2FluidCraftingIntegration implements Integration {
    public static final String ID = "ae2fc";
    public static final String MODID = "ae2fc";

    static volatile IntegrationGuard active;

    private final IntegrationGuard guard;

    public Ae2FluidCraftingIntegration(IntegrationGuard guard) {
        this.guard = guard;
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
}
