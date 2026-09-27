package com.starskyxiii.polyglottooltip.integration.jei;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmiSearchRefreshScopeTest {
    @Test
    void nestedRefreshRetainsTheOuterScopeAndCleansUpAfterSuccess() throws Exception {
        assertFalse(EmiSearchRefreshScope.isActive());
        EmiSearchRefreshScope.run(() -> {
            assertTrue(EmiSearchRefreshScope.isActive());
            EmiSearchRefreshScope.run(() -> assertTrue(EmiSearchRefreshScope.isActive()));
            assertTrue(EmiSearchRefreshScope.isActive());
        });
        assertFalse(EmiSearchRefreshScope.isActive());
    }

    @Test
    void reflectiveFailureRestoresBothNestedAndOuterScopes() throws Exception {
        NoSuchMethodException failure = new NoSuchMethodException("bake");
        EmiSearchRefreshScope.run(() -> {
            assertSame(failure, assertThrows(NoSuchMethodException.class,
                    () -> EmiSearchRefreshScope.run(() -> { throw failure; })));
            assertTrue(EmiSearchRefreshScope.isActive());
        });
        assertFalse(EmiSearchRefreshScope.isActive());
        assertSame(failure, assertThrows(NoSuchMethodException.class,
                () -> EmiSearchRefreshScope.run(() -> { throw failure; })));
        assertFalse(EmiSearchRefreshScope.isActive());
    }

    @Test
    void runtimeFailureDoesNotLeaveCancellationDisabled() {
        IllegalStateException failure = new IllegalStateException("bake failed");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> EmiSearchRefreshScope.run(() -> { throw failure; })));
        assertFalse(EmiSearchRefreshScope.isActive());
    }

    @Test
    void otherThreadsKeepNativeCancellationDuringAProductRefresh() throws Exception {
        EmiSearchRefreshScope.run(() -> assertFalse(
                CompletableFuture.supplyAsync(EmiSearchRefreshScope::isActive).join()));
        assertFalse(EmiSearchRefreshScope.isActive());
    }
}
