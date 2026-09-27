package com.starskyxiii.polyglottooltip.integration.jei;

public final class EmiSearchRefreshScope {
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

    private EmiSearchRefreshScope() {
    }

    public static boolean isActive() {
        return DEPTH.get() != null;
    }

    public static void run(RefreshAction action) throws ReflectiveOperationException {
        Integer previous = DEPTH.get();
        DEPTH.set(previous == null ? 1 : previous + 1);
        try {
            action.run();
        } finally {
            if (previous == null) {
                DEPTH.remove();
            } else {
                DEPTH.set(previous);
            }
        }
    }

    @FunctionalInterface
    public interface RefreshAction {
        void run() throws ReflectiveOperationException;
    }
}
