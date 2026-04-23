package com.starskyxiii.polyglottooltip.name.prebuilt;

import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder.ActiveBuildSnapshot;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder.BuildOwner;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder.BuildPhase;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder.CompletedBuild;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder.BuildResult;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder.PendingBuild;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder.SliceBudget;
import com.starskyxiii.polyglottooltip.report.BuildReportWriter;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;

/**
 * Runs the manual /polyglotbuild flow incrementally on client ticks and reports
 * progress back to chat.
 */
public final class ManualFullNameCacheBuildManager {

    private static final String PREFIX = "[PolyglotTooltips] ";
    private static final SliceBudget INTERACTIVE_SLICE_BUDGET =
        SliceBudget.of(
            96,
            6L * 1000L * 1000L,
            256,
            12L * 1000L * 1000L);
    private static final SliceBudget ACCELERATED_SLICE_BUDGET =
        SliceBudget.of(
            384,
            24L * 1000L * 1000L,
            4096,
            96L * 1000L * 1000L);
    private static final boolean FORCE_TURBO_DURING_GAMEPLAY = true;
    private static final long PROGRESS_CHAT_INTERVAL_MS = 4000L;

    private static final ManualFullNameCacheBuildManager INSTANCE =
        new ManualFullNameCacheBuildManager();

    private PendingBuild pending;
    private CompletedBuild completedBuild;
    private Object startedWorld;
    private boolean finishQueued;
    private long lastProgressChatMs;
    private String lastProgressSignature;

    private ManualFullNameCacheBuildManager() {}

    public static ManualFullNameCacheBuildManager getInstance() {
        return INSTANCE;
    }

    public void startBuild(ICommandSender sender, String scanFilter, boolean mergeWithPreviousCache) {
        if (pending != null || completedBuild != null) {
            chat(sender, EnumChatFormatting.YELLOW,
                PREFIX + "A manual build is already running. Use /polyglotbuild status to check progress.");
            showStatus(sender);
            return;
        }

        ActiveBuildSnapshot activeBuild = FullNameCacheBuilder.getActiveBuildSnapshot();
        if (activeBuild != null) {
            chat(sender, EnumChatFormatting.RED,
                PREFIX + "Cannot start a manual build while a "
                    + activeBuild.owner.getDisplayName() + " build is running.");
            chat(sender, EnumChatFormatting.GRAY,
                "  " + formatProgressLine(activeBuild));
            return;
        }

        try {
            pending = FullNameCacheBuilder.startBuild(
                scanFilter,
                FullNameCacheMetadata.normalizeLanguages(com.starskyxiii.polyglottooltip.config.Config.displayLanguages),
                mergeWithPreviousCache,
                BuildOwner.MANUAL);
            startedWorld = captureWorldIdentity();
            finishQueued = false;
            lastProgressChatMs = 0L;
            lastProgressSignature = null;

            ActiveBuildSnapshot snapshot = pending.snapshot(BuildOwner.MANUAL);
            PolyglotTooltip.LOG.info(
                "[PolyglotTooltips] Manual build queued for chat progress. {}",
                formatProgressLine(snapshot));
            chat(sender, EnumChatFormatting.YELLOW,
                PREFIX + "Started async full name cache build. filter='"
                    + displayFilter(snapshot.filter) + "', languages="
                    + snapshot.targetLanguages + ", merge=" + snapshot.mergeWithPreviousCache);
            chat(sender, EnumChatFormatting.GRAY,
                "  Turbo budget is enabled for manual builds, even during normal gameplay.");
            chat(sender, EnumChatFormatting.GRAY,
                "  Progress will be reported in chat. Use /polyglotbuild status or /polyglotbuild cancel.");
            emitProgress(sender, true);
        } catch (Throwable t) {
            clearState();
            chat(sender, EnumChatFormatting.RED, PREFIX + "Build could not start: " + t.getMessage());
        }
    }

    public void showStatus(ICommandSender sender) {
        if (completedBuild != null) {
            chat(sender, EnumChatFormatting.YELLOW,
                PREFIX + "Manual build is writing reports: " + formatReportPhaseLine(completedBuild));
            return;
        }

        ActiveBuildSnapshot snapshot = FullNameCacheBuilder.getActiveBuildSnapshot();
        if (snapshot == null) {
            chat(sender, EnumChatFormatting.GREEN, PREFIX + "No active full name cache build.");
            return;
        }

        EnumChatFormatting color =
            snapshot.owner == BuildOwner.MANUAL ? EnumChatFormatting.YELLOW : EnumChatFormatting.AQUA;
        chat(sender, color,
            PREFIX + "Active " + snapshot.owner.getDisplayName() + " build: " + formatProgressLine(snapshot));
        chat(sender, EnumChatFormatting.GRAY,
            "  filter='" + displayFilter(snapshot.filter) + "', languages="
                + snapshot.targetLanguages + ", merge=" + snapshot.mergeWithPreviousCache);
    }

