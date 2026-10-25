package com.starskyxiii.polyglottooltip.legacy.integration.projecte;

import com.starskyxiii.polyglottooltip.legacy.integration.Integration;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;

public final class ProjectEIntegration implements Integration {
    public static final String ID = "projecte";
    public static final String MODID = "projecte";

    static volatile IntegrationGuard active;

    private final IntegrationGuard guard;

    public ProjectEIntegration(IntegrationGuard guard) {
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
        ProjectESearch.TEXT.clear();
    }
}
