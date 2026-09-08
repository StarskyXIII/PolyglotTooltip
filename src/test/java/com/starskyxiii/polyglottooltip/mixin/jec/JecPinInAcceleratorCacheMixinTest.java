package com.starskyxiii.polyglottooltip.mixin.jec;

import me.towdium.pinin.Keyboard;
import me.towdium.pinin.PinIn;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JecPinInAcceleratorCacheMixinTest {
    private static final String CANDIDATE = "橡木";
    private static final String QUERY = "xiangmu";

    @Test
    void acceleratedMatchKeepsThePreviousResultForAnUnchangedQuery() {
        PinIn quanpinFirst = new PinIn().config().accelerate(true).commit();
        assertTrue(quanpinFirst.contains(CANDIDATE, QUERY));
        quanpinFirst.config().keyboard(Keyboard.DAQIAN).commit();
        assertSame(Keyboard.DAQIAN, quanpinFirst.keyboard());
        assertTrue(quanpinFirst.contains(CANDIDATE, QUERY));

        PinIn daqianFirst = new PinIn().config()
                .accelerate(true)
                .keyboard(Keyboard.DAQIAN)
                .commit();
        assertFalse(daqianFirst.contains(CANDIDATE, QUERY));
        daqianFirst.config().keyboard(Keyboard.QUANPIN).commit();
        assertSame(Keyboard.QUANPIN, daqianFirst.keyboard());
        assertFalse(daqianFirst.contains(CANDIDATE, QUERY));
    }

    @Test
    void generationGuardRefreshesTheRealAcceleratorInBothDirections() throws ReflectiveOperationException {
        assertGenerationGuardRefreshes(PinIn::contains);
        assertGenerationGuardRefreshes(PinIn::begins);
        assertGenerationGuardRefreshes(PinIn::matches);
    }

    @Test
    void generationGuardInvalidatesTheThreadThatOwnsTheWarmCache() throws Exception {
        PinIn context = new PinIn().config().accelerate(true).commit();
        ThreadLocal<Integer> observedModification = new ThreadLocal<>();
        ThreadLocal<?> acceleratorCache = readAcceleratorCache(context);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> quanpin = executor.submit(() -> {
                invalidateIfChanged(context, observedModification, acceleratorCache);
                return context.contains(CANDIDATE, QUERY);
            });
            assertTrue(quanpin.get());

            context.config().keyboard(Keyboard.DAQIAN).commit();
            assertSame(Keyboard.DAQIAN, context.keyboard());
            Future<Boolean> daqian = executor.submit(() -> {
                invalidateIfChanged(context, observedModification, acceleratorCache);
                return context.contains(CANDIDATE, QUERY);
            });
            assertFalse(daqian.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private static void assertGenerationGuardRefreshes(MatchOperation operation)
            throws ReflectiveOperationException {
        PinIn context = new PinIn().config().accelerate(true).commit();
        ThreadLocal<Integer> observedModification = new ThreadLocal<>();
        ThreadLocal<?> acceleratorCache = readAcceleratorCache(context);

        invalidateIfChanged(context, observedModification, acceleratorCache);
        assertTrue(operation.test(context, CANDIDATE, QUERY));

        context.config().keyboard(Keyboard.DAQIAN).commit();
        assertSame(Keyboard.DAQIAN, context.keyboard());
        invalidateIfChanged(context, observedModification, acceleratorCache);
        assertFalse(operation.test(context, CANDIDATE, QUERY));

        context.config().keyboard(Keyboard.QUANPIN).commit();
        assertSame(Keyboard.QUANPIN, context.keyboard());
        invalidateIfChanged(context, observedModification, acceleratorCache);
        assertTrue(operation.test(context, CANDIDATE, QUERY));
    }

    private static void invalidateIfChanged(
            PinIn context,
            ThreadLocal<Integer> observedModification,
            ThreadLocal<?> acceleratorCache
    ) throws ReflectiveOperationException {
        Method method = JecPinInAcceleratorCacheMixin.class.getDeclaredMethod(
                "polyglot$invalidateIfChanged",
                ThreadLocal.class,
                int.class,
                ThreadLocal.class
        );
        method.setAccessible(true);
        method.invoke(null, observedModification, readModification(context), acceleratorCache);
    }

    private static ThreadLocal<?> readAcceleratorCache(PinIn context) throws ReflectiveOperationException {
        Field field = PinIn.class.getDeclaredField("acc");
        field.setAccessible(true);
        return (ThreadLocal<?>) field.get(context);
    }

    private static int readModification(PinIn context) throws ReflectiveOperationException {
        Field field = PinIn.class.getDeclaredField("modification");
        field.setAccessible(true);
        return field.getInt(context);
    }

    @FunctionalInterface
    private interface MatchOperation {
        boolean test(PinIn context, String candidate, String query);
    }
}