    public void cancelBuild(ICommandSender sender) {
        if (completedBuild != null) {
            PolyglotTooltip.LOG.warn(
                "[PolyglotTooltips] Manual build report phase skipped by user request.");
            chat(sender, EnumChatFormatting.YELLOW,
                PREFIX + "Cache build already finished; skipped report generation.");
            clearState();
            return;
        }

        if (pending == null) {
            ActiveBuildSnapshot activeBuild = FullNameCacheBuilder.getActiveBuildSnapshot();
            if (activeBuild != null) {
                chat(sender, EnumChatFormatting.YELLOW,
                    PREFIX + "An " + activeBuild.owner.getDisplayName()
                        + " build is active. This command currently cancels manual builds only.");
                chat(sender, EnumChatFormatting.GRAY,
                    "  " + formatProgressLine(activeBuild));
                return;
            }

            chat(sender, EnumChatFormatting.GREEN, PREFIX + "No active manual build.");
            return;
        }

        try {
            FullNameCacheBuilder.cancelBuild(pending);
            PolyglotTooltip.LOG.info("[PolyglotTooltips] Manual build cancelled by user.");
            chat(sender, EnumChatFormatting.YELLOW, PREFIX + "Manual build cancelled.");
        } finally {
            clearState();
        }
    }

    public boolean hasActiveManualBuild() {
        return pending != null || completedBuild != null;
    }

    private static SliceBudget selectSliceBudget(Minecraft minecraft) {
        if (FORCE_TURBO_DURING_GAMEPLAY) {
            return ACCELERATED_SLICE_BUDGET;
        }

        if (minecraft == null) {
            return INTERACTIVE_SLICE_BUDGET;
        }

        return minecraft.theWorld == null || minecraft.currentScreen != null
            ? ACCELERATED_SLICE_BUDGET
            : INTERACTIVE_SLICE_BUDGET;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != Phase.END || (pending == null && completedBuild == null)) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (pending != null && hasWorldChanged(minecraft)) {
            cancelForSessionChange();
            return;
        }

        if (pending != null && (minecraft == null || minecraft.gameSettings == null)) {
            return;
        }

