package com.starskyxiii.polyglottooltip.legacy.integration.theoneprobe;

import com.starskyxiii.polyglottooltip.legacy.integration.Integration;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;

public final class TopIntegration implements Integration {
    public static final String ID = "top";
    public static final String MODID = "theoneprobe";

    static volatile IntegrationGuard active;

    private final IntegrationGuard guard;

    public TopIntegration(IntegrationGuard guard) {
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
