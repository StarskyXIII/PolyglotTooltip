package com.starskyxiii.polyglottooltip.name.prebuilt;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import com.starskyxiii.polyglottooltip.Tags;
import com.starskyxiii.polyglottooltip.config.Config;
import com.starskyxiii.polyglottooltip.i18n.ProgrammaticDisplayNameLookup;
import com.starskyxiii.polyglottooltip.i18n.ProgrammaticDisplayNameLookup.LiveDisplayNameHintScope;
import com.starskyxiii.polyglottooltip.i18n.ProgrammaticDisplayNameLookup.RestoreSnapshot;
import com.starskyxiii.polyglottooltip.i18n.ProgrammaticDisplayNameLookup.TranslationScope;
import com.starskyxiii.polyglottooltip.i18n.LanguageSwitcher;
import com.starskyxiii.polyglottooltip.i18n.LanguageSwitcher.SwitchResult;
import com.starskyxiii.polyglottooltip.name.DisplayNameResolver;
import com.starskyxiii.polyglottooltip.report.BuildReportWriter;
import com.starskyxiii.polyglottooltip.report.NameQuality;
import com.starskyxiii.polyglottooltip.report.NameQualityClassifier;

/**
 * Builds the full-registry name cache by enumerating items, expanding sub-item variants,
 * resolving names for each target language, then writing the merged results to disk.
 *
 * <p>The builder now supports both blocking and budgeted execution:
 * <ul>
 *   <li>Blocking builds remain available for direct callers and verification.</li>
 *   <li>Budgeted builds are used by auto-bootstrap and the manual command manager
 *       so the work can be sliced across ticks.</li>
 * </ul>
 */
public final class FullNameCacheBuilder {

    private static final Object ACTIVE_BUILD_LOCK = new Object();
    private static final Method ITEM_GET_CREATIVE_TABS_METHOD = resolveItemGetCreativeTabsMethod();
    private static final long EXPAND_SOFT_TARGET_NANOS = 24L * 1000L * 1000L;
    private static final long RESOLVE_SOFT_TARGET_NANOS = 48L * 1000L * 1000L;
    private static final int MIN_EXPAND_CHECKPOINT_ITEMS = 16;
    private static final int MAX_EXPAND_CHECKPOINT_ITEMS = 64;
    private static final int MIN_RESOLVE_CHECKPOINT_ITEMS = 64;
    private static final int MAX_RESOLVE_CHECKPOINT_ITEMS = 256;
    private static ActiveBuildState activeBuild;

    private FullNameCacheBuilder() {}

    public enum BuildOwner {
        AUTO("auto"),
        MANUAL("manual");

        private final String displayName;

        BuildOwner(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum BuildPhase {
        EXPANDING,
        RESOLVING,
        WRITING
    }

    public static final class SliceBudget {

        public final int expansionMaxItems;
        public final long expansionMaxNanos;
        public final int resolveMaxItems;
        public final long resolveMaxNanos;

        private SliceBudget(int expansionMaxItems, long expansionMaxNanos,
                int resolveMaxItems, long resolveMaxNanos) {
            this.expansionMaxItems = expansionMaxItems <= 0 ? Integer.MAX_VALUE : expansionMaxItems;
            this.expansionMaxNanos = expansionMaxNanos <= 0L ? Long.MAX_VALUE : expansionMaxNanos;
            this.resolveMaxItems = resolveMaxItems <= 0 ? Integer.MAX_VALUE : resolveMaxItems;
            this.resolveMaxNanos = resolveMaxNanos <= 0L ? Long.MAX_VALUE : resolveMaxNanos;
        }

        public static SliceBudget uniform(int maxItems, long maxNanos) {
            return new SliceBudget(maxItems, maxNanos, maxItems, maxNanos);
        }

        public static SliceBudget of(int expansionMaxItems, long expansionMaxNanos,
                int resolveMaxItems, long resolveMaxNanos) {
            return new SliceBudget(
                expansionMaxItems,
                expansionMaxNanos,
                resolveMaxItems,
                resolveMaxNanos);
        }
    }

    // =========================================================================
    // Blocking entry points
    // =========================================================================

    public static BuildResult build(String scanFilter) throws Exception {
        return build(scanFilter, Config.displayLanguages, shouldMergeWithPrevious(scanFilter));
    }

    public static BuildResult build(String scanFilter, List<String> targetLanguages,
            boolean mergeWithPreviousCache) throws Exception {
        PendingBuild pending = startBuild(
            scanFilter,
            targetLanguages,
            mergeWithPreviousCache,
            BuildOwner.MANUAL);
        try {
            ensureExpanded(pending);
            for (String lang : pending.targetLanguages) {
                resolveLanguage(pending, lang);
            }
            return finishBuild(pending);
        } catch (Exception e) {
            cancelBuild(pending);
            throw e;
        }
    }

    // =========================================================================
    // Budgeted entry points
    // =========================================================================

    public static PendingBuild startBuild(String scanFilter) {
        return startBuild(
            scanFilter,
            Config.displayLanguages,
            shouldMergeWithPrevious(scanFilter),
            BuildOwner.MANUAL);
    }

    public static PendingBuild startBuild(String scanFilter, List<String> targetLanguages,
            boolean mergeWithPreviousCache) {
        return startBuild(scanFilter, targetLanguages, mergeWithPreviousCache, BuildOwner.MANUAL);
    }

    public static PendingBuild startBuild(String scanFilter, List<String> targetLanguages,
            boolean mergeWithPreviousCache, BuildOwner owner) {
        String filter = normalizeFilter(scanFilter);
        List<String> normalizedLanguages = normalizeLanguages(targetLanguages);

        PolyglotTooltip.LOG.info(
            "[PolyglotTooltips] {} full name cache build starting. filter='{}', languages={}, displayNameFallback=BACKGROUND, liveSwitchConfig={}, merge={}",
            owner == null ? BuildOwner.MANUAL.getDisplayName() : owner.getDisplayName(),
            filter.isEmpty() ? "all" : filter,
            normalizedLanguages,
            Config.useFastLanguageSwitch,
            mergeWithPreviousCache);

        long startMs = System.currentTimeMillis();

        long enumStart = System.currentTimeMillis();
        List<Item> items = collectItems(filter);
        long enumMs = System.currentTimeMillis() - enumStart;
        PolyglotTooltip.LOG.info("[PolyglotTooltips] Items matching filter: {}  ({}ms)", items.size(), enumMs);

        Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> previousCache = FullNameCache.snapshot();
        FullNameCacheMetadata previousMetadata = FullNameCache.snapshotMetadata();

        PendingBuild pending = new PendingBuild(
            owner == null ? BuildOwner.MANUAL : owner,
            items,
            items.size(),
            enumMs,
            startMs,
            filter.isEmpty() ? "all" : filter,
            normalizedLanguages,
            mergeWithPreviousCache,
            previousCache,
            previousMetadata);

        claimActiveBuild(owner == null ? BuildOwner.MANUAL : owner, pending);

        try {
            // Bypass the prebuilt fast path while the builder is collecting fresh names.
            FullNameCache.replace(
                new LinkedHashMap<PrebuiltSecondaryNameIndexKey, Map<String, String>>(),
                previousMetadata);
            return pending;
        } catch (RuntimeException e) {
            releaseActiveBuild(pending);
            throw e;
        }
    }

    /**
     * Advances the build within the provided budget.
     *
     * @return true when the entire build has finished resolving every target language
     */
    public static boolean resolveNextSlice(PendingBuild pending, int maxItems, long maxNanos) {
        return resolveNextSlice(pending, SliceBudget.uniform(maxItems, maxNanos));
    }

    public static boolean resolveNextSlice(PendingBuild pending, SliceBudget budget) {
        if (pending == null) {
            return true;
        }
        ensureActiveBuild(pending);

        SliceBudget effectiveBudget = budget == null
            ? SliceBudget.uniform(Integer.MAX_VALUE, Long.MAX_VALUE)
            : budget;

        if (!pending.isExpansionComplete()) {
            expandNextSlice(
                pending,
                effectiveBudget.expansionMaxItems,
                effectiveBudget.expansionMaxNanos);
            return false;
        }

        if (pending.targetLanguages.isEmpty()) {
            return true;
        }

        if (pending.currentLanguageIndex >= pending.targetLanguages.size()) {
            return true;
        }

        String lang = pending.targetLanguages.get(pending.currentLanguageIndex);
        if (pending.activeLanguage == null) {
            beginLanguage(pending, lang);
            return false;
        }

        return resolveCurrentLanguageSlice(
            pending,
            effectiveBudget.resolveMaxItems,
            effectiveBudget.resolveMaxNanos);
    }

    // =========================================================================
    // Blocking resolution per language
    // =========================================================================

    public static void resolveLanguage(PendingBuild pending, String lang) {
        if (pending == null) {
            throw new IllegalArgumentException("pending build is null");
        }
        ensureActiveBuild(pending);

        ensureExpanded(pending);

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) {
            throw new IllegalStateException("Minecraft client is not ready for language resolution.");
        }

        PolyglotTooltip.LOG.info(
            "[PolyglotTooltips] {} build: resolving '{}' ({}/{}) via BACKGROUND mode; live language stays '{}'.",
            pending.owner.getDisplayName(),
            lang,
            blockingLanguageOrdinal(pending, lang),
            pending.targetLanguages.size(),
            mc.gameSettings.language == null ? "?" : mc.gameSettings.language);

        LanguageRunStats stats = new LanguageRunStats();
        stats.switchMode = SwitchResult.BACKGROUND;
        stats.switchMs = 0L;
        BuildProfiler.Scope profilerScope = pending.profiler.activate();
        TranslationScope translationScope = null;
        LiveDisplayNameHintScope liveDisplayNameHintScope = null;
        try {
            liveDisplayNameHintScope = beginLiveDisplayNameHintScope(pending);
            long setupProfileStartNs = BuildProfiler.startSection();
            long setupStart = System.currentTimeMillis();
            translationScope = beginTranslationScope(pending, lang);
            stats.setupMs += System.currentTimeMillis() - setupStart;
            BuildProfiler.record(
                "scope.begin",
                null,
                lang,
                setupProfileStartNs,
                translationScope != null && translationScope.isActive() ? "active" : null);

            long resolveStart = System.currentTimeMillis();
            long resolveSliceProfileStartNs = BuildProfiler.startSection();
            for (int i = 0; i < pending.expandedEntries.size(); i++) {
                processExpandedStack(pending, pending.expandedEntries.get(i), lang, stats);
            }
            stats.sliceCount++;
            stats.processedEntries += pending.expandedEntries.size();
            BuildProfiler.record(
                "slice.resolve",
                null,
                lang,
                resolveSliceProfileStartNs,
                pending.expandedEntries.isEmpty() ? null : String.valueOf(pending.expandedEntries.size()));
            stats.resolveMs += System.currentTimeMillis() - resolveStart;
        } finally {
            if (translationScope != null) {
                boolean activeTranslationScope = translationScope.isActive();
                long closeProfileStartNs = BuildProfiler.startSection();
                long closeStart = System.currentTimeMillis();
                translationScope.close();
                stats.setupMs += System.currentTimeMillis() - closeStart;
                BuildProfiler.record(
                    "scope.close",
                    null,
                    lang,
                    closeProfileStartNs,
                    activeTranslationScope ? "restored" : null);
            }
            if (liveDisplayNameHintScope != null) {
                liveDisplayNameHintScope.close();
            }
            profilerScope.close();
        }
        finalizeLanguage(pending, lang, stats);
    }

