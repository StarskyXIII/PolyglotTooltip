package com.starskyxiii.polyglottooltip.integration.jei;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JeiSearchRefreshBridgeTest {

    @BeforeEach
    void startWithoutRefreshActions() {
        JeiSearchRefreshBridge.clearRefreshActions();
    }

    @AfterEach
    void clearRefreshActions() {
        JeiSearchRefreshBridge.clearRefreshActions();
    }

    @Test
    void callsBeforeRuntimeRegistrationAreNoOps() {
        JeiSearchRefreshBridge.refreshResults();
        JeiSearchRefreshBridge.rebuildSearchIndex();
    }

    @Test
    void dispatchesRefreshAndRebuildIndependently() {
        AtomicInteger refreshCalls = new AtomicInteger();
        AtomicInteger rebuildCalls = new AtomicInteger();
        JeiSearchRefreshBridge.setRefreshActions(refreshCalls::incrementAndGet, rebuildCalls::incrementAndGet);

        JeiSearchRefreshBridge.refreshResults();
        JeiSearchRefreshBridge.refreshResults();
        assertEquals(2, refreshCalls.get());
        assertEquals(0, rebuildCalls.get());

        JeiSearchRefreshBridge.rebuildSearchIndex();
        assertEquals(2, refreshCalls.get());
        assertEquals(1, rebuildCalls.get());
    }

    @Test
    void replacingActionsStopsDispatchToPreviousCallbacks() {
        AtomicInteger previousCalls = new AtomicInteger();
        AtomicInteger replacementRefreshCalls = new AtomicInteger();
        AtomicInteger replacementRebuildCalls = new AtomicInteger();
        JeiSearchRefreshBridge.setRefreshActions(previousCalls::incrementAndGet, previousCalls::incrementAndGet);
        JeiSearchRefreshBridge.setRefreshActions(
                replacementRefreshCalls::incrementAndGet,
                replacementRebuildCalls::incrementAndGet);

        JeiSearchRefreshBridge.refreshResults();
        JeiSearchRefreshBridge.rebuildSearchIndex();

        assertEquals(0, previousCalls.get());
        assertEquals(1, replacementRefreshCalls.get());
        assertEquals(1, replacementRebuildCalls.get());
    }

    @Test
    void clearingActionsDropsRegisteredCallbacks() {
        AtomicInteger calls = new AtomicInteger();
        JeiSearchRefreshBridge.setRefreshActions(calls::incrementAndGet, calls::incrementAndGet);

        JeiSearchRefreshBridge.clearRefreshActions();
        JeiSearchRefreshBridge.refreshResults();
        JeiSearchRefreshBridge.rebuildSearchIndex();

        assertEquals(0, calls.get());
    }
}
