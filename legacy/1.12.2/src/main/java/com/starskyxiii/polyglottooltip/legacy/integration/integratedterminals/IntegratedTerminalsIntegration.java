package com.starskyxiii.polyglottooltip.legacy.integration.integratedterminals;

import com.starskyxiii.polyglottooltip.legacy.integration.Integration;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;

public final class IntegratedTerminalsIntegration implements Integration {
    public static final String ID = "integratedterminals";
    public static final String MODID = "integratedterminals";

    static volatile IntegrationGuard active;

    private final IntegrationGuard guard;

    public IntegratedTerminalsIntegration(IntegrationGuard guard) {
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
        IntegratedTerminalsSearch.TEXT.clear();
    }
}