    // =========================================================================
    // Finish / cancel
    // =========================================================================

    public static CompletedBuild finishBuildWithoutReports(PendingBuild pending) throws Exception {
        if (pending == null) {
            throw new IllegalArgumentException("pending build is null");
        }
        ensureActiveBuild(pending);

        ensureExpanded(pending);

        long writeStart = System.currentTimeMillis();
        Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> dataToWrite = pending.mergeWithPreviousCache
            ? mergeCaches(pending.previousCache, pending.collected)
            : deepCopyCache(pending.collected);
        FullNameCacheMetadata metadata = buildMetadata(pending, dataToWrite);
        FullNameCacheIO.write(dataToWrite, metadata);
        FullNameCache.replace(dataToWrite, metadata);
        long writeMs = System.currentTimeMillis() - writeStart;
        PolyglotTooltip.LOG.info("[PolyglotTooltips] Cache written ({}ms).", writeMs);

        long elapsedMs = System.currentTimeMillis() - pending.startMs;

        BuildResult result = new BuildResult(
            pending.itemsScanned,
            pending.expandedEntries.size(),
            dataToWrite.size(),
            pending.liveDisplayNameHints.size(),
            pending.collectedPerLang,
            pending.emptyPerLang,
            pending.rawKeyPerLang,
            pending.goodPerLang,
            pending.cjkSuspectPerLang,
            pending.mixedLangPerLang,
            pending.formatOnlyPerLang,
            pending.switchModePerLang,
            pending.setupMsPerLang,
            pending.switchMsPerLang,
            pending.resolveMsPerLang,
            pending.resolveSliceCountPerLang,
            pending.resolveBudgetItemStopsPerLang,
            pending.resolveBudgetTimeStopsPerLang,
            pending.resolveProcessedEntriesPerLang,
            pending.enumMs,
            pending.expandMs,
            pending.expandSliceCount,
            pending.expandBudgetItemStops,
            pending.expandBudgetTimeStops,
            writeMs,
            pending.filter,
            elapsedMs,
            pending.profiler.snapshot());

        PolyglotTooltip.LOG.info(
            "[PolyglotTooltips] Cache phase complete in {}ms  (enum={}ms  expand={}ms  write={}ms).",
            elapsedMs,
            pending.enumMs,
            pending.expandMs,
            writeMs);

        releaseActiveBuild(pending);
        return new CompletedBuild(result, dataToWrite, pending.startMs);
    }

    public static long writeReports(CompletedBuild completedBuild) {
        if (completedBuild == null) {
            return 0L;
        }

        long reportStart = System.currentTimeMillis();
        try {
            BuildReportWriter.write(completedBuild.result, completedBuild.reportData);
        } catch (Exception e) {
            PolyglotTooltip.LOG.warn("[PolyglotTooltips] Report write failed (non-fatal): {}", e.getMessage());
        }

        long reportMs = System.currentTimeMillis() - reportStart;
        long totalElapsedMs = System.currentTimeMillis() - completedBuild.buildStartMs;
        PolyglotTooltip.LOG.info(
            "[PolyglotTooltips] Build complete in {}ms  (enum={}ms  expand={}ms  write={}ms  report={}ms).",
            totalElapsedMs,
            completedBuild.result.enumMs,
            completedBuild.result.expandMs,
            completedBuild.result.writeMs,
            reportMs);
        return reportMs;
    }

    public static BuildResult finishBuild(PendingBuild pending) throws Exception {
        CompletedBuild completedBuild = finishBuildWithoutReports(pending);
        writeReports(completedBuild);
        return completedBuild.getResult();
    }

    public static void cancelBuild(PendingBuild pending) {
        if (pending == null) {
            return;
        }
        if (!isActiveBuild(pending)) {
            return;
        }

        try {
            restoreActiveLanguage(pending, Minecraft.getMinecraft());
            FullNameCache.replace(pending.previousCache, pending.previousMetadata);
            PolyglotTooltip.LOG.warn(
                "[PolyglotTooltips] {} build cancelled; previous cache restored.",
                pending.owner.getDisplayName());
        } finally {
            releaseActiveBuild(pending);
        }
    }

    public static boolean hasActiveBuild() {
        synchronized (ACTIVE_BUILD_LOCK) {
            return activeBuild != null;
        }
    }

    public static ActiveBuildSnapshot getActiveBuildSnapshot() {
        synchronized (ACTIVE_BUILD_LOCK) {
            return activeBuild == null ? null : activeBuild.toSnapshot();
        }
    }

    // =========================================================================
    // Expansion
    // =========================================================================

    private static void ensureExpanded(PendingBuild pending) {
        while (!pending.isExpansionComplete()) {
            expandNextSlice(pending, Integer.MAX_VALUE, Long.MAX_VALUE);
        }
    }

