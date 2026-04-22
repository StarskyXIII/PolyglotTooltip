package com.starskyxiii.polyglottooltip.name.prebuilt;

import java.util.ArrayList;
import java.util.List;

import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import com.starskyxiii.polyglottooltip.config.Config;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder.BuildResult;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder.PendingBuild;

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
    private static final int MAX_ITEMS_PER_TICK = 96;
    private static final long MAX_BUDGET_NS_PER_TICK = 8L * 1000L * 1000L;

    private enum State { IDLE, WAITING, RUNNING, DONE }

    private State state = State.IDLE;
    private PendingBuild pending;
    private int stableTicks;

    public void onLoadComplete() {
        if (!Config.autoRebuildFullNameCache) {
            if (pending != null) {
                FullNameCacheBuilder.cancelBuild(pending);
                pending = null;
            }
            state = State.DONE;
            stableTicks = 0;
            return;
        }

        BuildPlan plan = determineBuildPlan();
        if (!plan.shouldBuild()) {
            state = State.DONE;
            return;
        }

        try {
            pending = FullNameCacheBuilder.startBuild("all", plan.languagesToBuild, plan.mergeWithExistingCache);
            state = State.WAITING;
            PolyglotTooltip.LOG.info(
                "[PolyglotTooltips] Auto-build queued during load: {} (languages={}, merge={}).",
                plan.reason,
                plan.languagesToBuild,
                plan.mergeWithExistingCache);
        } catch (Throwable t) {
            PolyglotTooltip.LOG.warn(
                "[PolyglotTooltips] Auto-build setup failed during load: {}",
                t.getMessage());
            pending = null;
            state = State.DONE;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != Phase.END) {
            return;
        }

        if (!Config.autoRebuildFullNameCache) {
            if (pending != null) {
                FullNameCacheBuilder.cancelBuild(pending);
                pending = null;
            }
            state = State.DONE;
            stableTicks = 0;
            return;
        }

        if (pending == null) {
            BuildPlan plan = determineBuildPlan();
            if (!plan.shouldBuild()) {
                state = State.DONE;
                stableTicks = 0;
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
                pending = FullNameCacheBuilder.startBuild("all", plan.languagesToBuild, plan.mergeWithExistingCache);
                stableTicks = 0;
                state = State.WAITING;
                PolyglotTooltip.LOG.info(
                    "[PolyglotTooltips] Auto-build started: {} (languages={}, merge={}).",
                    plan.reason,
                    plan.languagesToBuild,
                    plan.mergeWithExistingCache);
            } catch (Throwable t) {
                PolyglotTooltip.LOG.warn(
                    "[PolyglotTooltips] Auto-build start failed: {}",
                    t.getMessage());
                pending = null;
                state = State.DONE;
            }
            return;
        }

        if (!isReady()) {
            state = State.WAITING;
            stableTicks = 0;
            return;
        }

        if (state == State.WAITING && ++stableTicks < REQUIRED_STABLE_TICKS) {
            return;
        }

        stableTicks = 0;
        state = State.RUNNING;
        boolean finished = false;
        try {
            boolean complete = FullNameCacheBuilder.resolveNextSlice(
                pending,
                MAX_ITEMS_PER_TICK,
                MAX_BUDGET_NS_PER_TICK);
            if (!complete) {
                return;
            }

            BuildResult result = FullNameCacheBuilder.finishBuild(pending);
            PolyglotTooltip.LOG.info(
                "[PolyglotTooltips] Auto-build complete. {}",
                result.toSummaryLine());
            finished = true;
        } catch (Throwable t) {
            PolyglotTooltip.LOG.warn(
                "[PolyglotTooltips] Auto-build failed: {}",
                t.getMessage());
            FullNameCacheBuilder.cancelBuild(pending);
            finished = true;
        } finally {
            if (finished) {
                pending = null;
                stableTicks = 0;
                state = State.DONE;
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
