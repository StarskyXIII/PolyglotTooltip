package com.starskyxiii.polyglottooltip.legacy.integration.lazyae2;

import com.starskyxiii.polyglottooltip.legacy.integration.Integration;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;

public final class LazyAe2Integration implements Integration {
    public static final String ID = "lazyae2";
    public static final String MODID = "threng";

    static volatile IntegrationGuard active;

    private final IntegrationGuard guard;

    public LazyAe2Integration(IntegrationGuard guard) {
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
        LazyAe2Search.TEXT.clear();
    }
}
