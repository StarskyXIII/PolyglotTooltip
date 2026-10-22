package com.starskyxiii.polyglottooltip.legacy.integration;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;

import com.starskyxiii.polyglottooltip.legacy.PolyglotTooltipMod;

public final class IntegrationGuard {
    public static final int FAILURE_LIMIT = 20;

    private final String id;
    private final BiConsumer<String, Throwable> warn;
    private final AtomicInteger failures = new AtomicInteger();
    private volatile String disabledReason;

    public IntegrationGuard(String id) {
        this(id, LogManager.getLogger(PolyglotTooltipMod.MOD_ID)::warn);
    }

    public IntegrationGuard(String id, BiConsumer<String, Throwable> warn) {
        this.id = id;
        this.warn = warn;
    }

    public String id() {
        return id;
    }

    public boolean active() {
        return disabledReason == null;
    }

    public String disabledReason() {
        return disabledReason;
    }

    public int failures() {
        return failures.get();
    }

    public void run(Runnable action) {
        if (!active()) {
            return;
        }
        try {
            action.run();
        } catch (RuntimeException | LinkageError error) {
            fail(error);
        }
    }

    public <T> T call(Supplier<T> action, T fallback) {
        if (!active()) {
            return fallback;
        }
        try {
            return action.get();
        } catch (RuntimeException | LinkageError error) {
            fail(error);
            return fallback;
        }
    }

    private void fail(Throwable error) {
        if (failures.incrementAndGet() != FAILURE_LIMIT) {
            return;
        }
        disabledReason = FAILURE_LIMIT + " errors, last " + error;
        warn.accept("Disabled the " + id + " integration after " + FAILURE_LIMIT + " errors; the last one is attached."
                + " It stays off until the game restarts. Set integrations." + id
                + "=false in config/polyglottooltip.cfg to keep it off.", error);
    }
}
