package com.starskyxiii.polyglottooltip.legacy.integration.hwyla;

import com.starskyxiii.polyglottooltip.legacy.integration.Integration;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;

public final class HwylaIntegration implements Integration {
    public static final String ID = "hwyla";
    public static final String MODID = "waila";

    static volatile IntegrationGuard active;

    private final IntegrationGuard guard;

    public HwylaIntegration(IntegrationGuard guard) {
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
