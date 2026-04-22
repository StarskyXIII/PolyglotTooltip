package com.starskyxiii.polyglottooltip.name.prebuilt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import com.starskyxiii.polyglottooltip.Tags;
import com.starskyxiii.polyglottooltip.config.Config;
import com.starskyxiii.polyglottooltip.i18n.LanguageSwitcher;
import com.starskyxiii.polyglottooltip.i18n.LanguageSwitcher.SwitchResult;
import com.starskyxiii.polyglottooltip.name.DisplayNameResolver;
import com.starskyxiii.polyglottooltip.report.BuildReportWriter;
import com.starskyxiii.polyglottooltip.report.NameQuality;
import com.starskyxiii.polyglottooltip.report.NameQualityClassifier;

/**
 * Builds the full-registry name cache by enumerating items, expanding sub-item variants,
 * switching language, resolving names, then writing the merged results to disk.
 *
 * <p>The builder now supports both blocking and budgeted execution:
 * <ul>
 *   <li>Blocking builds are still used by the manual command path.</li>
 *   <li>Budgeted builds are used by auto-bootstrap so the work can be sliced across ticks.</li>
 * </ul>
 */
public final class FullNameCacheBuilder {

    private FullNameCacheBuilder() {}

    // =========================================================================
    // Blocking entry points
    // =========================================================================

    public static BuildResult build(String scanFilter) throws Exception {
        return build(scanFilter, Config.displayLanguages, shouldMergeWithPrevious(scanFilter));
    }

    public static BuildResult build(String scanFilter, List<String> targetLanguages,
            boolean mergeWithPreviousCache) throws Exception {
        PendingBuild pending = startBuild(scanFilter, targetLanguages, mergeWithPreviousCache);
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
        return startBuild(scanFilter, Config.displayLanguages, shouldMergeWithPrevious(scanFilter));
    }

    public static PendingBuild startBuild(String scanFilter, List<String> targetLanguages,
            boolean mergeWithPreviousCache) {
        String filter = normalizeFilter(scanFilter);
        List<String> normalizedLanguages = normalizeLanguages(targetLanguages);

        PolyglotTooltip.LOG.info(
            "[PolyglotTooltips] Full name cache build starting. filter='{}', languages={}, fastSwitch={}, merge={}",
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

        // Bypass the prebuilt fast path while the builder is collecting fresh names.
        FullNameCache.replace(
            new LinkedHashMap<PrebuiltSecondaryNameIndexKey, Map<String, String>>(),
            previousMetadata);

        return new PendingBuild(
            items,
            items.size(),
            enumMs,
            startMs,
            filter.isEmpty() ? "all" : filter,
            normalizedLanguages,
            mergeWithPreviousCache,
            previousCache,
            previousMetadata);
    }

    /**
     * Advances the build within the provided budget.
     *
     * @return true when the entire build has finished resolving every target language
     */
    public static boolean resolveNextSlice(PendingBuild pending, int maxItems, long maxNanos) {
        if (pending == null) {
            return true;
        }

        int safeMaxItems = maxItems <= 0 ? Integer.MAX_VALUE : maxItems;
        long safeMaxNanos = maxNanos <= 0L ? Long.MAX_VALUE : maxNanos;

        if (!pending.isExpansionComplete()) {
            expandNextSlice(pending, safeMaxItems, safeMaxNanos);
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
        }

        return resolveCurrentLanguageSlice(pending, safeMaxItems, safeMaxNanos);
    }

    // =========================================================================
    // Blocking resolution per language
    // =========================================================================

    public static void resolveLanguage(PendingBuild pending, String lang) {
        if (pending == null) {
            throw new IllegalArgumentException("pending build is null");
        }

        ensureExpanded(pending);

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) {
            throw new IllegalStateException("Minecraft client is not ready for language resolution.");
        }

        String savedLanguage = mc.gameSettings.language;
        long switchStart = System.currentTimeMillis();
        SwitchResult sr = LanguageSwitcher.switchTo(mc, lang, Config.useFastLanguageSwitch);
        long switchMs = System.currentTimeMillis() - switchStart;
        if (sr == SwitchResult.FAILED) {
            throw new IllegalStateException("Failed to switch to language '" + lang + "'.");
        }

        PolyglotTooltip.LOG.info(
            "[PolyglotTooltips] Language switched to '{}' via {} path ({}ms).",
            lang,
            sr.name(),
            switchMs);

        LanguageRunStats stats = new LanguageRunStats();
        long resolveStart = System.currentTimeMillis();
        try {
            for (int i = 0; i < pending.expandedEntries.size(); i++) {
                processExpandedStack(pending, pending.expandedEntries.get(i), lang, stats);
            }
        } finally {
            LanguageSwitcher.switchTo(mc, savedLanguage, Config.useFastLanguageSwitch);
            PolyglotTooltip.LOG.info("[PolyglotTooltips] Language restored to '{}'.", savedLanguage);
        }

        stats.switchMs = switchMs;
        stats.switchMode = sr;
        stats.resolveMs = System.currentTimeMillis() - resolveStart;
        finalizeLanguage(pending, lang, stats);
    }

