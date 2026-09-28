package com.starskyxiii.polyglottooltip.integration.jec;

import com.starskyxiii.polyglottooltip.PolyglotTooltip;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

public final class JecAcceleratorCacheGuard {
    private static final AtomicBoolean WARNING_LOGGED = new AtomicBoolean();

    private JecAcceleratorCacheGuard() {
    }

    public static void beforeMatch() {
        Guard guard = MatchContext.GUARD;
        if (guard != null) {
            guard.invalidate();
        }
    }

    static Guard forContext(Object context) throws ReflectiveOperationException {
        Field modification = context.getClass().getDeclaredField("modification");
        Field accelerator = context.getClass().getDeclaredField("acc");
        if (modification.getType() != int.class || accelerator.getType() != ThreadLocal.class) {
            throw new NoSuchFieldException("Unexpected JEC accelerator fields");
        }
        modification.setAccessible(true);
        accelerator.setAccessible(true);
        return new Guard(context, modification, (ThreadLocal<?>) accelerator.get(context));
    }

    private static Guard findMatchContext() {
        try {
            Class<?> match = Class.forName("me.towdium.jecharacters.utils.Match", false,
                    JecAcceleratorCacheGuard.class.getClassLoader());
            return forContext(match.getField("context").get(null));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warnOnce(exception);
            return null;
        }
    }

    private static void warnOnce(Throwable exception) {
        if (WARNING_LOGGED.compareAndSet(false, true)) {
            PolyglotTooltip.LOGGER.warn("Unable to refresh the installed JEC accelerator cache", exception);
        }
    }

    private static final class MatchContext {
        private static final Guard GUARD = findMatchContext();
    }

    static final class Guard {
        private final Object context;
        private final Field modification;
        private final ThreadLocal<?> accelerator;
        private final ThreadLocal<Integer> observedModification = new ThreadLocal<>();

        private Guard(Object context, Field modification, ThreadLocal<?> accelerator) {
            this.context = context;
            this.modification = modification;
            this.accelerator = accelerator;
        }

        void invalidate() {
            try {
                int current = modification.getInt(context);
                Integer observed = observedModification.get();
                if (observed == null || observed.intValue() != current) {
                    accelerator.remove();
                    observedModification.set(current);
                }
            } catch (ReflectiveOperationException | RuntimeException exception) {
                warnOnce(exception);
            }
        }
    }
}
