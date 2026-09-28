package com.starskyxiii.polyglottooltip.integration.rs2;

//? if >1.20.1 {
import com.google.common.base.Supplier;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Rs2TooltipCacheTest {
    @Test
    void reusesValuesUntilLanguagesChange() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<Integer> cache = Rs2TooltipCache.memoize(calls::incrementAndGet);
        assertEquals(1, cache.get());
        assertEquals(1, cache.get());
        Rs2TooltipCache.invalidate();
        assertEquals(2, cache.get());
        assertEquals(2, cache.get());
    }

    @Test
    void cachesNullValues() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<Object> cache = Rs2TooltipCache.memoize(() -> { calls.incrementAndGet(); return null; });
        assertNull(cache.get());
        assertNull(cache.get());
        assertEquals(1, calls.get());
        Rs2TooltipCache.invalidate();
        assertNull(cache.get());
        assertEquals(2, calls.get());
    }

    @Test
    void failedRecomputationIsRetried() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<Integer> cache = Rs2TooltipCache.memoize(() -> {
            int call = calls.incrementAndGet();
            if (call == 2) throw new IllegalStateException();
            return call;
        });
        assertEquals(1, cache.get());
        Rs2TooltipCache.invalidate();
        assertThrows(IllegalStateException.class, cache::get);
        assertEquals(3, cache.get());
        assertEquals(3, cache.get());
    }

    @Test
    void invalidationDuringComputationRequiresAnotherRead() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<Integer> cache = Rs2TooltipCache.memoize(() -> {
            int call = calls.incrementAndGet();
            if (call == 1) Rs2TooltipCache.invalidate();
            return call;
        });
        assertEquals(1, cache.get());
        assertEquals(2, cache.get());
        assertEquals(2, cache.get());
    }
}
//?}