    private static void expandNextSlice(PendingBuild pending, int maxItems, long maxNanos) {
        long startNs = System.nanoTime();
        long sliceWallNanos = 0L;
        int processed = 0;
        int adaptiveMaxItems = maxItems <= 0 ? Integer.MAX_VALUE : maxItems;
        int checkpointSize = determineExpandCheckpointSize(adaptiveMaxItems);
        long softTargetNanos = determineSoftTargetNanos(maxNanos, EXPAND_SOFT_TARGET_NANOS);
        boolean itemBudgetHit = false;
        boolean timeBudgetHit = false;
        BuildProfiler.Scope profilerScope = pending.profiler.activate();
        long expandSliceStartNs = BuildProfiler.startSection();
        try {
            while (pending.nextItemIndex < pending.items.size() || pending.activeExpansion != null) {
                if (pending.activeExpansion == null) {
                    Item item = pending.items.get(pending.nextItemIndex);
                    pending.activeExpansion = new ExpansionState(item);
                }

                if (advanceExpansion(pending, pending.activeExpansion, startNs, maxNanos)) {
                    pending.activeExpansion = null;
                    pending.nextItemIndex++;
                    processed++;
                    if (shouldRecalculateAdaptiveCap(processed, checkpointSize)) {
                        adaptiveMaxItems = adaptItemCap(
                            processed,
                            System.nanoTime() - startNs,
                            softTargetNanos,
                            maxItems,
                            checkpointSize);
                    }
                }

                if (processed >= adaptiveMaxItems) {
                    itemBudgetHit = true;
                    break;
                }
                if (isBudgetExceeded(startNs, maxNanos)) {
                    timeBudgetHit = true;
                    break;
                }
            }
        } finally {
            sliceWallNanos = System.nanoTime() - startNs;
            pending.recordLastSliceTelemetry(
                BuildPhase.EXPANDING,
                null,
                sliceWallNanos,
                processed,
                itemBudgetHit,
                timeBudgetHit);
            pending.expandMs += nanosToMillis(sliceWallNanos);
            pending.expandSliceCount++;
            if (itemBudgetHit) {
                BuildProfiler.record("budget.expand.items", null, null, BuildProfiler.startSection(), "hit");
            }
            if (timeBudgetHit) {
                BuildProfiler.record("budget.expand.time", null, null, BuildProfiler.startSection(), "hit");
            }
            BuildProfiler.record(
                "slice.expand",
                null,
                null,
                expandSliceStartNs,
                processed > 0 ? String.valueOf(processed) : null);
            profilerScope.close();
        }

        if (itemBudgetHit) {
            pending.expandBudgetItemStops++;
        }
        if (timeBudgetHit) {
            pending.expandBudgetTimeStops++;
        }

        if (pending.isExpansionComplete()) {
            PolyglotTooltip.LOG.info(
                "[PolyglotTooltips] Unique item+damage pairs: {}  ({}ms)",
                pending.expandedEntries.size(),
                pending.expandMs);
        }
    }

    private static void addSubItems(Item item, CreativeTabs tab, List<ItemStack> target) {
        if (item == null || tab == null) {
            return;
        }

        try {
            item.getSubItems(item, tab, target);
        } catch (Throwable ignored) {
            // Some items expose buggy creative-tab expansion; skip those variants.
        }
    }

    private static boolean advanceExpansion(PendingBuild pending,
            ExpansionState state, long startNs, long maxNanos) {
        if (state == null) {
            return true;
        }

        if (state.item == null) {
            return true;
        }

        while (true) {
            if (!state.preferredTabsScanned) {
                if (advancePreferredTabScan(state)) {
                    return false;
                }
                continue;
            }

            if (state.variants.isEmpty() && !state.fallbackTabsScanned) {
                if (advanceFallbackTabScan(state)) {
                    return false;
                }
                continue;
            }

            if (!state.defaultVariantAdded && state.variants.isEmpty()) {
                state.variants.add(new ItemStack(state.item, 1, 0));
                state.defaultVariantAdded = true;
            }

            if (advanceVariantCollection(pending, state, startNs, maxNanos)) {
                return false;
            }

            return true;
        }
    }

    private static boolean advancePreferredTabScan(ExpansionState state) {
        while (state.nextPreferredTabIndex < state.preferredTabs.size()) {
            CreativeTabs tab = state.preferredTabs.get(state.nextPreferredTabIndex++);
            if (tab == null || !state.attemptedTabs.add(tab)) {
                continue;
            }
            int variantsBefore = state.variants.size();
            long profileStartNs = BuildProfiler.startSection();
            addSubItems(state.item, tab, state.variants);
            int variantsAdded = Math.max(0, state.variants.size() - variantsBefore);
            BuildProfiler.record(
                "expand.preferred_tab",
                null,
                null,
                profileStartNs,
                variantsAdded > 0 ? String.valueOf(variantsAdded) : null);
            return true;
        }

        state.preferredTabsScanned = true;
        return false;
    }

    private static boolean advanceFallbackTabScan(ExpansionState state) {
        while (state.nextFallbackTabIndex < CreativeTabs.creativeTabArray.length) {
            CreativeTabs tab = CreativeTabs.creativeTabArray[state.nextFallbackTabIndex++];
            if (tab == null || state.attemptedTabs.contains(tab)) {
                continue;
            }
            state.attemptedTabs.add(tab);
            int variantsBefore = state.variants.size();
            long profileStartNs = BuildProfiler.startSection();
            addSubItems(state.item, tab, state.variants);
            int variantsAdded = Math.max(0, state.variants.size() - variantsBefore);
            BuildProfiler.record(
                "expand.fallback_tab",
                null,
                null,
                profileStartNs,
                variantsAdded > 0 ? String.valueOf(variantsAdded) : null);
            return true;
        }

        state.fallbackTabsScanned = true;
        return false;
    }

    private static boolean advanceVariantCollection(PendingBuild pending,
            ExpansionState state, long startNs, long maxNanos) {
        long profileStartNs = BuildProfiler.startSection();
        int committed = 0;
        try {
            while (state.nextVariantIndex < state.variants.size()) {
                ItemStack stack = state.variants.get(state.nextVariantIndex++);
                if (stack == null || stack.getItem() == null) {
                    if (isBudgetExceeded(startNs, maxNanos)) {
                        return true;
                    }
                    continue;
                }

                String registryName = getRegistryName(stack.getItem());
                if (registryName == null || registryName.isEmpty()) {
                    if (isBudgetExceeded(startNs, maxNanos)) {
                        return true;
                    }
                    continue;
                }

                StackKey stackKey = StackKey.of(stack);
                if (stackKey != null && !pending.expandedStacks.containsKey(stackKey)) {
                    ItemStack expandedStack = stack.copy();
                    refreshLiveDisplayNameHintLanguage(pending);
                    String liveDisplayName = captureLiveDisplayNameIfNeeded(expandedStack);
                    ExpandedStack expanded = new ExpandedStack(
                        new PrebuiltSecondaryNameIndexKey(registryName, stack.getItemDamage()),
                        expandedStack,
                        liveDisplayName);
                    pending.expandedStacks.put(stackKey, expanded);
                    pending.expandedEntries.add(expanded);
                    committed++;
                    if (expanded.liveDisplayName != null && !expanded.liveDisplayName.trim().isEmpty()) {
                        pending.liveDisplayNameHints.put(expanded.stack, expanded.liveDisplayName);
                    }
                }

                if (isBudgetExceeded(startNs, maxNanos)) {
                    return true;
                }
            }
        } finally {
            BuildProfiler.record(
                "expand.variant_commit",
                null,
                null,
                profileStartNs,
                committed > 0 ? String.valueOf(committed) : null);
        }

        state.variants.clear();
        state.nextVariantIndex = 0;
        return false;
    }

    private static List<CreativeTabs> collectPreferredCreativeTabs(Item item) {
        LinkedHashSet<CreativeTabs> tabs = new LinkedHashSet<CreativeTabs>();
        if (item == null) {
            return new ArrayList<CreativeTabs>();
        }

        addCreativeTab(tabs, item.getCreativeTab());

        CreativeTabs[] extraTabs = getCreativeTabs(item);
        if (extraTabs != null) {
            for (int i = 0; i < extraTabs.length; i++) {
                addCreativeTab(tabs, extraTabs[i]);
            }
        }

        return new ArrayList<CreativeTabs>(tabs);
    }

    private static void addCreativeTab(Set<CreativeTabs> tabs, CreativeTabs tab) {
        if (tabs == null || tab == null) {
            return;
        }
        tabs.add(tab);
    }

    private static CreativeTabs[] getCreativeTabs(Item item) {
        if (item == null || ITEM_GET_CREATIVE_TABS_METHOD == null) {
            return null;
        }

        try {
            Object result = ITEM_GET_CREATIVE_TABS_METHOD.invoke(item);
            return result instanceof CreativeTabs[] ? (CreativeTabs[]) result : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method resolveItemGetCreativeTabsMethod() {
        try {
            return Item.class.getMethod("getCreativeTabs");
        } catch (Throwable ignored) {
            return null;
        }
    }

    // =========================================================================
    // Budgeted resolution
    // =========================================================================

    private static void beginLanguage(PendingBuild pending, String lang) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) {
            throw new IllegalStateException("Minecraft client is not ready for language resolution.");
        }

        pending.activeLanguage = lang;
        pending.currentExpandedIndex = 0;
        pending.activeStats = new LanguageRunStats();
        pending.activeSavedLanguage = mc.gameSettings.language;
        pending.activeLanguageSwitched = false;
        pending.activeStats.switchMode = SwitchResult.BACKGROUND;

        PolyglotTooltip.LOG.info(
            "[PolyglotTooltips] {} build: resolving '{}' ({}/{}) via BACKGROUND mode; live language stays '{}'.",
            pending.owner.getDisplayName(),
            lang,
            pending.currentLanguageIndex + 1,
            pending.targetLanguages.size(),
            pending.activeSavedLanguage == null ? "?" : pending.activeSavedLanguage);
    }

