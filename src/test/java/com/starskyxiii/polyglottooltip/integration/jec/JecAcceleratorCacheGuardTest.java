package com.starskyxiii.polyglottooltip.integration.jec;

import me.towdium.pinin.Keyboard;
import me.towdium.pinin.PinIn;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class JecAcceleratorCacheGuardTest {
    @Test
    void refreshesWarmUntransformedLibraryInBothDirections() throws Exception {
        for (Match operation : new Match[]{PinIn::contains, PinIn::begins, PinIn::matches}) {
            PinIn context = new PinIn().config().accelerate(true).commit();
            JecAcceleratorCacheGuard.Guard guard = JecAcceleratorCacheGuard.forContext(context);
            guard.invalidate();
            assertTrue(operation.test(context, "铁锭", "tieding"));
            context.config().keyboard(Keyboard.DAQIAN).commit();
            assertTrue(operation.test(context, "铁锭", "tieding"));
            guard.invalidate();
            assertFalse(operation.test(context, "铁锭", "tieding"));
            context.config().keyboard(Keyboard.QUANPIN).commit();
            assertFalse(operation.test(context, "铁锭", "tieding"));
            guard.invalidate();
            assertTrue(operation.test(context, "铁锭", "tieding"));
        }
    }

    @Test
    void unchangedGenerationKeepsWarmAccelerator() throws Exception {
        PinIn context = new PinIn().config().accelerate(true).commit();
        JecAcceleratorCacheGuard.Guard guard = JecAcceleratorCacheGuard.forContext(context);
        guard.invalidate();
        assertTrue(context.contains("铁锭", "tieding"));
        Field field = PinIn.class.getDeclaredField("acc");
        field.setAccessible(true);
        ThreadLocal<?> cache = (ThreadLocal<?>) field.get(context);
        Object warm = cache.get();
        guard.invalidate();
        assertSame(warm, cache.get());
    }

    @Test
    void independentContextsDoNotShareObservedGeneration() throws Exception {
        PinIn first = new PinIn().config().accelerate(true).commit();
        PinIn second = new PinIn().config().accelerate(true).commit();
        JecAcceleratorCacheGuard.Guard a = JecAcceleratorCacheGuard.forContext(first);
        JecAcceleratorCacheGuard.Guard b = JecAcceleratorCacheGuard.forContext(second);
        a.invalidate(); b.invalidate();
        assertTrue(first.contains("铁锭", "tieding"));
        assertTrue(second.contains("铁锭", "tieding"));
        first.config().keyboard(Keyboard.DAQIAN).commit();
        second.config().keyboard(Keyboard.DAQIAN).commit();
        a.invalidate(); b.invalidate();
        assertFalse(first.contains("铁锭", "tieding"));
        assertFalse(second.contains("铁锭", "tieding"));
    }

    @Test
    void invalidatesOnTheThreadThatOwnsTheCache() throws Exception {
        PinIn context = new PinIn().config().accelerate(true).commit();
        JecAcceleratorCacheGuard.Guard guard = JecAcceleratorCacheGuard.forContext(context);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            assertTrue(worker.submit(() -> {guard.invalidate(); return context.contains("铁锭", "tieding");}).get());
            context.config().keyboard(Keyboard.DAQIAN).commit();
            guard.invalidate();
            assertFalse(worker.submit(() -> {guard.invalidate(); return context.contains("铁锭", "tieding");}).get());
            context.config().keyboard(Keyboard.QUANPIN).commit();
            assertTrue(worker.submit(() -> {guard.invalidate(); return context.contains("铁锭", "tieding");}).get());
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void rejectsUnknownLibraryLayout() {
        assertThrows(NoSuchFieldException.class, () -> JecAcceleratorCacheGuard.forContext(new Object()));
    }

    private interface Match {
        boolean test(PinIn context, String candidate, String query);
    }
}
