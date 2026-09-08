package com.starskyxiii.polyglottooltip.integration.jec;

import com.starskyxiii.polyglottooltip.PolyglotTooltip;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

public final class JecMatchBridge {
    private static final AtomicBoolean WARNING_LOGGED = new AtomicBoolean();
    private static final Method CONTAINS = findContains();

    private JecMatchBridge() {
    }

    public static Result contains(String candidate, CharSequence query) {
        if (CONTAINS == null) {
            return Result.UNAVAILABLE;
        }
        try {
            return (boolean) CONTAINS.invoke(null, candidate, query) ? Result.MATCH : Result.NO_MATCH;
        } catch (IllegalAccessException | InvocationTargetException | ClassCastException |
                 LinkageError | SecurityException exception) {
            warnOnce("Unable to invoke the installed JEC search matcher", exception);
            return Result.INVOCATION_FAILED;
        }
    }

    public enum Result {
        MATCH,
        NO_MATCH,
        UNAVAILABLE,
        INVOCATION_FAILED
    }

    private static Method findContains() {
        try {
            Class<?> matchClass = Class.forName(
                    "me.towdium.jecharacters.utils.Match",
                    false,
                    JecMatchBridge.class.getClassLoader()
            );
            return matchClass.getMethod("contains", String.class, CharSequence.class);
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (NoSuchMethodException | LinkageError | SecurityException exception) {
            warnOnce("The installed JEC search matcher is incompatible", exception);
            return null;
        }
    }

    private static void warnOnce(String message, Throwable exception) {
        if (WARNING_LOGGED.compareAndSet(false, true)) {
            PolyglotTooltip.LOGGER.warn(message, exception);
        }
    }
}