    private static int blockingLanguageOrdinal(PendingBuild pending, String lang) {
        if (pending == null || pending.targetLanguages == null || pending.targetLanguages.isEmpty()) {
            return 1;
        }

        for (int i = 0; i < pending.targetLanguages.size(); i++) {
            String configuredLanguage = pending.targetLanguages.get(i);
            if (configuredLanguage != null && configuredLanguage.equalsIgnoreCase(lang)) {
                return i + 1;
            }
        }

        return Math.min(pending.targetLanguages.size(), pending.currentLanguageIndex + 1);
    }

    private static boolean resolveCurrentLanguageSlice(PendingBuild pending, int maxItems, long maxNanos) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) {
            throw new IllegalStateException("Minecraft client is not ready for language resolution.");
        }

        String lang = pending.activeLanguage;
        long sliceStartNs = System.nanoTime();
        long sliceWallNanos = 0L;
        int processed = 0;
        int adaptiveMaxItems = maxItems <= 0 ? Integer.MAX_VALUE : maxItems;
        int checkpointSize = determineResolveCheckpointSize(adaptiveMaxItems);
        long softTargetNanos = determineSoftTargetNanos(maxNanos, RESOLVE_SOFT_TARGET_NANOS);
        boolean itemBudgetHit = false;
        boolean timeBudgetHit = false;
        BuildProfiler.Scope profilerScope = pending.profiler.activate();
        TranslationScope translationScope = null;
        LiveDisplayNameHintScope liveDisplayNameHintScope = null;
        long resolveSliceProfileStartNs = 0L;
        try {
            liveDisplayNameHintScope = beginLiveDisplayNameHintScope(pending);
            long setupProfileStartNs = BuildProfiler.startSection();
            long setupStart = System.currentTimeMillis();
            translationScope = beginTranslationScope(pending, lang);
            pending.activeStats.setupMs += System.currentTimeMillis() - setupStart;
            BuildProfiler.record(
                "scope.begin",
                null,
                lang,
                setupProfileStartNs,
                translationScope != null && translationScope.isActive() ? "active" : null);

            resolveSliceProfileStartNs = BuildProfiler.startSection();
            long resolveStartNs = System.nanoTime();
            long workSoftTargetNanos = determineRemainingWorkBudgetNanos(sliceStartNs, softTargetNanos);
            while (pending.currentExpandedIndex < pending.expandedEntries.size()) {
                ExpandedStack expanded = pending.expandedEntries.get(pending.currentExpandedIndex++);
                processExpandedStack(pending, expanded, lang, pending.activeStats);
                processed++;

                if (shouldRecalculateAdaptiveCap(processed, checkpointSize)) {
                    adaptiveMaxItems = adaptItemCap(
                        processed,
                        System.nanoTime() - resolveStartNs,
                        workSoftTargetNanos,
                        maxItems,
                        checkpointSize);
                }

                if (processed >= adaptiveMaxItems) {
                    itemBudgetHit = true;
                    break;
                }
                if (isBudgetExceeded(sliceStartNs, maxNanos)) {
                    timeBudgetHit = true;
                    break;
                }
            }
            pending.activeStats.sliceCount++;
            pending.activeStats.processedEntries += processed;
            pending.activeStats.resolveMs += nanosToMillis(System.nanoTime() - resolveStartNs);
            BuildProfiler.record(
                "slice.resolve",
                null,
                lang,
                resolveSliceProfileStartNs,
                processed > 0 ? String.valueOf(processed) : null);
        } finally {
            if (translationScope != null) {
                boolean activeTranslationScope = translationScope.isActive();
                long closeProfileStartNs = BuildProfiler.startSection();
                long closeStart = System.currentTimeMillis();
                translationScope.close();
                pending.activeStats.setupMs += System.currentTimeMillis() - closeStart;
                BuildProfiler.record(
                    "scope.close",
                    null,
                    lang,
                    closeProfileStartNs,
                    activeTranslationScope ? "restored" : null);
            }
            if (liveDisplayNameHintScope != null) {
                liveDisplayNameHintScope.close();
            }
            if (itemBudgetHit) {
                BuildProfiler.record("budget.resolve.items", null, lang, BuildProfiler.startSection(), "hit");
            }
            if (timeBudgetHit) {
                BuildProfiler.record("budget.resolve.time", null, lang, BuildProfiler.startSection(), "hit");
            }
            profilerScope.close();
            sliceWallNanos = System.nanoTime() - sliceStartNs;
            pending.recordLastSliceTelemetry(
                BuildPhase.RESOLVING,
                lang,
                sliceWallNanos,
                processed,
                itemBudgetHit,
                timeBudgetHit);
        }

        if (itemBudgetHit) {
            pending.activeStats.budgetItemStops++;
        }
        if (timeBudgetHit) {
            pending.activeStats.budgetTimeStops++;
        }

        if (pending.currentExpandedIndex < pending.expandedEntries.size()) {
            return false;
        }

        restoreActiveLanguage(pending, mc);
        finalizeLanguage(pending, lang, pending.activeStats);
        clearActiveLanguageState(pending);

