package com.starskyxiii.polyglottooltip.integration.jei;

public final class JeiSearchRefreshBridge {
    private static final Runnable NO_OP = () -> {
    };

    private static volatile RuntimeActions runtimeActions = RuntimeActions.NONE;
    private static volatile RefreshActions emiActions = RefreshActions.NONE;

    private JeiSearchRefreshBridge() {
    }

    public static void setRefreshActions(Runnable refreshResults, Runnable rebuildSearchIndex) {
        setRefreshActions(refreshResults, rebuildSearchIndex, false);
    }

    public static void setRefreshActions(Runnable refreshResults, Runnable rebuildSearchIndex, boolean handlesEmi) {
        runtimeActions = new RuntimeActions(refreshResults, rebuildSearchIndex, handlesEmi);
    }

    public static void clearRefreshActions() {
        runtimeActions = RuntimeActions.NONE;
    }

    public static void setEmiRefreshActions(Runnable refreshResults, Runnable rebuildSearchIndex) {
        emiActions = new RefreshActions(refreshResults, rebuildSearchIndex);
    }

    static void clearEmiRefreshActions() {
        emiActions = RefreshActions.NONE;
    }

    public static void refreshResults() {
        RuntimeActions runtime = runtimeActions;
        runtime.refreshResults().run();
        if (!runtime.handlesEmi()) {
            emiActions.refreshResults().run();
        }
    }

    public static void rebuildSearchIndex() {
        RuntimeActions runtime = runtimeActions;
        runtime.rebuildSearchIndex().run();
        if (!runtime.handlesEmi()) {
            emiActions.rebuildSearchIndex().run();
        }
    }

    private record RefreshActions(Runnable refreshResults, Runnable rebuildSearchIndex) {
        private static final RefreshActions NONE = new RefreshActions(NO_OP, NO_OP);
    }

    private record RuntimeActions(Runnable refreshResults, Runnable rebuildSearchIndex, boolean handlesEmi) {
        private static final RuntimeActions NONE = new RuntimeActions(NO_OP, NO_OP, false);
    }
}
