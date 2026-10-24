package com.starskyxiii.polyglottooltip.legacy.integration.mekanism;

import com.starskyxiii.polyglottooltip.legacy.integration.Integration;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;

public final class MekanismIntegration implements Integration {
    public static final String ID = "mekanism";
    public static final String MODID = "mekanism";

    static volatile IntegrationGuard active;

    private final IntegrationGuard guard;

    public MekanismIntegration(IntegrationGuard guard) {
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
