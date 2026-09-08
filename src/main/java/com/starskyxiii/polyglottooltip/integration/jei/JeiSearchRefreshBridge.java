package com.starskyxiii.polyglottooltip.integration.jei;

public final class JeiSearchRefreshBridge {
    private static final Runnable NO_OP = () -> {
    };

    private static volatile Runnable refreshResultsAction = NO_OP;
    private static volatile Runnable rebuildSearchIndexAction = NO_OP;

    private JeiSearchRefreshBridge() {
    }

    public static void setRefreshActions(Runnable refreshResults, Runnable rebuildSearchIndex) {
        refreshResultsAction = refreshResults;
        rebuildSearchIndexAction = rebuildSearchIndex;
    }

    public static void clearRefreshActions() {
        refreshResultsAction = NO_OP;
        rebuildSearchIndexAction = NO_OP;
    }

    public static void refreshResults() {
        refreshResultsAction.run();
    }

    public static void rebuildSearchIndex() {
        rebuildSearchIndexAction.run();
    }
}
