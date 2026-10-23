package com.starskyxiii.polyglottooltip.legacy.integration.carbonconfig;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;

public class CarbonConfigIntegrationTest {
    private final List<String> nodes = Arrays.asList("a", "b", "c");

    @After
    public void stop() {
        CarbonConfigIntegration.active = null;
    }

    @Test
    public void keepsTheOriginalIndexWhileInactive() {
        assertEquals(0, CarbonConfigIntegration.savedIndex(nodes, "c", 0));
    }

    @Test
    public void savesToTheEditedNodePosition() {
        new CarbonConfigIntegration(new IntegrationGuard("carbonconfig", (message, error) -> {})).start();
        assertEquals(2, CarbonConfigIntegration.savedIndex(nodes, "c", 0));
        assertEquals(1, CarbonConfigIntegration.savedIndex(nodes, "missing", 1));
        assertEquals(1, CarbonConfigIntegration.savedIndex(null, "c", 1));
    }
}