        boolean finished = false;
        try {
            if (completedBuild != null) {
                long reportMs = FullNameCacheBuilder.writeReports(completedBuild);
                sendCompletion(completedBuild.getResult(), reportMs);
                finished = true;
                return;
            }

            if (!finishQueued) {
                boolean complete = FullNameCacheBuilder.resolveNextSlice(
                    pending,
                    selectSliceBudget(minecraft));
                emitProgress(null, false);
                if (!complete) {
                    return;
                }

                finishQueued = true;
                return;
            }

            completedBuild = FullNameCacheBuilder.finishBuildWithoutReports(pending);
            pending = null;
            startedWorld = null;
            finishQueued = false;
            emitReportPhase(null);
        } catch (Throwable t) {
            PolyglotTooltip.LOG.warn("[PolyglotTooltips] Manual build failed.", t);
            if (pending != null) {
                FullNameCacheBuilder.cancelBuild(pending);
            }
            PolyglotTooltip.LOG.warn(
                "[PolyglotTooltips] Manual build failure announced to chat: {}",
                t.getMessage());
            chat(null, EnumChatFormatting.RED, PREFIX + "Build failed: " + t.getMessage());
            finished = true;
        } finally {
            if (finished) {
                clearState();
            }
        }
    }

    private void cancelForSessionChange() {
        try {
            FullNameCacheBuilder.cancelBuild(pending);
        } finally {
            PolyglotTooltip.LOG.warn(
                "[PolyglotTooltips] Manual build cancelled because the world/session changed.");
            chat(null, EnumChatFormatting.RED,
                PREFIX + "Manual build cancelled because the world/session changed.");
            clearState();
        }
    }

    private void emitProgress(ICommandSender sender, boolean force) {
        if (pending == null) {
            return;
        }

        ActiveBuildSnapshot snapshot = pending.snapshot(BuildOwner.MANUAL);
        String signature = snapshot.phase.name() + "|" + String.valueOf(snapshot.currentLanguage);
        long now = System.currentTimeMillis();
        if (!force
            && signature.equals(lastProgressSignature)
            && now - lastProgressChatMs < PROGRESS_CHAT_INTERVAL_MS) {
            return;
        }

        lastProgressSignature = signature;
        lastProgressChatMs = now;
        PolyglotTooltip.LOG.info(
            "[PolyglotTooltips] Manual build progress: {}",
            formatProgressLine(snapshot));
        chat(sender, EnumChatFormatting.AQUA, PREFIX + formatProgressLine(snapshot));
    }

    private void emitReportPhase(ICommandSender sender) {
        if (completedBuild == null) {
            return;
        }

        String line = formatReportPhaseLine(completedBuild);
        PolyglotTooltip.LOG.info("[PolyglotTooltips] Manual build progress: {}", line);
        chat(sender, EnumChatFormatting.AQUA, PREFIX + line);
    }

    private void sendCompletion(BuildResult result, long reportMs) {
        PolyglotTooltip.LOG.info(
            "[PolyglotTooltips] Manual build completion sent to chat. {}",
            result.toSummaryLine());
        chat(null, EnumChatFormatting.GREEN, PREFIX + "Build complete!");
        chat(null, EnumChatFormatting.WHITE, "  " + result.toSummaryLine());

        for (String lang : result.collectedPerLang.keySet()) {
            int collected = result.collectedPerLang.get(lang);
            int empty = safeGet(result.emptyPerLang, lang);
            int rawKey = safeGet(result.rawKeyPerLang, lang);
            String mode = result.switchModePerLang.containsKey(lang)
                ? result.switchModePerLang.get(lang) : "?";
            long setupMs = result.setupMsPerLang.containsKey(lang)
                ? result.setupMsPerLang.get(lang) : -1L;
            long swMs = result.switchMsPerLang.containsKey(lang)
                ? result.switchMsPerLang.get(lang) : -1L;
            long rsMs = result.resolveMsPerLang.containsKey(lang)
                ? result.resolveMsPerLang.get(lang) : -1L;
            int slices = safeGet(result.resolveSliceCountPerLang, lang);
            int stopItem = safeGet(result.resolveBudgetItemStopsPerLang, lang);
            int stopTime = safeGet(result.resolveBudgetTimeStopsPerLang, lang);
            chat(null, EnumChatFormatting.AQUA,
                String.format(
                    Locale.ROOT,
                    "  %s: collected=%d  empty=%d  rawKey=%d  setup=%dms  resolve=%dms  switch=%dms  slices=%d  stop[item=%d,time=%d]  [%s]",
                    lang, collected, empty, rawKey, setupMs, rsMs, swMs, slices, stopItem, stopTime, mode));
        }

        chat(null, EnumChatFormatting.GRAY,
            String.format(
                Locale.ROOT,
                "  timing: enum=%dms  expand=%dms  write=%dms  report=%dms  expandSlices=%d  liveHints=%d",
                result.enumMs,
                result.expandMs,
                result.writeMs,
                reportMs,
                result.expandSliceCount,
                result.capturedLiveDisplayNames));
        chat(null, EnumChatFormatting.GRAY,
            "  cache  -> " + FullNameCacheIO.getCacheFile().getAbsolutePath());
        chat(null, EnumChatFormatting.GRAY,
            "  report -> " + BuildReportWriter.getReportDir(result.scanFilter).getAbsolutePath());
    }

    private static String formatProgressLine(ActiveBuildSnapshot snapshot) {
        if (snapshot == null) {
            return "No active build.";
        }

        if (snapshot.phase == BuildPhase.EXPANDING) {
            return String.format(
                Locale.ROOT,
                "expanding items %d/%d (%d%%), unique pairs=%d, elapsed=%s",
                snapshot.itemsExpanded,
                snapshot.itemsScanned,
                percent(snapshot.itemsExpanded, snapshot.itemsScanned),
                snapshot.uniqueKeysExpanded,
                formatElapsed(snapshot.elapsedMs));
        }

        if (snapshot.phase == BuildPhase.RESOLVING) {
            return String.format(
                Locale.ROOT,
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
            Locale.ROOT,
            "writing cache output, keys=%d, elapsed=%s",
            snapshot.collectedKeyCount,
            formatElapsed(snapshot.elapsedMs));
    }

    private static String formatReportPhaseLine(CompletedBuild completedBuild) {
        if (completedBuild == null) {
            return "writing build reports";
        }

        BuildResult result = completedBuild.getResult();
        return String.format(
            Locale.ROOT,
            "writing build reports, keys=%d, entries=%d",
            result.uniqueKeys,
            result.totalEntries());
    }

    private static String displayFilter(String filter) {
        if (filter == null || filter.trim().isEmpty() || "all".equalsIgnoreCase(filter.trim())) {
            return "all";
        }

        String normalized = filter.trim();
        return normalized.endsWith(":") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private static String formatElapsed(long elapsedMs) {
        return String.format(Locale.ROOT, "%.1fs", elapsedMs / 1000.0D);
    }

    private static int percent(int current, int total) {
        if (total <= 0) {
            return 100;
        }
        return (int) Math.min(100L, Math.round(current * 100.0D / total));
    }

    private static Object captureWorldIdentity() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft == null ? null : minecraft.theWorld;
    }

    private boolean hasWorldChanged(Minecraft minecraft) {
        if (pending == null || startedWorld == null) {
            return false;
        }
        return minecraft == null || minecraft.theWorld != startedWorld;
    }

    private void clearState() {
        pending = null;
        completedBuild = null;
        startedWorld = null;
        finishQueued = false;
        lastProgressChatMs = 0L;
        lastProgressSignature = null;
    }

    private static void chat(ICommandSender sender, EnumChatFormatting color, String message) {
        ICommandSender target = sender != null ? sender : currentPlayer();
        if (target == null) {
            return;
        }

        ChatComponentText component = new ChatComponentText(message);
        component.getChatStyle().setColor(color);
        target.addChatMessage(component);
    }

    private static ICommandSender currentPlayer() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft == null ? null : minecraft.thePlayer;
    }

    private static int safeGet(Map<String, Integer> map, String key) {
        Integer value = map.get(key);
        return value == null ? 0 : value.intValue();
    }
}
