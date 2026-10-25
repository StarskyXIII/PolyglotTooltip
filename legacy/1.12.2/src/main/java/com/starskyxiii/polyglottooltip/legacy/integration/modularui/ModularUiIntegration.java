package com.starskyxiii.polyglottooltip.legacy.integration.modularui;

import com.starskyxiii.polyglottooltip.legacy.integration.Integration;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;

public final class ModularUiIntegration implements Integration {
    public static final String ID = "modularui";
    public static final String MODID = "modularui";

    static volatile IntegrationGuard active;

    private final IntegrationGuard guard;

    public ModularUiIntegration(IntegrationGuard guard) {
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
