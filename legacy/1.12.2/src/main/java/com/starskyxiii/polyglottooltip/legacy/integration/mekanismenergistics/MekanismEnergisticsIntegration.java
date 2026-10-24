package com.starskyxiii.polyglottooltip.legacy.integration.mekanismenergistics;

import com.starskyxiii.polyglottooltip.legacy.integration.Integration;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;

public final class MekanismEnergisticsIntegration implements Integration {
    public static final String ID = "mekanismenergistics";
    public static final String MODID = "mekeng";

    static volatile IntegrationGuard active;

    private final IntegrationGuard guard;

    public MekanismEnergisticsIntegration(IntegrationGuard guard) {
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

    @Override
    public void invalidate() {
        MekanismEnergisticsGases.TEXT.clear();
    }
}
