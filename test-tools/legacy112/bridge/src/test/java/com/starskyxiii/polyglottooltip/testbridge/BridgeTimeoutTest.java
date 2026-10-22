package com.starskyxiii.polyglottooltip.testbridge;

import org.junit.Test;
import static org.junit.Assert.*;

public final class BridgeTimeoutTest {
    @Test public void retainsTheExistingNormalBounds() {
        assertEquals(120, BridgeServer.timeoutSeconds("lifecycle.adversary", Integer.MIN_VALUE, null, "owned"));
        assertEquals(240, BridgeServer.timeoutSeconds("lifecycle.adversary", 240, null, "owned"));
        assertEquals(600, BridgeServer.timeoutSeconds("lifecycle.preparation", Integer.MAX_VALUE, null, "owned"));
    }

    @Test public void appliesTheExplicitDeadlineOnlyToTheTwoOwnedMethods() {
        assertEquals(3600, BridgeServer.timeoutSeconds("lifecycle.adversary", 600, 3600, "owned"));
        assertEquals(601, BridgeServer.timeoutSeconds("lifecycle.preparation", 600, 601, "owned"));
        for (String method : new String[]{"status", "quit", "lifecycle.transition", "lifecycle.adversary.extra", "LIFECYCLE.adversary", "", null}) {
            assertEquals(600, BridgeServer.timeoutSeconds(method, 600, 3600, "owned"));
        }
    }

    @Test public void rejectsMissingOwnershipWithoutChangingNormalBehavior() {
        assertEquals(240, BridgeServer.timeoutSeconds("lifecycle.adversary", 240, 3600, null));
        assertEquals(240, BridgeServer.timeoutSeconds("lifecycle.preparation", 240, 3600, ""));
    }

    @Test public void rejectsInvalidOverridesWithoutChangingNormalBehavior() {
        for (int value : new int[]{Integer.MIN_VALUE, -1, 0, 120, 600, 3601, Integer.MAX_VALUE}) {
            assertEquals(240, BridgeServer.timeoutSeconds("lifecycle.adversary", 240, value, "owned"));
        }
    }
}
