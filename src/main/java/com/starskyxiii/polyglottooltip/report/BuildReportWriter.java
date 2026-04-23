package com.starskyxiii.polyglottooltip.report;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.client.Minecraft;

import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import com.starskyxiii.polyglottooltip.name.prebuilt.BuildProfiler;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder.BuildResult;
import com.starskyxiii.polyglottooltip.name.prebuilt.PrebuiltSecondaryNameIndexKey;

/**
 * Writes output files after a successful /polyglotbuild:
 *
 * build-report.txt    — human-readable analysis with per-language and per-mod stats
 * build-summary.tsv   — machine-readable per-modid table with quality breakdown per language
 * suspect-entries.tsv — per-entry listing of all non-GOOD collected names for triage
 *
 * All files go to: .minecraft/polyglottooltip/report/
 */
public final class BuildReportWriter {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String REPORT_DIR = "polyglottooltip/report";
    private static final String NL = System.getProperty("line.separator");

    private BuildReportWriter() {}

    public static File getReportDir(String scanFilter) {
        Minecraft mc = Minecraft.getMinecraft();
        File root = mc != null ? mc.mcDataDir : new File(".");
        File base = new File(root, REPORT_DIR);
        if (scanFilter == null || scanFilter.isEmpty() || "all".equals(scanFilter)) {
            return base;
        }
        // normalizeFilter() appends ":" to use as a registry prefix — strip it for directory name.
        String dirName = scanFilter.endsWith(":") ? scanFilter.substring(0, scanFilter.length() - 1) : scanFilter;
        return new File(base, dirName);
    }

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    public static void write(BuildResult result,
            Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> data) throws Exception {
        File dir = getReportDir(result.scanFilter);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Cannot create report dir: " + dir.getAbsolutePath());
        }

        List<String> langs = new ArrayList<String>(result.collectedPerLang.keySet());
        Map<String, ModStats> modStats = buildModStats(data, langs);

        writeTextReport(result, data, modStats, new File(dir, "build-report.txt"));
        writeSummaryTsv(result, modStats, new File(dir, "build-summary.tsv"));
        writeSuspectEntriesTsv(data, new File(dir, "suspect-entries.tsv"));
        if (hasProfilerData(result)) {
            writeProfilerSummaryTsv(result.profilerReport, new File(dir, "build-profiler-summary.tsv"));
            writeProfilerHotspotsTsv(result.profilerReport, new File(dir, "build-profiler-hotspots.tsv"));
        }

