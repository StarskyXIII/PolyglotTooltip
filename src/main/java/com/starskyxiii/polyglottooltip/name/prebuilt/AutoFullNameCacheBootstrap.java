package com.starskyxiii.polyglottooltip.name.prebuilt;

import java.util.ArrayList;
import java.util.List;

import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import com.starskyxiii.polyglottooltip.config.Config;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder.ActiveBuildSnapshot;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder.BuildOwner;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder.BuildPhase;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder.CompletedBuild;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder.BuildResult;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder.PendingBuild;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder.SliceBudget;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder.SliceTelemetry;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import net.minecraft.client.Minecraft;

/**
 * Automatically supplements the persistent full-name cache when it is empty or
 * missing one of the currently configured display languages.
 *
 * <p>Work is budgeted across ticks so the first build no longer resolves an
 * entire language in a single frame.
 */
public final class AutoFullNameCacheBootstrap {

    private static final int REQUIRED_STABLE_TICKS = 5;
    private static final SliceBudget AUTO_TURBO_SLICE_BUDGET =
        SliceBudget.of(
            256,
            20L * 1000L * 1000L,
            2048,
            48L * 1000L * 1000L);
    private static final long PROGRESS_LOG_INTERVAL_MS = 5000L;

    private enum State { IDLE, WAITING, RUNNING, DONE }

    private State state = State.IDLE;
    private PendingBuild pending;
    private CompletedBuild completedBuild;
    private boolean finishQueued;
    private boolean autoRetrySuppressed;
    private boolean sliceWorkObserved;
    private int stableTicks;
    private long lastProgressLogMs;

    public void onLoadComplete() {
        if (!Config.autoRebuildFullNameCache) {
            cancelPendingBuildSafely("Auto-build cleanup failed while disabling auto rebuild during load.");
            completedBuild = null;
            resetState(true);
            return;
        }

        if (autoRetrySuppressed) {
            cancelPendingBuildSafely("Auto-build suppression cleanup failed during load.");
            completedBuild = null;
            resetState(false);
            PolyglotTooltip.LOG.info(
                "[PolyglotTooltips] Auto-build retry remains suppressed for this session; skipping load-triggered retry.");
            return;
        }

        BuildPlan plan = determineBuildPlan();
        if (!plan.shouldBuild()) {
            resetState(true);
            return;
        }

        try {
            if (FullNameCacheBuilder.hasActiveBuild()) {
                state = State.WAITING;
                stableTicks = 0;
                return;
            }

            pending = FullNameCacheBuilder.startBuild(
                "all",
                plan.languagesToBuild,
                plan.mergeWithExistingCache,
                BuildOwner.AUTO);
            finishQueued = false;
            sliceWorkObserved = false;
            state = State.WAITING;
            PolyglotTooltip.LOG.info(
                "[PolyglotTooltips] Auto-build queued during load: {} (languages={}, merge={}).",
                plan.reason,
                plan.languagesToBuild,
                plan.mergeWithExistingCache);
        } catch (Throwable t) {
            PolyglotTooltip.LOG.warn(
                "[PolyglotTooltips] Auto-build setup failed during load; suppressing auto retries for this session.",
                t);
            pending = null;
            completedBuild = null;
            state = State.DONE;
            finishQueued = false;
            autoRetrySuppressed = true;
            sliceWorkObserved = false;
            stableTicks = 0;
            lastProgressLogMs = 0L;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != Phase.END) {
            return;
        }

        if (!Config.autoRebuildFullNameCache) {
            cancelPendingBuildSafely("Auto-build cleanup failed while auto rebuild was disabled.");
            if (completedBuild != null) {
                PolyglotTooltip.LOG.info(
                    "[PolyglotTooltips] Auto-build report phase skipped because auto rebuild was disabled.");
                completedBuild = null;
            }
            resetState(true);
            return;
        }

        if (completedBuild != null) {
            state = State.RUNNING;
            try {
                long reportMs = FullNameCacheBuilder.writeReports(completedBuild);
                BuildResult result = completedBuild.getResult();
                PolyglotTooltip.LOG.info(
                    "[PolyglotTooltips] Auto-build complete. {}  report={}ms",
                    result.toSummaryLine(),
                    reportMs);
            } finally {
                pending = null;
                completedBuild = null;
                resetState(true);
            }
            return;
        }

        if (pending == null) {
            if (autoRetrySuppressed) {
                resetState(false);
                return;
            }

            BuildPlan plan = determineBuildPlan();
            if (!plan.shouldBuild()) {
                resetState(true);
                return;
            }

            if (!isReady()) {
                state = State.WAITING;
                stableTicks = 0;
                return;
            }

            if (++stableTicks < REQUIRED_STABLE_TICKS) {
                state = State.WAITING;
                return;
            }

            try {
                if (FullNameCacheBuilder.hasActiveBuild()) {
                    state = State.WAITING;
                    stableTicks = 0;
                    return;
                }

                pending = FullNameCacheBuilder.startBuild(
                    "all",
                    plan.languagesToBuild,
                    plan.mergeWithExistingCache,
                    BuildOwner.AUTO);
                finishQueued = false;
                sliceWorkObserved = false;
                stableTicks = 0;
                lastProgressLogMs = 0L;
                state = State.WAITING;
                PolyglotTooltip.LOG.info(
                    "[PolyglotTooltips] Auto-build started: {} (languages={}, merge={}).",
                    plan.reason,
                    plan.languagesToBuild,
                    plan.mergeWithExistingCache);
            } catch (Throwable t) {
                PolyglotTooltip.LOG.warn(
                    "[PolyglotTooltips] Auto-build start failed; suppressing auto retries for this session.",
                    t);
                pending = null;
                finishQueued = false;
                autoRetrySuppressed = true;
                state = State.DONE;
                sliceWorkObserved = false;
                stableTicks = 0;
                lastProgressLogMs = 0L;
            }
            return;
        }

        if (!isReady()) {
            state = State.WAITING;
            stableTicks = 0;
            return;
        }

        if (!sliceWorkObserved && state == State.WAITING && ++stableTicks < REQUIRED_STABLE_TICKS) {
            return;
        }

        stableTicks = 0;
        state = State.RUNNING;
        boolean finished = false;
        try {
            if (!finishQueued) {
                boolean complete = FullNameCacheBuilder.resolveNextSlice(
                    pending,
                    AUTO_TURBO_SLICE_BUDGET);
                sliceWorkObserved = true;
                emitProgressIfNeeded();
                if (!complete) {
                    return;
                }

                finishQueued = true;
                return;
            }

            completedBuild = FullNameCacheBuilder.finishBuildWithoutReports(pending);
            pending = null;
            finishQueued = false;
            BuildResult result = completedBuild.getResult();
            PolyglotTooltip.LOG.info(
                "[PolyglotTooltips] Auto-build cache phase complete. {}",
                result.toSummaryLine());
        } catch (Throwable t) {
            PolyglotTooltip.LOG.warn(
                "[PolyglotTooltips] Auto-build failed; suppressing auto retries for this session.",
                t);
            autoRetrySuppressed = true;
            finished = true;
            cancelPendingBuildSafely("Auto-build cleanup failed after an auto-build error.");
        } finally {
            if (finished) {
                pending = null;
                completedBuild = null;
                resetState(false);
            }
        }
    }

