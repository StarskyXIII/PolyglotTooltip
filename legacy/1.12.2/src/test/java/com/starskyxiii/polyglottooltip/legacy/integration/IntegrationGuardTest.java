package com.starskyxiii.polyglottooltip.legacy.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class IntegrationGuardTest {
    private final List<String> warnings = new ArrayList<>();
    private final IntegrationGuard guard = new IntegrationGuard("jei", (message, error) -> warnings.add(message));

    @Test
    public void staysActiveBelowTheLimit() {
        for (int i = 0; i < IntegrationGuard.FAILURE_LIMIT - 1; i++) {
            guard.run(() -> {
                throw new IllegalStateException("expected");
            });
        }
        assertTrue(guard.active());
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void disablesAtTheLimitAndWarnsOnce() {
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < IntegrationGuard.FAILURE_LIMIT + 5; i++) {
            guard.run(() -> {
                calls.incrementAndGet();
                throw new IllegalStateException("expected");
            });
        }
        assertFalse(guard.active());
        assertEquals(IntegrationGuard.FAILURE_LIMIT, calls.get());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("integrations.jei=false"));
        assertTrue(guard.disabledReason().contains("IllegalStateException"));
    }

    @Test
    public void callReturnsFallbackOnFailureAndAfterDisabling() {
        assertEquals("ok", guard.call(() -> "ok", "fallback"));
        for (int i = 0; i < IntegrationGuard.FAILURE_LIMIT; i++) {
            assertEquals("fallback", guard.call(() -> {
                throw new NoClassDefFoundError("expected");
            }, "fallback"));
        }
        assertEquals("fallback", guard.call(() -> "ok", "fallback"));
    }
}
