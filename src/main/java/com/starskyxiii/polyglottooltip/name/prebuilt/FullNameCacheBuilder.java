package com.starskyxiii.polyglottooltip.name.prebuilt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import com.starskyxiii.polyglottooltip.config.Config;
import com.starskyxiii.polyglottooltip.i18n.LanguageSwitcher;
import com.starskyxiii.polyglottooltip.i18n.LanguageSwitcher.SwitchResult;
import com.starskyxiii.polyglottooltip.name.DisplayNameResolver;
import com.starskyxiii.polyglottooltip.report.BuildReportWriter;
import com.starskyxiii.polyglottooltip.report.NameQuality;
import com.starskyxiii.polyglottooltip.report.NameQualityClassifier;

/**
 * Builds the full-registry name cache by enumerating all items (or a filtered subset),
 * switching language once per language, resolving display names via the existing
 * DisplayNameResolver chain, then writing results to FullNameCache + disk.
 *
 * Scan filter:
 *   null / "" / "all" → all mods
 *   "manametalmod"    → only items whose registry name starts with "manametalmod:"
 *
 * Two usage modes:
 *
 * 1. Blocking (/ptbuild command):
 *      BuildResult r = FullNameCacheBuilder.build(filter);
 *
 * 2. Phased (AutoFullNameCacheBootstrap):
 *      PendingBuild p = FullNameCacheBuilder.startBuild(filter);  // FMLLoadCompleteEvent
 *      FullNameCacheBuilder.resolveLanguage(p, lang);             // one per ClientTickEvent
 *      BuildResult r = FullNameCacheBuilder.finishBuild(p);       // final tick
 *      // or on error: FullNameCacheBuilder.cancelBuild(p);
 *
 * IMPORTANT: Must only be called from explicit command paths or tick events,
 * never from tooltip or render paths.
 */
public final class FullNameCacheBuilder {

    private FullNameCacheBuilder() {}

    // =========================================================================
    // Blocking entry point (used by /ptbuild command)
    // =========================================================================

    /**
     * Runs a full prebuild for the given scan filter, blocking until complete.
     *
     * @param scanFilter registry prefix filter, or null/"all" for everything
     * @return build result with stats
     * @throws Exception on fatal I/O error writing the cache file
     */
    public static BuildResult build(String scanFilter) throws Exception {
        List<String> targetLanguages = new ArrayList<String>(Config.displayLanguages);
        PendingBuild pending = startBuild(scanFilter);
        try {
            for (String lang : targetLanguages) {
                resolveLanguage(pending, lang);
            }
            return finishBuild(pending);
        } catch (Exception e) {
            cancelBuild(pending);
            throw e;
        }
    }

    // =========================================================================
    // Phased entry points (used by AutoFullNameCacheBootstrap)
    // =========================================================================

    /**
     * Phase 1: enumerate items from the registry and expand all sub-item variants.
     *
     * Safe to call during FMLLoadCompleteEvent (main thread, loading screen still visible).
     * Does not touch the language system. Returns a PendingBuild that must later be
     * passed to resolveLanguage() + finishBuild(), or cancelBuild() on error.
     */
    public static PendingBuild startBuild(String scanFilter) {
        String filter = normalizeFilter(scanFilter);

        PolyglotTooltip.LOG.info(
            "[PolyglotTooltips] Full name cache build starting. filter='{}', languages={}, fastSwitch={}",
            filter.isEmpty() ? "all" : filter,
            Config.displayLanguages,
            Config.useFastLanguageSwitch);

        long startMs = System.currentTimeMillis();

        long enumStart = System.currentTimeMillis();
        List<Item> items = collectItems(filter);
        long enumMs = System.currentTimeMillis() - enumStart;
        PolyglotTooltip.LOG.info("[PolyglotTooltips] Items matching filter: {}  ({}ms)", items.size(), enumMs);

        long expandStart = System.currentTimeMillis();
        Map<StackKey, ItemStack> expandedStacks = expandAllSubItems(items);
        long expandMs = System.currentTimeMillis() - expandStart;
        PolyglotTooltip.LOG.info("[PolyglotTooltips] Unique item+damage pairs: {}  ({}ms)",
            expandedStacks.size(), expandMs);

        // Snapshot and clear the full cache so resolvers bypass it during the build.
        Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> previousCache = FullNameCache.snapshot();
        FullNameCache.replace(Collections.<PrebuiltSecondaryNameIndexKey, Map<String, String>>emptyMap());

        return new PendingBuild(items.size(), enumMs, expandMs, startMs,
            filter.isEmpty() ? "all" : filter, expandedStacks, previousCache);
    }