    // =========================================================================
    // Finish / cancel
    // =========================================================================

    public static BuildResult finishBuild(PendingBuild pending) throws Exception {
        if (pending == null) {
            throw new IllegalArgumentException("pending build is null");
        }

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
            pending.collectedPerLang,
            pending.emptyPerLang,
            pending.rawKeyPerLang,
            pending.goodPerLang,
            pending.cjkSuspectPerLang,
            pending.mixedLangPerLang,
            pending.formatOnlyPerLang,
            pending.switchModePerLang,
            pending.switchMsPerLang,
            pending.resolveMsPerLang,
            pending.enumMs,
            pending.expandMs,
            writeMs,
            pending.filter,
            elapsedMs);

        try {
            BuildReportWriter.write(result, dataToWrite);
        } catch (Exception e) {
            PolyglotTooltip.LOG.warn("[PolyglotTooltips] Report write failed (non-fatal): {}", e.getMessage());
        }

        PolyglotTooltip.LOG.info(
            "[PolyglotTooltips] Build complete in {}ms  (enum={}ms  expand={}ms  write={}ms).",
            elapsedMs,
            pending.enumMs,
            pending.expandMs,
            writeMs);

        return result;
    }

    public static void cancelBuild(PendingBuild pending) {
        if (pending == null) {
            return;
        }

        restoreActiveLanguage(pending, Minecraft.getMinecraft());
        FullNameCache.replace(pending.previousCache, pending.previousMetadata);
        PolyglotTooltip.LOG.warn("[PolyglotTooltips] Build cancelled; previous cache restored.");
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
        int processed = 0;

        while (pending.nextItemIndex < pending.items.size()) {
            Item item = pending.items.get(pending.nextItemIndex++);
            expandItem(item, pending.expandedStacks);
            processed++;

            if (processed >= maxItems || isBudgetExceeded(startNs, maxNanos)) {
                break;
            }
        }

        pending.expandMs += nanosToMillis(System.nanoTime() - startNs);

        if (pending.isExpansionComplete() && pending.expandedEntries == null) {
            pending.expandedEntries = new ArrayList<ExpandedStack>(pending.expandedStacks.values());
            PolyglotTooltip.LOG.info(
                "[PolyglotTooltips] Unique item+damage pairs: {}  ({}ms)",
                pending.expandedEntries.size(),
                pending.expandMs);
        }
    }

    private static void expandItem(Item item, Map<StackKey, ExpandedStack> out) {
        if (item == null) {
            return;
        }

        List<ItemStack> variants = new ArrayList<ItemStack>();
        addSubItems(item, item.getCreativeTab(), variants);
        for (CreativeTabs tab : CreativeTabs.creativeTabArray) {
            addSubItems(item, tab, variants);
        }

        if (variants.isEmpty()) {
            variants.add(new ItemStack(item, 1, 0));
        }

        for (ItemStack stack : variants) {
            if (stack == null || stack.getItem() == null) {
                continue;
            }

            String registryName = getRegistryName(stack.getItem());
            if (registryName == null || registryName.isEmpty()) {
                continue;
            }

            StackKey stackKey = StackKey.of(stack);
            if (stackKey != null && !out.containsKey(stackKey)) {
                out.put(
                    stackKey,
                    new ExpandedStack(
                        new PrebuiltSecondaryNameIndexKey(registryName, stack.getItemDamage()),
                        stack.copy()));
            }
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

        PolyglotTooltip.LOG.info(
            "[PolyglotTooltips] Auto-build: resolving '{}' ({}/{})...",
            lang,
            pending.currentLanguageIndex + 1,
            pending.targetLanguages.size());

        if (sameLanguage(pending.activeSavedLanguage, lang)) {
            pending.activeStats.switchMode = SwitchResult.FAST;
            PolyglotTooltip.LOG.info(
                "[PolyglotTooltips] Language '{}' already active; reusing current locale for budgeted build.",
                lang);
            return;
        }

        long switchStart = System.currentTimeMillis();
        SwitchResult sr = LanguageSwitcher.switchTo(mc, lang, Config.useFastLanguageSwitch);
        long switchMs = System.currentTimeMillis() - switchStart;
        if (sr == SwitchResult.FAILED) {
            throw new IllegalStateException("Failed to switch to language '" + lang + "'.");
        }

        pending.activeStats.switchMs += switchMs;
        pending.activeStats.switchMode = combineSwitchModes(pending.activeStats.switchMode, sr);
        pending.activeLanguageSwitched = true;
        PolyglotTooltip.LOG.info(
            "[PolyglotTooltips] Language switched to '{}' via {} path ({}ms).",
            lang,
            sr.name(),
            switchMs);
    }

    private static boolean resolveCurrentLanguageSlice(PendingBuild pending, int maxItems, long maxNanos) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) {
            throw new IllegalStateException("Minecraft client is not ready for language resolution.");
        }

        String lang = pending.activeLanguage;
        long sliceStartNs = System.nanoTime();
        int processed = 0;
        while (pending.currentExpandedIndex < pending.expandedEntries.size()) {
            ExpandedStack expanded = pending.expandedEntries.get(pending.currentExpandedIndex++);
            processExpandedStack(pending, expanded, lang, pending.activeStats);
            processed++;

            if (processed >= maxItems || isBudgetExceeded(sliceStartNs, maxNanos)) {
                break;
            }
        }

        pending.activeStats.resolveMs += nanosToMillis(System.nanoTime() - sliceStartNs);

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
        pending.switchMsPerLang.put(lang, Long.valueOf(stats.switchMs));
        pending.resolveMsPerLang.put(lang, Long.valueOf(stats.resolveMs));
        pending.collectedPerLang.put(lang, Integer.valueOf(stats.okCount));
        pending.emptyPerLang.put(lang, Integer.valueOf(stats.emptyCount));
        pending.rawKeyPerLang.put(lang, Integer.valueOf(stats.rawCount));
        pending.goodPerLang.put(lang, Integer.valueOf(stats.goodCount));
        pending.cjkSuspectPerLang.put(lang, Integer.valueOf(stats.cjkSuspectCount));
        pending.mixedLangPerLang.put(lang, Integer.valueOf(stats.mixedLangCount));
        pending.formatOnlyPerLang.put(lang, Integer.valueOf(stats.formatOnlyCount));

        PolyglotTooltip.LOG.info(
            "[PolyglotTooltips] lang={}: collected={}, good={}, empty={}, rawKey={}, cjkSuspect={}, mixed={}, formatOnly={}  resolve={}ms",
            lang,
            stats.okCount,
            stats.goodCount,
            stats.emptyCount,
            stats.rawCount,
            stats.cjkSuspectCount,
            stats.mixedLangCount,
            stats.formatOnlyCount,
            stats.resolveMs);
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
                "[PolyglotTooltips] Failed to restore language '{}'.",
                restoreLanguage);
            return;
        }

        pending.activeStats.switchMs += restoreMs;
        pending.activeStats.switchMode = combineSwitchModes(pending.activeStats.switchMode, restoreResult);
        pending.activeLanguageSwitched = false;
        PolyglotTooltip.LOG.info(
            "[PolyglotTooltips] Language restored to '{}' via {} path ({}ms).",
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
        return safeGetDisplayName(stack);
    }

    private static String safeGetDisplayName(ItemStack stack) {
        try {
            String name = stack.getDisplayName();
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

        return SwitchResult.FAST;
    }

    private static boolean sameLanguage(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    // =========================================================================
    // Pending build state
    // =========================================================================

    public static final class PendingBuild {

        private final List<Item> items;
        private final Map<StackKey, ExpandedStack> expandedStacks =
            new LinkedHashMap<StackKey, ExpandedStack>();
        private List<ExpandedStack> expandedEntries;
        private int nextItemIndex;
        private int currentLanguageIndex;
        private int currentExpandedIndex;
        private String activeLanguage;
        private String activeSavedLanguage;
        private boolean activeLanguageSwitched;
        private LanguageRunStats activeStats;

        private final Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> previousCache;
        private final FullNameCacheMetadata previousMetadata;

        public final int itemsScanned;
        public final long enumMs;
        public long expandMs;

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
        final Map<String, Long> switchMsPerLang = new LinkedHashMap<String, Long>();
        final Map<String, Long> resolveMsPerLang = new LinkedHashMap<String, Long>();

        PendingBuild(List<Item> items, int itemsScanned, long enumMs, long startMs, String filter,
                List<String> targetLanguages, boolean mergeWithPreviousCache,
                Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> previousCache,
                FullNameCacheMetadata previousMetadata) {
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
            if (expandedEntries != null) {
                return expandedEntries.size();
            }
            return expandedStacks.size();
        }

        public boolean isExpansionComplete() {
            return nextItemIndex >= items.size();
        }
    }

    // =========================================================================
    // Build result
    // =========================================================================

    public static final class BuildResult {

        public final int itemsScanned;
        public final int subitemsExpanded;
        public final int uniqueKeys;
        public final Map<String, Integer> collectedPerLang;
        public final Map<String, Integer> emptyPerLang;
        public final Map<String, Integer> rawKeyPerLang;
        public final Map<String, Integer> goodPerLang;
        public final Map<String, Integer> cjkSuspectPerLang;
        public final Map<String, Integer> mixedLangPerLang;
        public final Map<String, Integer> formatOnlyPerLang;
        public final Map<String, String> switchModePerLang;
        public final Map<String, Long> switchMsPerLang;
        public final Map<String, Long> resolveMsPerLang;
        public final long enumMs;
        public final long expandMs;
        public final long writeMs;
        public final String scanFilter;
        public final long elapsedMs;

        BuildResult(int itemsScanned, int subitemsExpanded, int uniqueKeys,
                Map<String, Integer> collectedPerLang, Map<String, Integer> emptyPerLang,
                Map<String, Integer> rawKeyPerLang, Map<String, Integer> goodPerLang,
                Map<String, Integer> cjkSuspectPerLang, Map<String, Integer> mixedLangPerLang,
                Map<String, Integer> formatOnlyPerLang, Map<String, String> switchModePerLang,
                Map<String, Long> switchMsPerLang, Map<String, Long> resolveMsPerLang,
                long enumMs, long expandMs, long writeMs,
                String scanFilter, long elapsedMs) {
            this.itemsScanned = itemsScanned;
            this.subitemsExpanded = subitemsExpanded;
            this.uniqueKeys = uniqueKeys;
            this.collectedPerLang = collectedPerLang;
            this.emptyPerLang = emptyPerLang;
            this.rawKeyPerLang = rawKeyPerLang;
            this.goodPerLang = goodPerLang;
            this.cjkSuspectPerLang = cjkSuspectPerLang;
            this.mixedLangPerLang = mixedLangPerLang;
            this.formatOnlyPerLang = formatOnlyPerLang;
            this.switchModePerLang = switchModePerLang;
            this.switchMsPerLang = switchMsPerLang;
            this.resolveMsPerLang = resolveMsPerLang;
            this.enumMs = enumMs;
            this.expandMs = expandMs;
            this.writeMs = writeMs;
            this.scanFilter = scanFilter;
            this.elapsedMs = elapsedMs;
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

    // =========================================================================
    // Internal DTOs
    // =========================================================================

    private static final class ExpandedStack {

        private final PrebuiltSecondaryNameIndexKey cacheKey;
        private final ItemStack stack;

        private ExpandedStack(PrebuiltSecondaryNameIndexKey cacheKey, ItemStack stack) {
            this.cacheKey = cacheKey;
            this.stack = stack;
        }
    }

    private static final class LanguageRunStats {

        private long resolveMs;
        private long switchMs;
        private SwitchResult switchMode;
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
