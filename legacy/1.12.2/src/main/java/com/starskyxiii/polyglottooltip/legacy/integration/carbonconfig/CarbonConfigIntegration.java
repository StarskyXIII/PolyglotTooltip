package com.starskyxiii.polyglottooltip.legacy.integration.carbonconfig;

import java.util.List;

import com.starskyxiii.polyglottooltip.legacy.integration.Integration;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;

public final class CarbonConfigIntegration implements Integration {
    public static final String ID = "carbonconfig";
    public static final String MODID = "carbonconfig";

    static volatile IntegrationGuard active;

    private final IntegrationGuard guard;

    public CarbonConfigIntegration(IntegrationGuard guard) {
        this.guard = guard;
    }

    public static int savedIndex(List<?> nodes, Object node, int index) {
        IntegrationGuard guard = active;
        if (guard == null || nodes == null) {
            return index;
        }
        int position = guard.call(() -> nodes.indexOf(node), index);
        return position < 0 ? index : position;
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