    /**
     * Phase 2: switch to the given language, resolve all expanded items, then switch back.
     *
     * Call once per target language. Restores the original language in a finally block
     * regardless of outcome. Safe to call from ClientTickEvent (main thread).
     */
    public static void resolveLanguage(PendingBuild pending, String lang) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) {
            throw new IllegalStateException("Minecraft client is not ready for language resolution.");
        }
        String savedLanguage = mc.gameSettings.language;

        long switchStart = System.currentTimeMillis();
        SwitchResult sr = LanguageSwitcher.switchTo(mc, lang, Config.useFastLanguageSwitch);
        long switchMs = System.currentTimeMillis() - switchStart;
        pending.switchModePerLang.put(lang, sr.name());
        pending.switchMsPerLang.put(lang, switchMs);
        PolyglotTooltip.LOG.info("[PolyglotTooltips] Language switched to '{}' via {} path ({}ms).",
            lang, sr.name(), switchMs);
        if (sr == SwitchResult.FAILED) {
            throw new IllegalStateException("Failed to switch to language '" + lang + "'.");
        }

        int okCount = 0, emptyCount = 0, rawCount = 0, goodCount = 0;
        int cjkSuspectCount = 0, mixedLangCount = 0, formatOnlyCount = 0;

        long resolveStart = System.currentTimeMillis();
        try {
            for (Map.Entry<StackKey, ItemStack> entry : pending.expandedStacks.entrySet()) {
                ItemStack stack = entry.getValue();
                if (stack == null || stack.getItem() == null) continue;

                String regName = getRegistryName(stack.getItem());
                if (regName == null) continue;

                String displayName = resolveDisplayName(stack, lang);
                if (displayName == null || displayName.isEmpty()) { emptyCount++; continue; }

                NameQuality quality = NameQualityClassifier.classify(displayName, lang);
                switch (quality) {
                    case RAW_KEY:        rawCount++; break;
                    case CONTAINS_CJK:   cjkSuspectCount++; break;
                    case MIXED_LANGUAGE: mixedLangCount++; break;
                    case FORMAT_ONLY:    formatOnlyCount++; break;
                    default:             goodCount++; break;
                }

                PrebuiltSecondaryNameIndexKey key =
                    new PrebuiltSecondaryNameIndexKey(regName, stack.getItemDamage());
                Map<String, String> langs = pending.collected.get(key);
                if (langs == null) {
                    langs = new HashMap<String, String>();
                    pending.collected.put(key, langs);
                }
                langs.put(lang, displayName);
                okCount++;
            }
        } finally {
            LanguageSwitcher.switchTo(mc, savedLanguage, Config.useFastLanguageSwitch);
            PolyglotTooltip.LOG.info("[PolyglotTooltips] Language restored to '{}'.", savedLanguage);
        }

        long resolveMs = System.currentTimeMillis() - resolveStart;
        pending.resolveMsPerLang.put(lang, resolveMs);
        pending.collectedPerLang.put(lang, okCount);
        pending.emptyPerLang.put(lang, emptyCount);
        pending.rawKeyPerLang.put(lang, rawCount);
        pending.goodPerLang.put(lang, goodCount);
        pending.cjkSuspectPerLang.put(lang, cjkSuspectCount);
        pending.mixedLangPerLang.put(lang, mixedLangCount);
        pending.formatOnlyPerLang.put(lang, formatOnlyCount);

        PolyglotTooltip.LOG.info(
            "[PolyglotTooltips] lang={}: collected={}, good={}, empty={}, rawKey={}, "
            + "cjkSuspect={}, mixed={}, formatOnly={}  resolve={}ms",
            lang, okCount, goodCount, emptyCount, rawCount,
            cjkSuspectCount, mixedLangCount, formatOnlyCount, resolveMs);
    }

    /**
     * Phase 3: write the accumulated results to disk and update the in-memory cache.
     *
     * @throws Exception on fatal I/O error (cache was not written)
     */
    public static BuildResult finishBuild(PendingBuild pending) throws Exception {
        long writeStart = System.currentTimeMillis();
        FullNameCacheIO.write(pending.collected);
        FullNameCache.replace(pending.collected);
        long writeMs = System.currentTimeMillis() - writeStart;
        PolyglotTooltip.LOG.info("[PolyglotTooltips] Cache written ({}ms).", writeMs);

        long elapsedMs = System.currentTimeMillis() - pending.startMs;

        BuildResult result = new BuildResult(
            pending.itemsScanned,
            pending.expandedStacks.size(),
            pending.collected.size(),
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
            BuildReportWriter.write(result, pending.collected);
        } catch (Exception e) {
            PolyglotTooltip.LOG.warn("[PolyglotTooltips] Report write failed (non-fatal): {}", e.getMessage());
        }

        PolyglotTooltip.LOG.info(
            "[PolyglotTooltips] Build complete in {}ms  (enum={}ms  expand={}ms  write={}ms).",
            elapsedMs, pending.enumMs, pending.expandMs, writeMs);

        return result;
    }

    /**
     * Error path: discards accumulated results and restores the previous cache.
     */
    public static void cancelBuild(PendingBuild pending) {
        FullNameCache.replace(pending.previousCache);
        PolyglotTooltip.LOG.warn("[PolyglotTooltips] Build cancelled; previous cache restored.");
    }

    // =========================================================================
    // Item collection
    // =========================================================================

    private static List<Item> collectItems(String filter) {
        List<Item> result = new ArrayList<Item>();
        for (Object obj : Item.itemRegistry) {
            if (!(obj instanceof Item)) continue;
            Item item = (Item) obj;
            if (!filter.isEmpty()) {
                String reg = getRegistryName(item);
                if (reg == null || !reg.startsWith(filter)) continue;
            }
            result.add(item);
        }
        return result;
    }

    /**
     * Expands each item into its sub-item variants.
     *
     * Strategy:
     * 1. Call getSubItems() with the item's own creative tab.
     * 2. Call getSubItems() with every creative tab (catches items in multiple tabs).
     * 3. If still no variants found, fall back to damage=0.
     *
     * Dedup via StackKey (item identity + damage).
     */
    private static Map<StackKey, ItemStack> expandAllSubItems(List<Item> items) {
        Map<StackKey, ItemStack> result = new LinkedHashMap<StackKey, ItemStack>();
        for (Item item : items) {
            expandItem(item, result);
        }
        return result;
    }

    private static void expandItem(Item item, Map<StackKey, ItemStack> out) {
        List<ItemStack> variants = new ArrayList<ItemStack>();

        addSubItems(item, item.getCreativeTab(), variants);
        for (CreativeTabs tab : CreativeTabs.creativeTabArray) {
            addSubItems(item, tab, variants);
        }

        if (variants.isEmpty()) {
            variants.add(new ItemStack(item, 1, 0));
        }

        for (ItemStack stack : variants) {
            if (stack == null || stack.getItem() == null) continue;
            StackKey key = StackKey.of(stack);
            if (key != null && !out.containsKey(key)) {
                out.put(key, stack.copy());
            }
        }
    }

    private static void addSubItems(Item item, CreativeTabs tab, List<ItemStack> target) {
        if (item == null || tab == null) return;
        try {
            item.getSubItems(item, tab, target);
        } catch (Throwable ignored) {
            // Some items have buggy getSubItems — skip silently
        }
    }

    // =========================================================================
    // Name resolution
    // =========================================================================

    private static String resolveDisplayName(ItemStack stack, String languageCode) {
        try {
            String resolved = DisplayNameResolver.resolveSecondaryDisplayNameForLanguage(stack, languageCode);
            if (resolved != null && !resolved.isEmpty()) {
                return resolved;
            }
        } catch (Throwable ignored) {
            // Fall through to stack.getDisplayName() fallback
        }
        return safeGetDisplayName(stack);
    }

    private static String safeGetDisplayName(ItemStack stack) {
        try {
            String name = stack.getDisplayName();
            if (name == null) return null;
            String stripped = EnumChatFormatting.getTextWithoutFormattingCodes(name);
            String result = stripped != null ? stripped.trim() : name.trim();
            return result.isEmpty() ? null : result;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String getRegistryName(Item item) {
        if (item == null) return null;
        try {
            Object name = Item.itemRegistry.getNameForObject(item);
            return name == null ? null : String.valueOf(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String normalizeFilter(String filter) {
        if (filter == null) return "";
        String f = filter.trim();
        if (f.equalsIgnoreCase("all") || f.isEmpty()) return "";
        if (!f.contains(":")) f = f + ":";
        return f;
    }

    // =========================================================================
    // PendingBuild — holds all accumulated state across phased build steps
    // =========================================================================

    public static final class PendingBuild {

        // Private — only accessed by FullNameCacheBuilder methods
        private final Map<StackKey, ItemStack> expandedStacks;
        private final Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> previousCache;

        // Public stats
        public final int itemsScanned;
        public final long enumMs;
        public final long expandMs;

        // Package-private — filled by resolveLanguage(), read by finishBuild()
        final long startMs;
        final String filter;
        final Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> collected =
            new LinkedHashMap<PrebuiltSecondaryNameIndexKey, Map<String, String>>();
        final Map<String, Integer> collectedPerLang = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> emptyPerLang     = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> rawKeyPerLang    = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> goodPerLang      = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> cjkSuspectPerLang = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> mixedLangPerLang = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> formatOnlyPerLang = new LinkedHashMap<String, Integer>();
        final Map<String, String>  switchModePerLang = new LinkedHashMap<String, String>();
        final Map<String, Long>    switchMsPerLang   = new LinkedHashMap<String, Long>();
        final Map<String, Long>    resolveMsPerLang  = new LinkedHashMap<String, Long>();

        PendingBuild(int itemsScanned, long enumMs, long expandMs, long startMs,
                String filter,
                Map<StackKey, ItemStack> expandedStacks,
                Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> previousCache) {
            this.itemsScanned = itemsScanned;
            this.enumMs = enumMs;
            this.expandMs = expandMs;
            this.startMs = startMs;
            this.filter = filter;
            this.expandedStacks = expandedStacks;
            this.previousCache = previousCache;
        }

        /** Number of unique item+damage pairs ready for resolution. */
        public int expandedCount() {
            return expandedStacks.size();
        }
    }

    // =========================================================================
    // BuildResult
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
            int t = 0;
            for (int v : collectedPerLang.values()) t += v;
            return t;
        }

        public String toSummaryLine() {
            return String.format(
                "items=%d  subitems=%d  keys=%d  entries=%d  time=%dms  filter=%s",
                itemsScanned, subitemsExpanded, uniqueKeys, totalEntries(), elapsedMs, scanFilter);
        }
    }

    // =========================================================================
    // StackKey — dedup key for item variant collection
    // =========================================================================

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
            if (stack == null || stack.getItem() == null) return null;
            return new StackKey(stack.getItem(), stack.getItemDamage());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof StackKey)) return false;
            StackKey o = (StackKey) obj;
            return item == o.item && damage == o.damage;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
