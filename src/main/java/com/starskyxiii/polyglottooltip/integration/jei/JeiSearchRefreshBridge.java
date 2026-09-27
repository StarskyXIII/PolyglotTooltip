package com.starskyxiii.polyglottooltip.integration.jei;

import java.util.Objects;

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

    private static final class RefreshActions {
        private static final RefreshActions NONE = new RefreshActions(NO_OP, NO_OP);

        private final Runnable refreshResults;
        private final Runnable rebuildSearchIndex;

        private RefreshActions(Runnable refreshResults, Runnable rebuildSearchIndex) {
            this.refreshResults = refreshResults;
            this.rebuildSearchIndex = rebuildSearchIndex;
        }

        public Runnable refreshResults() {
            return refreshResults;
        }

        public Runnable rebuildSearchIndex() {
            return rebuildSearchIndex;
        }

        @Override
        public boolean equals(Object value) {
            if (!(value instanceof RefreshActions)) {
                return false;
            }
            RefreshActions other = (RefreshActions) value;
            return Objects.equals(refreshResults, other.refreshResults)
                    && Objects.equals(rebuildSearchIndex, other.rebuildSearchIndex);
        }

        @Override
        public int hashCode() {
            return 31 * (Objects.hashCode(refreshResults)) + Objects.hashCode(rebuildSearchIndex);
        }

        @Override
        public String toString() {
            return "RefreshActions[refreshResults=" + refreshResults + ", rebuildSearchIndex=" + rebuildSearchIndex + "]";
        }
    }

    private static final class RuntimeActions {
        private static final RuntimeActions NONE = new RuntimeActions(NO_OP, NO_OP, false);

        private final Runnable refreshResults;
        private final Runnable rebuildSearchIndex;
        private final boolean handlesEmi;

        private RuntimeActions(Runnable refreshResults, Runnable rebuildSearchIndex, boolean handlesEmi) {
            this.refreshResults = refreshResults;
            this.rebuildSearchIndex = rebuildSearchIndex;
            this.handlesEmi = handlesEmi;
        }

        public Runnable refreshResults() {
            return refreshResults;
        }

        public Runnable rebuildSearchIndex() {
            return rebuildSearchIndex;
        }

        public boolean handlesEmi() {
            return handlesEmi;
        }

        @Override
        public boolean equals(Object value) {
            if (!(value instanceof RuntimeActions)) {
                return false;
            }
            RuntimeActions other = (RuntimeActions) value;
            return Objects.equals(refreshResults, other.refreshResults)
                    && Objects.equals(rebuildSearchIndex, other.rebuildSearchIndex)
                    && handlesEmi == other.handlesEmi;
        }

        @Override
        public int hashCode() {
            return 31 * (31 * (Objects.hashCode(refreshResults)) + Objects.hashCode(rebuildSearchIndex)) + Boolean.hashCode(handlesEmi);
        }

        @Override
        public String toString() {
            return "RuntimeActions[refreshResults=" + refreshResults + ", rebuildSearchIndex=" + rebuildSearchIndex + ", handlesEmi=" + handlesEmi + "]";
        }
    }
}
