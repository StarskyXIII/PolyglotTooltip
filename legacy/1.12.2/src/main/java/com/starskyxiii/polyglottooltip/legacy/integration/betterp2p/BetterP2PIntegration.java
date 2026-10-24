package com.starskyxiii.polyglottooltip.legacy.integration.betterp2p;

import com.starskyxiii.polyglottooltip.legacy.integration.Integration;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;

public final class BetterP2PIntegration implements Integration {
    public static final String ID = "betterp2p";
    public static final String MODID = "betterp2p";

    static volatile IntegrationGuard active;

    private final IntegrationGuard guard;

    public BetterP2PIntegration(IntegrationGuard guard) {
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