        PolyglotTooltip.LOG.info("[PolyglotTooltips] Build reports written to: {}", dir.getAbsolutePath());
    }

    // -------------------------------------------------------------------------
    // build-report.txt
    // -------------------------------------------------------------------------

    private static void writeTextReport(BuildResult r,
            Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> data,
            Map<String, ModStats> modStats, File out) throws Exception {

        List<String> langs = new ArrayList<String>(r.collectedPerLang.keySet());

        Writer w = null;
        try {
            w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), UTF8));

            line(w, "=================================================================");
            line(w, "  PolyglotTooltips — Full Name Cache Build Report");
            line(w, "=================================================================");
            line(w, "  Filter      : " + r.scanFilter);
            line(w, "  Elapsed     : " + r.elapsedMs + " ms");
            line(w, "  Languages   : " + langs);
            line(w, "-----------------------------------------------------------------");
            line(w, "");

            line(w, "[ Item Counts ]");
            line(w, "  Items in registry (matched filter) : " + r.itemsScanned);
            line(w, "  Unique item+damage pairs            : " + r.subitemsExpanded);
            line(w, "  Keys with >=1 collected name        : " + r.uniqueKeys);
            line(w, "  Total (key x lang) entries          : " + r.totalEntries());
            line(w, "");

            int totalPossible = r.subitemsExpanded * langs.size();
            int totalCollected = r.totalEntries();
            int totalGood = sum(r.goodPerLang);
            int totalEmpty = sum(r.emptyPerLang);
            int totalRaw = sum(r.rawKeyPerLang);
            int totalCjkSuspect = sum(r.cjkSuspectPerLang);
            int totalMixed = sum(r.mixedLangPerLang);
            int totalFormatOnly = sum(r.formatOnlyPerLang);
            int totalSuspect = totalRaw + totalCjkSuspect + totalMixed + totalFormatOnly;

            double collectedPct = totalPossible > 0 ? (totalCollected * 100.0 / totalPossible) : 0.0;
            double goodPct = totalPossible > 0 ? (totalGood * 100.0 / totalPossible) : 0.0;

            line(w, "[ Overall Coverage ]");
            line(w, String.format("  Collected coverage       : %d / %d  (%.1f%%)",
                totalCollected, totalPossible, collectedPct));
            line(w, String.format("  Good-quality coverage    : %d / %d  (%.1f%%)",
                totalGood, totalPossible, goodPct));
            line(w, String.format("  Missing (empty/null)     : %d", totalEmpty));
            line(w, String.format(
                "  Suspect total            : %d  (raw_key=%d  cjk_suspect=%d  mixed=%d  format_only=%d)",
                totalSuspect, totalRaw, totalCjkSuspect, totalMixed, totalFormatOnly));
            line(w, "");

            line(w, "[ Per-Language Quality Breakdown ]");
            line(w, String.format("  %-12s  %8s  %8s  %8s  %8s  %8s  %8s  %8s  %s",
                "Language", "Collected", "GOOD", "EMPTY", "RAW_KEY", "CJK_SUS", "MIXED", "FMT_ONLY", "Mode"));
            line(w, "  " + repeat("-", 96));
            for (String lang : langs) {
                int collected = safeGet(r.collectedPerLang, lang);
                int good = safeGet(r.goodPerLang, lang);
                int empty = safeGet(r.emptyPerLang, lang);
                int rawKey = safeGet(r.rawKeyPerLang, lang);
                int cjkSus = safeGet(r.cjkSuspectPerLang, lang);
                int mixed = safeGet(r.mixedLangPerLang, lang);
                int fmtOnly = safeGet(r.formatOnlyPerLang, lang);
                String mode = r.switchModePerLang.containsKey(lang) ? r.switchModePerLang.get(lang) : "?";
                boolean isCjkLang = NameQualityClassifier.isCjkLanguage(lang);
                String cjkSusDisplay = isCjkLang ? "  n/a   " : String.valueOf(cjkSus);
                line(w, String.format("  %-12s  %8d  %8d  %8d  %8d  %8s  %8d  %8d  %s",
                    lang, collected, good, empty, rawKey, cjkSusDisplay, mixed, fmtOnly, mode));
            }
            line(w, "");

            line(w, "[ Per-Mod Quality Breakdown ]");
            StringBuilder hdr = new StringBuilder();
            hdr.append(String.format("  %-32s  %8s", "ModId", "Subitems"));
            for (String lang : langs) {
                hdr.append("  ").append(String.format("%-12s", lang + "_good%"));
                hdr.append("  ").append(String.format("%-8s", "raw"));
                hdr.append("  ").append(String.format("%-8s", "mixed"));
                hdr.append("  ").append(String.format("%-8s", "cjk_sus"));
            }
            line(w, hdr.toString());
            line(w, "  " + repeat("-", 100));

            List<String> sortedMods = new ArrayList<String>(modStats.keySet());
            Collections.sort(sortedMods);
            for (String modId : sortedMods) {
                ModStats ms = modStats.get(modId);
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("  %-32s  %8d", modId, ms.subitems));
                for (String lang : langs) {
                    int good = safeGet(ms.goodPerLang, lang);
                    int raw = safeGet(ms.rawKeyPerLang, lang);
                    int mixed = safeGet(ms.mixedLangPerLang, lang);
                    int cjkSus = safeGet(ms.cjkSuspectPerLang, lang);
                    double goodPctMod = ms.subitems > 0 ? (good * 100.0 / ms.subitems) : 0.0;
                    sb.append(String.format("  %11.1f%%", goodPctMod));
                    sb.append(String.format("  %8d", raw));
                    sb.append(String.format("  %8d", mixed));
                    sb.append(String.format("  %8d", cjkSus));
                }
                line(w, sb.toString());
            }
            line(w, "");

            int singleDamageItems = countSingleDamageItems(data);
            line(w, "[ Expansion Diagnostics ]");
            line(w, "  Items with only damage=0 in cache : " + singleDamageItems
                + "  (may indicate items that did not expand via getSubItems)");
            line(w, "  Captured live-name hints          : " + r.capturedLiveDisplayNames);
            line(w, "");

            line(w, "[ Timing Breakdown ]");
            line(w, "  Total elapsed  : " + r.elapsedMs + " ms");
            line(w, "  Item enum      : " + r.enumMs + " ms");
            line(w, "  Sub-item expand: " + r.expandMs + " ms");
            double expandAvgPairs = r.expandSliceCount > 0
                ? (r.subitemsExpanded * 1.0D / r.expandSliceCount)
                : 0.0D;
            line(w, String.format(
                java.util.Locale.ROOT,
                "  Expand slices  : %d  (avg_pairs=%.1f  budgetStops[item=%d, time=%d])",
                r.expandSliceCount,
                expandAvgPairs,
                r.expandBudgetItemStops,
                r.expandBudgetTimeStops));
            line(w, "  Cache write    : " + r.writeMs + " ms");
            line(w, String.format("  %-12s  %12s  %14s  %14s  %8s  %10s  %9s  %9s  %s",
                "Language",
                "Setup (ms)",
                "Resolve (ms)",
                "Switch (ms)",
                "Slices",
                "Avg/slice",
                "StopItem",
                "StopTime",
                "Mode"));
            line(w, "  " + repeat("-", 122));
            for (String lang : langs) {
                long setup = r.setupMsPerLang.containsKey(lang) ? r.setupMsPerLang.get(lang) : -1L;
                long sw = r.switchMsPerLang.containsKey(lang) ? r.switchMsPerLang.get(lang) : -1L;
                long rs = r.resolveMsPerLang.containsKey(lang) ? r.resolveMsPerLang.get(lang) : -1L;
                int slices = safeGet(r.resolveSliceCountPerLang, lang);
                int processed = safeGet(r.resolveProcessedEntriesPerLang, lang);
                int stopItem = safeGet(r.resolveBudgetItemStopsPerLang, lang);
                int stopTime = safeGet(r.resolveBudgetTimeStopsPerLang, lang);
                double avgPerSlice = slices > 0 ? (processed * 1.0D / slices) : 0.0D;
                String mode = r.switchModePerLang.containsKey(lang) ? r.switchModePerLang.get(lang) : "?";
                line(w, String.format(
                    java.util.Locale.ROOT,
                    "  %-12s  %12d  %14d  %14d  %8d  %10.1f  %9d  %9d  %s",
                    lang,
                    setup,
                    rs,
                    sw,
                    slices,
                    avgPerSlice,
                    stopItem,
                    stopTime,
                    mode));
            }
            line(w, "");

            if (hasProfilerData(r)) {
                line(w, "  Profiler note: section total_ms values are inclusive and may overlap; do not sum them.");
                line(w, "");
                writeProfilerSummaryText(w, r.profilerReport);
                writeProfilerHotspotsText(w, r.profilerReport);
            }

            line(w, "=================================================================");
            line(w, "  Output: " + out.getParentFile().getAbsolutePath());
            line(w, "=================================================================");
        } finally {
            if (w != null) w.close();
        }
    }

    // -------------------------------------------------------------------------
    // build-summary.tsv
    // -------------------------------------------------------------------------

    private static void writeSummaryTsv(BuildResult r, Map<String, ModStats> modStats, File out) throws Exception {
        List<String> langs = new ArrayList<String>(r.collectedPerLang.keySet());
        Writer w = null;
        try {
            w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), UTF8));

            w.write("modid\tsubitems");
            for (String lang : langs) {
                w.write("\t" + lang + "_collected");
                w.write("\t" + lang + "_good");
                w.write("\t" + lang + "_empty");
                w.write("\t" + lang + "_raw_key");
                w.write("\t" + lang + "_mixed_lang");
                w.write("\t" + lang + "_cjk_suspect");
                w.write("\t" + lang + "_good_pct");
            }
            w.write(NL);

            for (String modId : new TreeMap<String, ModStats>(modStats).keySet()) {
                ModStats ms = modStats.get(modId);
                w.write(modId);
                w.write('\t');
                w.write(String.valueOf(ms.subitems));
                for (String lang : langs) {
                    int collected = safeGet(ms.collectedPerLang, lang);
                    int good = safeGet(ms.goodPerLang, lang);
                    int empty = safeGet(ms.emptyPerLang, lang);
                    int rawKey = safeGet(ms.rawKeyPerLang, lang);
                    int mixed = safeGet(ms.mixedLangPerLang, lang);
                    int cjkSus = safeGet(ms.cjkSuspectPerLang, lang);
                    double goodPct = ms.subitems > 0 ? (good * 100.0 / ms.subitems) : 0.0;
                    w.write('\t'); w.write(String.valueOf(collected));
                    w.write('\t'); w.write(String.valueOf(good));
                    w.write('\t'); w.write(String.valueOf(empty));
                    w.write('\t'); w.write(String.valueOf(rawKey));
                    w.write('\t'); w.write(String.valueOf(mixed));
                    w.write('\t'); w.write(String.valueOf(cjkSus));
                    w.write('\t'); w.write(String.format("%.1f", goodPct));
                }
                w.write(NL);
            }
        } finally {
            if (w != null) w.close();
        }
    }

    // -------------------------------------------------------------------------
    // suspect-entries.tsv
    // -------------------------------------------------------------------------

    private static void writeSuspectEntriesTsv(
            Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> data, File out) throws Exception {
        Writer w = null;
        try {
            w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), UTF8));
            w.write("modid\tregistry_name\tdamage\tlanguage_code\tdisplay_name\tquality_category");
            w.write(NL);

            for (Map.Entry<PrebuiltSecondaryNameIndexKey, Map<String, String>> entry : data.entrySet()) {
                PrebuiltSecondaryNameIndexKey key = entry.getKey();
                String modId = extractModId(key.registryName);
                for (Map.Entry<String, String> langEntry : entry.getValue().entrySet()) {
                    String lang = langEntry.getKey();
                    String name = langEntry.getValue();
                    NameQuality q = NameQualityClassifier.classify(name, lang);
                    if (q == NameQuality.GOOD) continue;
                    w.write(esc(modId)); w.write('\t');
                    w.write(esc(key.registryName)); w.write('\t');
                    w.write(String.valueOf(key.damage)); w.write('\t');
                    w.write(esc(lang)); w.write('\t');
                    w.write(esc(name == null ? "" : name)); w.write('\t');
                    w.write(q.name());
                    w.write(NL);
                }
            }
        } finally {
            if (w != null) w.close();
        }
    }

    // -------------------------------------------------------------------------
    // build-profiler-*.tsv and text report sections
    // -------------------------------------------------------------------------

    private static void writeProfilerSummaryTsv(BuildProfiler.Report report, File out) throws Exception {
        Writer w = null;
        try {
            w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), UTF8));
            w.write("section\tcalls\thits\thit_pct\ttotal_ms\tavg_ms\tmax_ms");
            w.write(NL);

            for (BuildProfiler.SectionSnapshot section : report.sections) {
                w.write(esc(section.section));
                w.write('\t');
                w.write(String.valueOf(section.calls));
                w.write('\t');
                w.write(String.valueOf(section.hits));
                w.write('\t');
                w.write(formatHitPercent(section.hits, section.calls));
                w.write('\t');
                w.write(formatMillis(section.totalNanos));
                w.write('\t');
                w.write(formatMillis(section.calls <= 0 ? 0L : section.totalNanos / section.calls));
                w.write('\t');
                w.write(formatMillis(section.maxNanos));
                w.write(NL);
            }
        } finally {
            if (w != null) w.close();
        }
    }

    private static void writeProfilerHotspotsTsv(BuildProfiler.Report report, File out) throws Exception {
        Writer w = null;
        try {
            w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), UTF8));
            w.write("rank\tsection\tduration_ms\tlanguage_code\tregistry_name\tdamage\titem_class\thit");
            w.write(NL);

            for (int i = 0; i < report.slowCalls.size(); i++) {
                BuildProfiler.SlowCallSnapshot slowCall = report.slowCalls.get(i);
                w.write(String.valueOf(i + 1));
                w.write('\t');
                w.write(esc(slowCall.section));
                w.write('\t');
                w.write(formatMillis(slowCall.durationNanos));
                w.write('\t');
                w.write(esc(slowCall.languageCode));
                w.write('\t');
                w.write(esc(slowCall.registryName));
                w.write('\t');
                w.write(String.valueOf(slowCall.damage));
                w.write('\t');
                w.write(esc(slowCall.itemClass));
                w.write('\t');
                w.write(String.valueOf(slowCall.hit));
                w.write(NL);
            }
        } finally {
            if (w != null) w.close();
        }
    }

    private static void writeProfilerSummaryText(Writer w, BuildProfiler.Report report) throws Exception {
        line(w, "[ Profiler Summary ]");
        line(w, String.format("  %-40s  %8s  %8s  %8s  %10s  %10s  %10s",
            "Section", "Calls", "Hits", "Hit %", "Total ms", "Avg ms", "Max ms"));
        line(w, "  " + repeat("-", 108));

        int limit = Math.min(20, report.sections.size());
        for (int i = 0; i < limit; i++) {
            BuildProfiler.SectionSnapshot section = report.sections.get(i);
            line(w, String.format(
                "  %-40s  %8d  %8d  %8s  %10s  %10s  %10s",
                trimForColumn(section.section, 40),
                section.calls,
                section.hits,
                formatHitPercent(section.hits, section.calls),
                formatMillis(section.totalNanos),
                formatMillis(section.calls <= 0 ? 0L : section.totalNanos / section.calls),
                formatMillis(section.maxNanos)));
        }
        if (report.sections.size() > limit) {
            line(w, "  ... " + (report.sections.size() - limit) + " more sections in build-profiler-summary.tsv");
        }
        line(w, "");
    }

    private static void writeProfilerHotspotsText(Writer w, BuildProfiler.Report report) throws Exception {
        line(w, "[ Profiler Hotspots ]");
        line(w, String.format("  %-4s  %-32s  %10s  %-8s  %s",
            "Rank", "Section", "Time ms", "Lang", "Registry"));
        line(w, "  " + repeat("-", 96));

        int limit = Math.min(20, report.slowCalls.size());
        for (int i = 0; i < limit; i++) {
            BuildProfiler.SlowCallSnapshot slowCall = report.slowCalls.get(i);
            String registry = slowCall.registryName;
            if (registry == null || registry.isEmpty()) {
                registry = slowCall.itemClass == null || slowCall.itemClass.isEmpty()
                    ? "<unknown>"
                    : slowCall.itemClass;
            }

            line(w, String.format(
                "  %-4d  %-32s  %10s  %-8s  %s",
                i + 1,
                trimForColumn(slowCall.section, 32),
                formatMillis(slowCall.durationNanos),
                trimForColumn(slowCall.languageCode, 8),
                registry + "@" + slowCall.damage + (slowCall.hit ? "" : " [miss]")));
        }
        if (report.slowCalls.size() > limit) {
            line(w, "  ... " + (report.slowCalls.size() - limit) + " more hotspots in build-profiler-hotspots.tsv");
        }
        line(w, "");
    }

    // -------------------------------------------------------------------------
    // Per-mod stats
    // -------------------------------------------------------------------------

    private static Map<String, ModStats> buildModStats(
            Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> data, List<String> langs) {
        Map<String, ModStats> result = new HashMap<String, ModStats>();
        for (Map.Entry<PrebuiltSecondaryNameIndexKey, Map<String, String>> entry : data.entrySet()) {
            String modId = extractModId(entry.getKey().registryName);
            ModStats ms = result.get(modId);
            if (ms == null) {
                ms = new ModStats();
                result.put(modId, ms);
            }
            ms.subitems++;
            for (Map.Entry<String, String> langEntry : entry.getValue().entrySet()) {
                String lang = langEntry.getKey();
                String name = langEntry.getValue();
                if (name == null || name.isEmpty()) continue;
                NameQuality q = NameQualityClassifier.classify(name, lang);
                inc(ms.collectedPerLang, lang);
                switch (q) {
                    case GOOD:        inc(ms.goodPerLang, lang); break;
                    case RAW_KEY:     inc(ms.rawKeyPerLang, lang); break;
                    case CONTAINS_CJK: inc(ms.cjkSuspectPerLang, lang); break;
                    case MIXED_LANGUAGE: inc(ms.mixedLangPerLang, lang); break;
                    case FORMAT_ONLY: inc(ms.formatOnlyPerLang, lang); break;
                    default: break;
                }
            }
        }
        // Compute per-mod empty counts: keys that had no collected entry for a given language.
        // The data map only stores collected entries, so empty = subitems - collected[lang].
        for (ModStats ms : result.values()) {
            for (String lang : langs) {
                int empty = ms.subitems - safeGet(ms.collectedPerLang, lang);
                if (empty > 0) ms.emptyPerLang.put(lang, empty);
            }
        }
        return result;
    }

    private static int countSingleDamageItems(Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> data) {
        Map<String, Integer> variants = new HashMap<String, Integer>();
        for (PrebuiltSecondaryNameIndexKey key : data.keySet()) {
            Integer prev = variants.get(key.registryName);
            variants.put(key.registryName, (prev == null ? 0 : prev) + 1);
        }
        int count = 0;
        for (int v : variants.values()) {
            if (v == 1) count++;
        }
        return count;
    }

    private static String extractModId(String registryName) {
        if (registryName == null) return "unknown";
        int colon = registryName.indexOf(':');
        return colon > 0 ? registryName.substring(0, colon) : registryName;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int safeGet(Map<String, Integer> map, String key) {
        Integer v = map.get(key);
        return v == null ? 0 : v;
    }

    private static void inc(Map<String, Integer> map, String key) {
        Integer v = map.get(key);
        map.put(key, v == null ? 1 : v + 1);
    }

    private static int sum(Map<String, Integer> map) {
        int total = 0;
        for (int v : map.values()) total += v;
        return total;
    }

    private static boolean hasProfilerData(BuildResult result) {
        return result != null
            && result.profilerReport != null
            && !result.profilerReport.isEmpty();
    }

    private static String formatMillis(long nanos) {
        return String.format(Locale.US, "%.3f", nanos / 1000000.0D);
    }

    private static String formatHitPercent(int hits, int calls) {
        if (calls <= 0) {
            return "0.0";
        }
        return String.format(Locale.US, "%.1f", hits * 100.0D / calls);
    }

    private static String trimForColumn(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 3) {
            return value.substring(0, maxLength);
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private static void line(Writer w, String text) throws Exception {
        w.write(text);
        w.write(NL);
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    private static String esc(String v) {
        if (v == null) return "";
        return v.replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }

    // -------------------------------------------------------------------------
    // ModStats
    // -------------------------------------------------------------------------

    private static final class ModStats {

        int subitems = 0;
        Map<String, Integer> collectedPerLang = new HashMap<String, Integer>();
        Map<String, Integer> goodPerLang = new HashMap<String, Integer>();
        Map<String, Integer> emptyPerLang = new HashMap<String, Integer>();
        Map<String, Integer> rawKeyPerLang = new HashMap<String, Integer>();
        Map<String, Integer> cjkSuspectPerLang = new HashMap<String, Integer>();
        Map<String, Integer> mixedLangPerLang = new HashMap<String, Integer>();
        Map<String, Integer> formatOnlyPerLang = new HashMap<String, Integer>();
    }
}
