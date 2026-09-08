package com.starskyxiii.polyglottooltip.integration.jei;

import org.junit.jupiter.api.Test;

import static com.starskyxiii.polyglottooltip.integration.jei.OptionalEmiSearchRefresh.RefreshKind.NONE;
import static com.starskyxiii.polyglottooltip.integration.jei.OptionalEmiSearchRefresh.RefreshKind.REBUILD;
import static com.starskyxiii.polyglottooltip.integration.jei.OptionalEmiSearchRefresh.RefreshKind.SEARCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionalEmiSearchRefreshTest {

    @Test
    void duplicateRegistrationPreservesIdentityAndQueuedRequest() {
        OptionalEmiSearchRefresh.PendingRefreshState state =
                new OptionalEmiSearchRefresh.PendingRefreshState();
        Object registration = state.registerIfAbsent();
        assertTrue(state.request(registration, SEARCH));

        assertSame(registration, state.registerIfAbsent());
        assertEquals(SEARCH, state.claim(registration));
    }

    @Test
    void rebuildDominatesSearchInEitherRequestOrder() {
        OptionalEmiSearchRefresh.PendingRefreshState state =
                new OptionalEmiSearchRefresh.PendingRefreshState();
        Object registration = state.registerIfAbsent();
        state.request(registration, SEARCH);
        state.request(registration, REBUILD);
        assertEquals(REBUILD, state.claim(registration));

        state.request(registration, REBUILD);
        state.request(registration, SEARCH);
        assertEquals(REBUILD, state.claim(registration));
    }

    @Test
    void completedRequestCannotBeRestoredByALaterReadinessFailure() {
        OptionalEmiSearchRefresh.PendingRefreshState state =
                new OptionalEmiSearchRefresh.PendingRefreshState();
        Object registration = state.registerIfAbsent();
        state.request(registration, SEARCH);
        assertEquals(SEARCH, state.claim(registration));
        state.complete(registration);

        state.restore(registration);

        assertFalse(state.hasPending(registration));
        assertEquals(NONE, state.claim(registration));
    }

    @Test
    void readinessRaceRestoreKeepsClaimedRebuildOverNewSearch() {
        OptionalEmiSearchRefresh.PendingRefreshState state =
                new OptionalEmiSearchRefresh.PendingRefreshState();
        Object registration = state.registerIfAbsent();
        state.request(registration, REBUILD);
        assertEquals(REBUILD, state.claim(registration));
        state.request(registration, SEARCH);

        state.restore(registration);

        assertEquals(REBUILD, state.claim(registration));
    }
}