    private static BuildPlan determineBuildPlan() {
        List<String> configuredLanguages = FullNameCacheMetadata.normalizeLanguages(Config.displayLanguages);
        if (configuredLanguages.isEmpty()) {
            return BuildPlan.none();
        }

        FullNameCacheMetadata metadata = FullNameCache.snapshotMetadata();
        if (FullNameCache.isEmpty()) {
            if (metadata != null && !metadata.getLanguages().isEmpty()) {
                return BuildPlan.none();
            }
            return BuildPlan.full(configuredLanguages, "cache empty");
        }

        List<String> missingLanguages;
        if (metadata != null) {
            missingLanguages = metadata.findMissingLanguages(configuredLanguages);
        } else {
            missingLanguages = findMissingLanguages(configuredLanguages, FullNameCache.getAvailableLanguages());
        }

        if (missingLanguages.isEmpty()) {
            return BuildPlan.none();
        }

        return BuildPlan.supplement(
            missingLanguages,
            "missing configured languages " + missingLanguages);
    }

    private static List<String> findMissingLanguages(List<String> configuredLanguages, List<String> availableLanguages) {
        List<String> missing = new ArrayList<String>();
        List<String> normalizedAvailable = FullNameCacheMetadata.normalizeLanguages(availableLanguages);
        for (int i = 0; i < configuredLanguages.size(); i++) {
            String configured = configuredLanguages.get(i);
            boolean found = false;
            for (int j = 0; j < normalizedAvailable.size(); j++) {
                if (configured.equalsIgnoreCase(normalizedAvailable.get(j))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                missing.add(configured);
            }
        }
        return missing;
    }

    private static boolean isReady() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) {
            return false;
        }
        return mc.currentScreen != null || mc.theWorld != null;
    }

    private void emitProgressIfNeeded() {
        if (pending == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastProgressLogMs < PROGRESS_LOG_INTERVAL_MS) {
            return;
        }

        lastProgressLogMs = now;
        ActiveBuildSnapshot snapshot = pending.snapshot(BuildOwner.AUTO);
        SliceTelemetry telemetry = pending.getLastSliceTelemetry();
        PolyglotTooltip.LOG.info(
            "[PolyglotTooltips] Auto-build progress [TURBO slice={}ms processed={}]: {}",
            telemetry == null ? 0L : telemetry.wallMs,
            telemetry == null ? 0 : telemetry.processed,
            formatProgressLine(snapshot));
    }

    private void cancelPendingBuildSafely(String failureMessage) {
        PendingBuild buildToCancel = pending;
        if (buildToCancel == null) {
            return;
        }

        try {
            FullNameCacheBuilder.cancelBuild(buildToCancel);
        } catch (Throwable t) {
            PolyglotTooltip.LOG.warn(
                "[PolyglotTooltips] {}",
                failureMessage,
                t);
        } finally {
            pending = null;
        }
    }

    private void resetState(boolean clearRetrySuppression) {
        pending = null;
        completedBuild = null;
        state = State.DONE;
        finishQueued = false;
        sliceWorkObserved = false;
        stableTicks = 0;
        lastProgressLogMs = 0L;
        if (clearRetrySuppression) {
            autoRetrySuppressed = false;
        }
    }

    private static String formatProgressLine(ActiveBuildSnapshot snapshot) {
        if (snapshot == null) {
            return "no active build";
        }

        if (snapshot.phase == BuildPhase.EXPANDING) {
            return String.format(
                java.util.Locale.ROOT,
                "expanding items %d/%d (%d%%), unique pairs=%d, elapsed=%s",
                snapshot.itemsExpanded,
                snapshot.itemsScanned,
                percent(snapshot.itemsExpanded, snapshot.itemsScanned),
                snapshot.uniqueKeysExpanded,
                formatElapsed(snapshot.elapsedMs));
        }

        if (snapshot.phase == BuildPhase.RESOLVING) {
            return String.format(
                java.util.Locale.ROOT,
                "resolving %s (%d/%d) %d/%d entries (%d%%), keys=%d, elapsed=%s",
                snapshot.currentLanguage == null ? "?" : snapshot.currentLanguage,
                snapshot.currentLanguageOrdinal,
                snapshot.totalLanguages,
                snapshot.currentLanguageEntriesResolved,
                snapshot.currentLanguageEntriesTotal,
                percent(snapshot.currentLanguageEntriesResolved, snapshot.currentLanguageEntriesTotal),
                snapshot.collectedKeyCount,
                formatElapsed(snapshot.elapsedMs));
        }

        return String.format(
            java.util.Locale.ROOT,
            "writing cache output, keys=%d, elapsed=%s",
            snapshot.collectedKeyCount,
            formatElapsed(snapshot.elapsedMs));
    }

    private static int percent(int current, int total) {
        if (total <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(100, (int) ((current * 100L) / total)));
    }

    private static String formatElapsed(long elapsedMs) {
        long totalSeconds = Math.max(0L, elapsedMs / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return minutes > 0L
            ? String.format(java.util.Locale.ROOT, "%dm%02ds", minutes, seconds)
            : String.format(java.util.Locale.ROOT, "%ds", seconds);
    }

    private static final class BuildPlan {

        private static final BuildPlan NONE =
            new BuildPlan(false, new ArrayList<String>(), false, "");

        private final boolean shouldBuild;
        private final List<String> languagesToBuild;
        private final boolean mergeWithExistingCache;
        private final String reason;

        private BuildPlan(boolean shouldBuild, List<String> languagesToBuild,
                boolean mergeWithExistingCache, String reason) {
            this.shouldBuild = shouldBuild;
            this.languagesToBuild = languagesToBuild;
            this.mergeWithExistingCache = mergeWithExistingCache;
            this.reason = reason == null ? "" : reason;
        }

        private static BuildPlan none() {
            return NONE;
        }

        private static BuildPlan full(List<String> languagesToBuild, String reason) {
            return new BuildPlan(
                true,
                new ArrayList<String>(FullNameCacheMetadata.normalizeLanguages(languagesToBuild)),
                false,
                reason);
        }

        private static BuildPlan supplement(List<String> languagesToBuild, String reason) {
            return new BuildPlan(
                true,
                new ArrayList<String>(FullNameCacheMetadata.normalizeLanguages(languagesToBuild)),
                true,
                reason);
        }

        private boolean shouldBuild() {
            return shouldBuild && !languagesToBuild.isEmpty();
        }
    }
}