        return pending.currentLanguageIndex >= pending.targetLanguages.size();
    }

    private static void processExpandedStack(PendingBuild pending, ExpandedStack expanded, String lang,
            LanguageRunStats stats) {
        if (expanded == null || expanded.stack == null || expanded.stack.getItem() == null) {
            return;
        }

        String displayName = resolveDisplayName(expanded.stack, lang);
        if (displayName == null || displayName.isEmpty()) {
            stats.emptyCount++;
            return;
        }

        NameQuality quality = NameQualityClassifier.classify(displayName, lang);
        switch (quality) {
            case RAW_KEY:
                stats.rawCount++;
                break;
            case CONTAINS_CJK:
                stats.cjkSuspectCount++;
                break;
            case MIXED_LANGUAGE:
                stats.mixedLangCount++;
                break;
            case FORMAT_ONLY:
                stats.formatOnlyCount++;
                break;
            default:
                stats.goodCount++;
                break;
        }

        Map<String, String> langs = pending.collected.get(expanded.cacheKey);
        if (langs == null) {
            langs = new HashMap<String, String>();
            pending.collected.put(expanded.cacheKey, langs);
        }
        langs.put(lang, displayName);
        stats.okCount++;
    }

    private static void finalizeLanguage(PendingBuild pending, String lang, LanguageRunStats stats) {
        pending.switchModePerLang.put(lang, stats.switchMode == null ? SwitchResult.FAILED.name() : stats.switchMode.name());
        pending.setupMsPerLang.put(lang, Long.valueOf(stats.setupMs));
        pending.switchMsPerLang.put(lang, Long.valueOf(stats.switchMs));
        pending.resolveMsPerLang.put(lang, Long.valueOf(stats.resolveMs));
        pending.resolveSliceCountPerLang.put(lang, Integer.valueOf(stats.sliceCount));
        pending.resolveBudgetItemStopsPerLang.put(lang, Integer.valueOf(stats.budgetItemStops));
        pending.resolveBudgetTimeStopsPerLang.put(lang, Integer.valueOf(stats.budgetTimeStops));
        pending.resolveProcessedEntriesPerLang.put(lang, Integer.valueOf(stats.processedEntries));
        pending.collectedPerLang.put(lang, Integer.valueOf(stats.okCount));
        pending.emptyPerLang.put(lang, Integer.valueOf(stats.emptyCount));
        pending.rawKeyPerLang.put(lang, Integer.valueOf(stats.rawCount));
        pending.goodPerLang.put(lang, Integer.valueOf(stats.goodCount));
        pending.cjkSuspectPerLang.put(lang, Integer.valueOf(stats.cjkSuspectCount));
        pending.mixedLangPerLang.put(lang, Integer.valueOf(stats.mixedLangCount));
        pending.formatOnlyPerLang.put(lang, Integer.valueOf(stats.formatOnlyCount));

        PolyglotTooltip.LOG.info(
            "[PolyglotTooltips] {} build lang={}: collected={}, good={}, empty={}, rawKey={}, cjkSuspect={}, mixed={}, formatOnly={}  setup={}ms  resolve={}ms  slices={}  budgetStops[item={}, time={}]",
            pending.owner.getDisplayName(),
            lang,
            stats.okCount,
            stats.goodCount,
            stats.emptyCount,
            stats.rawCount,
            stats.cjkSuspectCount,
            stats.mixedLangCount,
            stats.formatOnlyCount,
            stats.setupMs,
            stats.resolveMs,
            stats.sliceCount,
            stats.budgetItemStops,
            stats.budgetTimeStops);
    }

    private static void restoreActiveLanguage(PendingBuild pending, Minecraft mc) {
        if (pending == null || pending.activeStats == null || mc == null || mc.gameSettings == null) {
            return;
        }

        String restoreLanguage = pending.activeSavedLanguage;
        if (!pending.activeLanguageSwitched || restoreLanguage == null || restoreLanguage.trim().isEmpty()) {
            return;
        }

        long restoreStart = System.currentTimeMillis();
        SwitchResult restoreResult = LanguageSwitcher.switchTo(mc, restoreLanguage, Config.useFastLanguageSwitch);
        long restoreMs = System.currentTimeMillis() - restoreStart;
        if (restoreResult == SwitchResult.FAILED) {
            pending.activeStats.switchMode = combineSwitchModes(pending.activeStats.switchMode, SwitchResult.FAILED);
            PolyglotTooltip.LOG.warn(
                "[PolyglotTooltips] {} build failed to restore language '{}'.",
                pending.owner.getDisplayName(),
                restoreLanguage);
            return;
        }

        pending.activeStats.switchMs += restoreMs;
        pending.activeStats.switchMode = combineSwitchModes(pending.activeStats.switchMode, restoreResult);
        pending.activeLanguageSwitched = false;
        PolyglotTooltip.LOG.info(
            "[PolyglotTooltips] {} build restored language to '{}' via {} path ({}ms).",
            pending.owner.getDisplayName(),
            restoreLanguage,
            restoreResult.name(),
            restoreMs);
    }

    private static void clearActiveLanguageState(PendingBuild pending) {
        pending.activeLanguage = null;
        pending.activeSavedLanguage = null;
        pending.activeLanguageSwitched = false;
        pending.activeStats = null;
        pending.currentExpandedIndex = 0;
        pending.currentLanguageIndex++;
    }

    // =========================================================================
    // Output helpers
    // =========================================================================

    private static Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> mergeCaches(
            Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> previousCache,
            Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> collected) {
        Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> merged = deepCopyCache(previousCache);
        for (Map.Entry<PrebuiltSecondaryNameIndexKey, Map<String, String>> entry : collected.entrySet()) {
            Map<String, String> existing = merged.get(entry.getKey());
            if (existing == null) {
                existing = new LinkedHashMap<String, String>();
                merged.put(entry.getKey(), existing);
            }
            existing.putAll(entry.getValue());
        }
        return merged;
    }

    private static Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> deepCopyCache(
            Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> source) {
        Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> copy =
            new LinkedHashMap<PrebuiltSecondaryNameIndexKey, Map<String, String>>();
        if (source == null || source.isEmpty()) {
            return copy;
        }

        for (Map.Entry<PrebuiltSecondaryNameIndexKey, Map<String, String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashMap<String, String>(entry.getValue()));
        }
        return copy;
    }

    private static FullNameCacheMetadata buildMetadata(PendingBuild pending,
            Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> dataToWrite) {
        List<String> languages = new ArrayList<String>(collectLanguages(dataToWrite));

        String coverageFilter = pending.filter;
        if (pending.mergeWithPreviousCache
            && pending.previousMetadata != null
            && pending.previousMetadata.getCoverageFilter() != null
            && !pending.previousMetadata.getCoverageFilter().trim().isEmpty()) {
            coverageFilter = pending.previousMetadata.getCoverageFilter();
        }

        boolean complete = false;
        if (!pending.mergeWithPreviousCache) {
            complete = "all".equals(pending.filter);
        } else if (pending.previousMetadata != null && pending.previousMetadata.isComplete()) {
            complete = true;
        } else if ("all".equals(pending.filter) && containsAllLanguages(languages, pending.targetLanguages)) {
            complete = true;
        }

        return FullNameCacheMetadata.createForBuild(
            languages,
            coverageFilter,
            complete,
            Tags.VERSION,
            System.currentTimeMillis());
    }

    private static Set<String> collectLanguages(
            Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> data) {
        LinkedHashSet<String> languages = new LinkedHashSet<String>();
        if (data == null || data.isEmpty()) {
            return languages;
        }

        for (Map<String, String> langs : data.values()) {
            if (langs == null || langs.isEmpty()) {
                continue;
            }

            for (String languageCode : langs.keySet()) {
                if (languageCode != null && !languageCode.trim().isEmpty()) {
                    languages.add(languageCode.trim());
                }
            }
        }

        return languages;
    }

    private static boolean containsAllLanguages(List<String> actualLanguages, List<String> expectedLanguages) {
        List<String> normalizedActual = normalizeLanguages(actualLanguages);
        List<String> normalizedExpected = normalizeLanguages(expectedLanguages);
        for (int i = 0; i < normalizedExpected.size(); i++) {
            String expected = normalizedExpected.get(i);
            boolean found = false;
            for (int j = 0; j < normalizedActual.size(); j++) {
                if (expected.equalsIgnoreCase(normalizedActual.get(j))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    // =========================================================================
    // Name resolution helpers
    // =========================================================================

    private static String resolveDisplayName(ItemStack stack, String languageCode) {
        try {
            String resolved = DisplayNameResolver.resolveSecondaryDisplayNameForLanguage(stack, languageCode);
            if (resolved != null && !resolved.isEmpty()) {
                return resolved;
            }
        } catch (Throwable ignored) {
            // Fall through to stack.getDisplayName() fallback.
        }
        return safeGetDisplayName(stack, languageCode);
    }

    private static String safeGetDisplayName(ItemStack stack, String languageCode) {
        try {
            long fallbackStartNs = BuildProfiler.startSection();
            String name = null;
            try {
                name = ProgrammaticDisplayNameLookup.getItemDisplayName(stack, languageCode);
            } finally {
                BuildProfiler.record(
                    "fallback.item_display_name",
                    stack,
                    languageCode,
                    fallbackStartNs,
                    name);
            }
            if (name == null) {
                return null;
            }
            String stripped = EnumChatFormatting.getTextWithoutFormattingCodes(name);
            String result = stripped != null ? stripped.trim() : name.trim();
            return result.isEmpty() ? null : result;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String captureLiveDisplayNameIfNeeded(ItemStack stack) {
        if (!shouldCaptureLiveDisplayName(stack)) {
            return null;
        }

        long captureStartNs = BuildProfiler.startSection();
        String liveDisplayName = null;
        try {
            liveDisplayName = safeGetRawDisplayName(stack);
        } finally {
            BuildProfiler.record("capture.live_name", stack, null, captureStartNs, liveDisplayName);
        }

        return liveDisplayName == null || liveDisplayName.trim().isEmpty() ? null : liveDisplayName;
    }

    private static String safeGetRawDisplayName(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        try {
            return stack.getDisplayName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean shouldCaptureLiveDisplayName(ItemStack stack) {
        String itemClassName = safeGetItemClassName(stack);
        String blockClassName = safeGetBlockClassName(stack);
        String registryName = stack == null || stack.getItem() == null ? null : getRegistryName(stack.getItem());
        String unlocalizedName = safeGetNormalizedUnlocalizedName(stack);
        return isBinnieCandidate(itemClassName, blockClassName, registryName, unlocalizedName)
            || isGregTechCandidate(itemClassName, blockClassName, registryName, unlocalizedName);
    }

    private static boolean isBinnieCandidate(String itemClassName, String blockClassName,
            String registryName, String unlocalizedName) {
        if (startsWithIgnoreCase(itemClassName, "binnie.")) {
            return true;
        }
        if (startsWithIgnoreCase(blockClassName, "binnie.")) {
            return true;
        }

        if (startsWithIgnoreCase(registryName, "ExtraBees:")
            || startsWithIgnoreCase(registryName, "ExtraTrees:")
            || startsWithIgnoreCase(registryName, "Botany:")
            || startsWithIgnoreCase(registryName, "Genetics:")
            || startsWithIgnoreCase(registryName, "BinnieCore:")) {
            return true;
        }

        return startsWithIgnoreCase(unlocalizedName, "extrabees.")
            || startsWithIgnoreCase(unlocalizedName, "extratrees.")
            || startsWithIgnoreCase(unlocalizedName, "botany.")
            || startsWithIgnoreCase(unlocalizedName, "genetics.")
            || startsWithIgnoreCase(unlocalizedName, "binniecore.")
            || startsWithIgnoreCase(unlocalizedName, "for.extratrees.");
    }

    private static boolean isGregTechCandidate(String itemClassName, String blockClassName,
            String registryName, String unlocalizedName) {
        if (startsWithIgnoreCase(itemClassName, "gregtech.")
            || startsWithIgnoreCase(itemClassName, "bartworks.")
            || startsWithIgnoreCase(blockClassName, "gregtech.")
            || startsWithIgnoreCase(blockClassName, "bartworks.")) {
            return true;
        }

        if (startsWithIgnoreCase(registryName, "gregtech:")
            || startsWithIgnoreCase(registryName, "bartworks:")) {
            return true;
        }

        return startsWithIgnoreCase(unlocalizedName, "gt.")
            || startsWithIgnoreCase(unlocalizedName, "bw.")
            || startsWithIgnoreCase(unlocalizedName, "gtplusplus.")
            || startsWithIgnoreCase(unlocalizedName, "comb.")
            || startsWithIgnoreCase(unlocalizedName, "propolis.");
    }

    private static String safeGetItemClassName(ItemStack stack) {
        try {
            return stack == null || stack.getItem() == null ? null : stack.getItem().getClass().getName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String safeGetBlockClassName(ItemStack stack) {
        try {
            if (stack == null || stack.getItem() == null) {
                return null;
            }
            Block block = Block.getBlockFromItem(stack.getItem());
            return block == null ? null : block.getClass().getName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String safeGetNormalizedUnlocalizedName(ItemStack stack) {
        try {
            if (stack == null) {
                return null;
            }

            return normalizeUnlocalizedName(stack.getUnlocalizedName());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String normalizeUnlocalizedName(String unlocalizedName) {
        if (unlocalizedName == null) {
            return null;
        }

        String normalized = unlocalizedName.trim();
        if (normalized.startsWith("item.") || normalized.startsWith("tile.")) {
            return normalized.substring(5);
        }
        return normalized;
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        if (value == null || prefix == null || value.length() < prefix.length()) {
            return false;
        }
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static LiveDisplayNameHintScope beginLiveDisplayNameHintScope(PendingBuild pending) {
        refreshLiveDisplayNameHintLanguage(pending);
        return ProgrammaticDisplayNameLookup.beginLiveDisplayNameHintScope(
            pending == null ? null : pending.liveDisplayNameHints);
    }

    private static TranslationScope beginTranslationScope(PendingBuild pending, String languageCode) {
        String liveLanguageCode = getCurrentLanguageCode();
        if (sameLanguageCode(languageCode, liveLanguageCode)) {
            return ProgrammaticDisplayNameLookup.beginScope(languageCode);
        }

        RestoreSnapshot restoreSnapshot =
            getLiveTranslationRestoreSnapshot(pending, liveLanguageCode);
        return ProgrammaticDisplayNameLookup.beginScope(languageCode, restoreSnapshot);
    }

    private static RestoreSnapshot getLiveTranslationRestoreSnapshot(PendingBuild pending,
            String liveLanguageCode) {
        if (pending == null || liveLanguageCode == null || liveLanguageCode.trim().isEmpty()) {
            return null;
        }

        RestoreSnapshot cached = pending.liveTranslationRestoreSnapshots.get(liveLanguageCode);
        if (cached != null) {
            return cached;
        }

        RestoreSnapshot snapshot = ProgrammaticDisplayNameLookup.newRestoreSnapshot(liveLanguageCode);
        pending.liveTranslationRestoreSnapshots.put(liveLanguageCode, snapshot);
        return snapshot;
    }

    private static void refreshLiveDisplayNameHintLanguage(PendingBuild pending) {
        if (pending == null) {
            return;
        }

        String currentLanguageCode = getCurrentLanguageCode();
        if (currentLanguageCode == null) {
            return;
        }

        if (pending.liveDisplayNameHintLanguage == null
            || pending.liveDisplayNameHintLanguage.trim().isEmpty()) {
            pending.liveDisplayNameHintLanguage = currentLanguageCode;
            return;
        }

        if (sameLanguageCode(pending.liveDisplayNameHintLanguage, currentLanguageCode)) {
            return;
        }

        if (!pending.liveDisplayNameHints.isEmpty()) {
            pending.liveDisplayNameHints.clear();
            PolyglotTooltip.LOG.warn(
                "[PolyglotTooltips] {} build cleared live-name hints after display language changed from '{}' to '{}'.",
                pending.owner.getDisplayName(),
                pending.liveDisplayNameHintLanguage,
                currentLanguageCode);
        }
        pending.liveDisplayNameHintLanguage = currentLanguageCode;
    }

    private static boolean sameLanguageCode(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static String getCurrentLanguageCode() {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft != null && minecraft.gameSettings != null && minecraft.gameSettings.language != null) {
                String languageCode = minecraft.gameSettings.language.trim();
                return languageCode.isEmpty() ? null : languageCode;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // =========================================================================
    // Generic helpers
    // =========================================================================

    private static List<Item> collectItems(String filter) {
        List<Item> result = new ArrayList<Item>();
        for (Object obj : Item.itemRegistry) {
            if (!(obj instanceof Item)) {
                continue;
            }

            Item item = (Item) obj;
            if (!filter.isEmpty()) {
                String reg = getRegistryName(item);
                if (reg == null || !reg.startsWith(filter)) {
                    continue;
                }
            }

            result.add(item);
        }
        return result;
    }

    private static boolean shouldMergeWithPrevious(String scanFilter) {
        return !normalizeFilter(scanFilter).isEmpty();
    }

    private static String getRegistryName(Item item) {
        if (item == null) {
            return null;
        }

        try {
            Object name = Item.itemRegistry.getNameForObject(item);
            return name == null ? null : String.valueOf(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<String> normalizeLanguages(List<String> targetLanguages) {
        List<String> normalized = FullNameCacheMetadata.normalizeLanguages(targetLanguages);
        return normalized == null ? new ArrayList<String>() : normalized;
    }

    private static String normalizeFilter(String filter) {
        if (filter == null) {
            return "";
        }

        String normalized = filter.trim();
        if (normalized.isEmpty() || normalized.equalsIgnoreCase("all")) {
            return "";
        }

        if (!normalized.contains(":")) {
            normalized = normalized + ":";
        }

        return normalized;
    }

    private static boolean isBudgetExceeded(long startNs, long maxNanos) {
        return maxNanos != Long.MAX_VALUE && System.nanoTime() - startNs >= maxNanos;
    }

    private static long determineSoftTargetNanos(long maxNanos, long softTargetNanos) {
        if (maxNanos == Long.MAX_VALUE || softTargetNanos <= 0L) {
            return Long.MAX_VALUE;
        }

        return Math.min(maxNanos, softTargetNanos);
    }

    private static long determineRemainingWorkBudgetNanos(long sliceStartNs, long softTargetNanos) {
        if (softTargetNanos == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }

        long elapsedBeforeWorkNs = System.nanoTime() - sliceStartNs;
        long remaining = softTargetNanos - elapsedBeforeWorkNs;
        return remaining <= 0L ? 1L * 1000L * 1000L : remaining;
    }

    private static int determineExpandCheckpointSize(int maxItems) {
        return determineCheckpointSize(
            maxItems,
            MIN_EXPAND_CHECKPOINT_ITEMS,
            MAX_EXPAND_CHECKPOINT_ITEMS);
    }

    private static int determineResolveCheckpointSize(int maxItems) {
        return determineCheckpointSize(
            maxItems,
            MIN_RESOLVE_CHECKPOINT_ITEMS,
            MAX_RESOLVE_CHECKPOINT_ITEMS);
    }

    private static int determineCheckpointSize(int maxItems, int minimum, int maximum) {
        int hardCap = maxItems <= 0 || maxItems == Integer.MAX_VALUE ? maximum : maxItems;
        int checkpoint = Math.max(minimum, hardCap / 4);
        return Math.min(maximum, checkpoint);
    }

    private static boolean shouldRecalculateAdaptiveCap(int processed, int checkpointSize) {
        return checkpointSize > 0 && processed >= checkpointSize && processed % checkpointSize == 0;
    }

    private static int adaptItemCap(int processed, long elapsedNanos, long targetNanos,
            int hardCap, int checkpointSize) {
        int normalizedHardCap = hardCap <= 0 ? Integer.MAX_VALUE : hardCap;
        int minimumCap = Math.max(1, checkpointSize);
        if (normalizedHardCap <= minimumCap || elapsedNanos <= 0L || targetNanos == Long.MAX_VALUE) {
            return normalizedHardCap;
        }

        long projected = Math.round(processed * (double) targetNanos / (double) elapsedNanos);
        if (projected <= 0L) {
            return normalizedHardCap;
        }

        if (projected < minimumCap) {
            projected = minimumCap;
        }
        if (projected > normalizedHardCap) {
            projected = normalizedHardCap;
        }
        return (int) projected;
    }

    private static long nanosToMillis(long nanos) {
        return nanos <= 0L ? 0L : nanos / 1000000L;
    }

    private static SwitchResult combineSwitchModes(SwitchResult existing, SwitchResult next) {
        if (next == null) {
            return existing;
        }

        if (existing == null) {
            return next;
        }

        if (existing == SwitchResult.FAILED || next == SwitchResult.FAILED) {
            return SwitchResult.FAILED;
        }

        if (existing == SwitchResult.FULL || next == SwitchResult.FULL) {
            return SwitchResult.FULL;
        }

        if (existing == SwitchResult.FAST || next == SwitchResult.FAST) {
            return SwitchResult.FAST;
        }

        return SwitchResult.BACKGROUND;
    }

    private static void claimActiveBuild(BuildOwner owner, PendingBuild pending) {
        if (pending == null) {
            throw new IllegalArgumentException("pending build is null");
        }

        synchronized (ACTIVE_BUILD_LOCK) {
            if (activeBuild != null) {
                throw new IllegalStateException(buildConflictMessage(activeBuild.toSnapshot()));
            }
            activeBuild = new ActiveBuildState(owner, pending);
        }
    }

    private static void ensureActiveBuild(PendingBuild pending) {
        if (!isActiveBuild(pending)) {
            throw new IllegalStateException("Build is no longer active.");
        }
    }

    private static boolean isActiveBuild(PendingBuild pending) {
        synchronized (ACTIVE_BUILD_LOCK) {
            return activeBuild != null && activeBuild.pending == pending;
        }
    }

    private static void releaseActiveBuild(PendingBuild pending) {
        synchronized (ACTIVE_BUILD_LOCK) {
            if (activeBuild != null && activeBuild.pending == pending) {
                activeBuild = null;
            }
        }
    }

    private static String buildConflictMessage(ActiveBuildSnapshot snapshot) {
        if (snapshot == null) {
            return "Another full name cache build is already running.";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Another ")
            .append(snapshot.owner.getDisplayName())
            .append(" build is already running");
        if (snapshot.filter != null && !snapshot.filter.isEmpty()) {
            builder.append(" (filter='").append(snapshot.filter).append("')");
        }
        builder.append('.');
        return builder.toString();
    }

    // =========================================================================
    // Pending build state
    // =========================================================================

    public static final class PendingBuild {

        final BuildOwner owner;
        private final List<Item> items;
        private final Map<StackKey, ExpandedStack> expandedStacks =
            new LinkedHashMap<StackKey, ExpandedStack>();
        private final List<ExpandedStack> expandedEntries = new ArrayList<ExpandedStack>();
        private ExpansionState activeExpansion;
        private int nextItemIndex;
        private int currentLanguageIndex;
        private int currentExpandedIndex;
        private String activeLanguage;
        private String activeSavedLanguage;
        private boolean activeLanguageSwitched;
        private LanguageRunStats activeStats;
        private final Map<ItemStack, String> liveDisplayNameHints =
            new IdentityHashMap<ItemStack, String>();
        private String liveDisplayNameHintLanguage;
        private final Map<String, RestoreSnapshot> liveTranslationRestoreSnapshots =
            new LinkedHashMap<String, RestoreSnapshot>();

        private final Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> previousCache;
        private final FullNameCacheMetadata previousMetadata;

        public final int itemsScanned;
        public final long enumMs;
        public long expandMs;
        public int expandSliceCount;
        public int expandBudgetItemStops;
        public int expandBudgetTimeStops;

        final long startMs;
        final String filter;
        final List<String> targetLanguages;
        final boolean mergeWithPreviousCache;
        final Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> collected =
            new LinkedHashMap<PrebuiltSecondaryNameIndexKey, Map<String, String>>();
        final Map<String, Integer> collectedPerLang = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> emptyPerLang = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> rawKeyPerLang = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> goodPerLang = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> cjkSuspectPerLang = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> mixedLangPerLang = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> formatOnlyPerLang = new LinkedHashMap<String, Integer>();
        final Map<String, String> switchModePerLang = new LinkedHashMap<String, String>();
        final Map<String, Long> setupMsPerLang = new LinkedHashMap<String, Long>();
        final Map<String, Long> switchMsPerLang = new LinkedHashMap<String, Long>();
        final Map<String, Long> resolveMsPerLang = new LinkedHashMap<String, Long>();
        final Map<String, Integer> resolveSliceCountPerLang = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> resolveBudgetItemStopsPerLang = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> resolveBudgetTimeStopsPerLang = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> resolveProcessedEntriesPerLang = new LinkedHashMap<String, Integer>();
        final BuildProfiler profiler = new BuildProfiler();
        private SliceTelemetry lastSliceTelemetry = SliceTelemetry.empty();

        PendingBuild(BuildOwner owner, List<Item> items, int itemsScanned, long enumMs, long startMs, String filter,
                List<String> targetLanguages, boolean mergeWithPreviousCache,
                Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> previousCache,
                FullNameCacheMetadata previousMetadata) {
            this.owner = owner == null ? BuildOwner.MANUAL : owner;
            this.items = items == null ? new ArrayList<Item>() : new ArrayList<Item>(items);
            this.itemsScanned = itemsScanned;
            this.enumMs = enumMs;
            this.startMs = startMs;
            this.filter = filter;
            this.targetLanguages = normalizeLanguages(targetLanguages);
            this.mergeWithPreviousCache = mergeWithPreviousCache;
            this.previousCache = deepCopyCache(previousCache);
            this.previousMetadata = previousMetadata;
        }

        public int expandedCount() {
            return expandedStacks.size();
        }

        public boolean isExpansionComplete() {
            return nextItemIndex >= items.size() && activeExpansion == null;
        }

        public ActiveBuildSnapshot snapshot(BuildOwner owner) {
            int totalExpanded = expandedCount();
            int languageCount = targetLanguages.size();
            String currentLanguageName = activeLanguage;
            if (currentLanguageName == null
                && currentLanguageIndex >= 0
                && currentLanguageIndex < targetLanguages.size()) {
                currentLanguageName = targetLanguages.get(currentLanguageIndex);
            }

            return new ActiveBuildSnapshot(
                owner == null ? this.owner : owner,
                determinePhase(),
                filter,
                new ArrayList<String>(targetLanguages),
                mergeWithPreviousCache,
                itemsScanned,
                activeExpansion == null ? nextItemIndex : Math.min(nextItemIndex + 1, itemsScanned),
                totalExpanded,
                currentLanguageName,
                languageCount == 0 ? 0 : Math.min(currentLanguageIndex + 1, languageCount),
                languageCount,
                currentExpandedIndex,
                totalExpanded,
                collected.size(),
                System.currentTimeMillis() - startMs);
        }

        public SliceTelemetry getLastSliceTelemetry() {
            return lastSliceTelemetry;
        }

        private void recordLastSliceTelemetry(BuildPhase phase, String languageCode, long wallNanos,
                int processed, boolean itemBudgetHit, boolean timeBudgetHit) {
            lastSliceTelemetry = new SliceTelemetry(
                phase,
                languageCode,
                nanosToMillis(wallNanos),
                processed,
                itemBudgetHit,
                timeBudgetHit);
        }

        private BuildPhase determinePhase() {
            if (!isExpansionComplete()) {
                return BuildPhase.EXPANDING;
            }
            if (currentLanguageIndex >= targetLanguages.size()) {
                return BuildPhase.WRITING;
            }
            return BuildPhase.RESOLVING;
        }
    }

    public static final class SliceTelemetry {

        private static final SliceTelemetry EMPTY =
            new SliceTelemetry(BuildPhase.EXPANDING, null, 0L, 0, false, false);

        public final BuildPhase phase;
        public final String languageCode;
        public final long wallMs;
        public final int processed;
        public final boolean itemBudgetHit;
        public final boolean timeBudgetHit;

        private SliceTelemetry(BuildPhase phase, String languageCode, long wallMs,
                int processed, boolean itemBudgetHit, boolean timeBudgetHit) {
            this.phase = phase == null ? BuildPhase.EXPANDING : phase;
            this.languageCode = languageCode;
            this.wallMs = Math.max(0L, wallMs);
            this.processed = Math.max(0, processed);
            this.itemBudgetHit = itemBudgetHit;
            this.timeBudgetHit = timeBudgetHit;
        }

        private static SliceTelemetry empty() {
            return EMPTY;
        }
    }

    // =========================================================================
    // Build result
    // =========================================================================

    public static final class BuildResult {

        public final int itemsScanned;
        public final int subitemsExpanded;
        public final int uniqueKeys;
        public final int capturedLiveDisplayNames;
        public final Map<String, Integer> collectedPerLang;
        public final Map<String, Integer> emptyPerLang;
        public final Map<String, Integer> rawKeyPerLang;
        public final Map<String, Integer> goodPerLang;
        public final Map<String, Integer> cjkSuspectPerLang;
        public final Map<String, Integer> mixedLangPerLang;
        public final Map<String, Integer> formatOnlyPerLang;
        public final Map<String, String> switchModePerLang;
        public final Map<String, Long> setupMsPerLang;
        public final Map<String, Long> switchMsPerLang;
        public final Map<String, Long> resolveMsPerLang;
        public final Map<String, Integer> resolveSliceCountPerLang;
        public final Map<String, Integer> resolveBudgetItemStopsPerLang;
        public final Map<String, Integer> resolveBudgetTimeStopsPerLang;
        public final Map<String, Integer> resolveProcessedEntriesPerLang;
        public final long enumMs;
        public final long expandMs;
        public final int expandSliceCount;
        public final int expandBudgetItemStops;
        public final int expandBudgetTimeStops;
        public final long writeMs;
        public final String scanFilter;
        public final long elapsedMs;
        public final BuildProfiler.Report profilerReport;

        BuildResult(int itemsScanned, int subitemsExpanded, int uniqueKeys, int capturedLiveDisplayNames,
                Map<String, Integer> collectedPerLang, Map<String, Integer> emptyPerLang,
                Map<String, Integer> rawKeyPerLang, Map<String, Integer> goodPerLang,
                Map<String, Integer> cjkSuspectPerLang, Map<String, Integer> mixedLangPerLang,
                Map<String, Integer> formatOnlyPerLang, Map<String, String> switchModePerLang,
                Map<String, Long> setupMsPerLang,
                Map<String, Long> switchMsPerLang, Map<String, Long> resolveMsPerLang,
                Map<String, Integer> resolveSliceCountPerLang,
                Map<String, Integer> resolveBudgetItemStopsPerLang,
                Map<String, Integer> resolveBudgetTimeStopsPerLang,
                Map<String, Integer> resolveProcessedEntriesPerLang,
                long enumMs, long expandMs, int expandSliceCount,
                int expandBudgetItemStops, int expandBudgetTimeStops, long writeMs,
                String scanFilter, long elapsedMs, BuildProfiler.Report profilerReport) {
            this.itemsScanned = itemsScanned;
            this.subitemsExpanded = subitemsExpanded;
            this.uniqueKeys = uniqueKeys;
            this.capturedLiveDisplayNames = capturedLiveDisplayNames;
            this.collectedPerLang = collectedPerLang;
            this.emptyPerLang = emptyPerLang;
            this.rawKeyPerLang = rawKeyPerLang;
            this.goodPerLang = goodPerLang;
            this.cjkSuspectPerLang = cjkSuspectPerLang;
            this.mixedLangPerLang = mixedLangPerLang;
            this.formatOnlyPerLang = formatOnlyPerLang;
            this.switchModePerLang = switchModePerLang;
            this.setupMsPerLang = setupMsPerLang;
            this.switchMsPerLang = switchMsPerLang;
            this.resolveMsPerLang = resolveMsPerLang;
            this.resolveSliceCountPerLang = resolveSliceCountPerLang;
            this.resolveBudgetItemStopsPerLang = resolveBudgetItemStopsPerLang;
            this.resolveBudgetTimeStopsPerLang = resolveBudgetTimeStopsPerLang;
            this.resolveProcessedEntriesPerLang = resolveProcessedEntriesPerLang;
            this.enumMs = enumMs;
            this.expandMs = expandMs;
            this.expandSliceCount = expandSliceCount;
            this.expandBudgetItemStops = expandBudgetItemStops;
            this.expandBudgetTimeStops = expandBudgetTimeStops;
            this.writeMs = writeMs;
            this.scanFilter = scanFilter;
            this.elapsedMs = elapsedMs;
            this.profilerReport = profilerReport;
        }

        public int totalEntries() {
            int total = 0;
            for (int value : collectedPerLang.values()) {
                total += value;
            }
            return total;
        }

        public String toSummaryLine() {
            return String.format(
                "items=%d  subitems=%d  keys=%d  entries=%d  time=%dms  filter=%s",
                itemsScanned,
                subitemsExpanded,
                uniqueKeys,
                totalEntries(),
                elapsedMs,
                scanFilter);
        }
    }

    public static final class CompletedBuild {

        private final BuildResult result;
        private final Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> reportData;
        private final long buildStartMs;

        private CompletedBuild(BuildResult result,
                Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> reportData,
                long buildStartMs) {
            this.result = result;
            this.reportData = reportData;
            this.buildStartMs = buildStartMs;
        }

        public BuildResult getResult() {
            return result;
        }
    }

    public static final class ActiveBuildSnapshot {

        public final BuildOwner owner;
        public final BuildPhase phase;
        public final String filter;
        public final List<String> targetLanguages;
        public final boolean mergeWithPreviousCache;
        public final int itemsScanned;
        public final int itemsExpanded;
        public final int uniqueKeysExpanded;
        public final String currentLanguage;
        public final int currentLanguageOrdinal;
        public final int totalLanguages;
        public final int currentLanguageEntriesResolved;
        public final int currentLanguageEntriesTotal;
        public final int collectedKeyCount;
        public final long elapsedMs;

        ActiveBuildSnapshot(BuildOwner owner, BuildPhase phase, String filter,
                List<String> targetLanguages, boolean mergeWithPreviousCache,
                int itemsScanned, int itemsExpanded, int uniqueKeysExpanded,
                String currentLanguage, int currentLanguageOrdinal, int totalLanguages,
                int currentLanguageEntriesResolved, int currentLanguageEntriesTotal,
                int collectedKeyCount, long elapsedMs) {
            this.owner = owner == null ? BuildOwner.MANUAL : owner;
            this.phase = phase == null ? BuildPhase.EXPANDING : phase;
            this.filter = filter == null || filter.trim().isEmpty() ? "all" : filter;
            this.targetLanguages = targetLanguages == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(targetLanguages));
            this.mergeWithPreviousCache = mergeWithPreviousCache;
            this.itemsScanned = itemsScanned;
            this.itemsExpanded = itemsExpanded;
            this.uniqueKeysExpanded = uniqueKeysExpanded;
            this.currentLanguage = currentLanguage;
            this.currentLanguageOrdinal = currentLanguageOrdinal;
            this.totalLanguages = totalLanguages;
            this.currentLanguageEntriesResolved = currentLanguageEntriesResolved;
            this.currentLanguageEntriesTotal = currentLanguageEntriesTotal;
            this.collectedKeyCount = collectedKeyCount;
            this.elapsedMs = elapsedMs;
        }
    }

    // =========================================================================
    // Internal DTOs
    // =========================================================================

    private static final class ActiveBuildState {

        private final BuildOwner owner;
        private final PendingBuild pending;

        private ActiveBuildState(BuildOwner owner, PendingBuild pending) {
            this.owner = owner == null ? BuildOwner.MANUAL : owner;
            this.pending = pending;
        }

        private ActiveBuildSnapshot toSnapshot() {
            return pending.snapshot(owner);
        }
    }

    private static final class ExpansionState {

        private final Item item;
        private final List<CreativeTabs> preferredTabs;
        private final LinkedHashSet<CreativeTabs> attemptedTabs =
            new LinkedHashSet<CreativeTabs>();
        private final List<ItemStack> variants = new ArrayList<ItemStack>();
        private int nextPreferredTabIndex;
        private int nextFallbackTabIndex;
        private int nextVariantIndex;
        private boolean preferredTabsScanned;
        private boolean fallbackTabsScanned;
        private boolean defaultVariantAdded;

        private ExpansionState(Item item) {
            this.item = item;
            this.preferredTabs = collectPreferredCreativeTabs(item);
        }
    }

    private static final class ExpandedStack {

        private final PrebuiltSecondaryNameIndexKey cacheKey;
        private final ItemStack stack;
        private final String liveDisplayName;

        private ExpandedStack(PrebuiltSecondaryNameIndexKey cacheKey, ItemStack stack, String liveDisplayName) {
            this.cacheKey = cacheKey;
            this.stack = stack;
            this.liveDisplayName = liveDisplayName;
        }
    }

    private static final class LanguageRunStats {

        private long setupMs;
        private long resolveMs;
        private long switchMs;
        private SwitchResult switchMode;
        private int sliceCount;
        private int budgetItemStops;
        private int budgetTimeStops;
        private int processedEntries;
        private int okCount;
        private int emptyCount;
        private int rawCount;
        private int goodCount;
        private int cjkSuspectCount;
        private int mixedLangCount;
        private int formatOnlyCount;
    }

    private static final class StackKey {

        private final Item item;
        private final int damage;
        private final int hashCode;

        private StackKey(Item item, int damage) {
            this.item = item;
            this.damage = damage;
            int h = System.identityHashCode(item);
            h = 31 * h + damage;
            this.hashCode = h;
        }

        static StackKey of(ItemStack stack) {
            if (stack == null || stack.getItem() == null) {
                return null;
            }
            return new StackKey(stack.getItem(), stack.getItemDamage());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof StackKey)) {
                return false;
            }
            StackKey other = (StackKey) obj;
            return item == other.item && damage == other.damage;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
