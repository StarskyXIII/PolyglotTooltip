package com.starskyxiii.polyglottooltip.name.prebuilt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Lightweight profiler used during full-name cache builds.
 *
 * <p>It is only active while a build explicitly installs it in the current
 * thread, so normal tooltip resolution outside cache builds stays unaffected.
 */
public final class BuildProfiler {

    private static final int MAX_SLOW_CALLS = 64;
    private static final ThreadLocal<BuildProfiler> ACTIVE = new ThreadLocal<BuildProfiler>();
    private static final Comparator<SectionSnapshot> SECTION_TOTAL_DESC =
        new Comparator<SectionSnapshot>() {
            @Override
            public int compare(SectionSnapshot left, SectionSnapshot right) {
                if (left.totalNanos == right.totalNanos) {
                    return left.section.compareTo(right.section);
                }
                return left.totalNanos < right.totalNanos ? 1 : -1;
            }
        };
    private static final Comparator<SlowCallSnapshot> SLOW_CALL_DESC =
        new Comparator<SlowCallSnapshot>() {
            @Override
            public int compare(SlowCallSnapshot left, SlowCallSnapshot right) {
                if (left.durationNanos == right.durationNanos) {
                    return left.section.compareTo(right.section);
                }
                return left.durationNanos < right.durationNanos ? 1 : -1;
            }
        };

    private final Map<String, SectionStats> sections =
        new LinkedHashMap<String, SectionStats>();
    private final List<SlowCallSnapshot> slowCalls =
        new ArrayList<SlowCallSnapshot>();

    public Scope activate() {
        BuildProfiler previous = ACTIVE.get();
        ACTIVE.set(this);
        return new Scope(previous);
    }

    public static long startSection() {
        return ACTIVE.get() == null ? 0L : System.nanoTime();
    }

    public static void record(String section, ItemStack stack, String languageCode, long startNs, String result) {
        if (startNs <= 0L) {
            return;
        }

        BuildProfiler active = ACTIVE.get();
        if (active == null) {
            return;
        }

        long durationNs = System.nanoTime() - startNs;
        active.recordInternal(section, stack, languageCode, durationNs, result != null && !result.isEmpty());
    }

    public synchronized Report snapshot() {
        List<SectionSnapshot> sectionSnapshots = new ArrayList<SectionSnapshot>(sections.size());
        for (Map.Entry<String, SectionStats> entry : sections.entrySet()) {
            sectionSnapshots.add(entry.getValue().snapshot(entry.getKey()));
        }
        Collections.sort(sectionSnapshots, SECTION_TOTAL_DESC);

        List<SlowCallSnapshot> slowCallSnapshots = new ArrayList<SlowCallSnapshot>(slowCalls);
        Collections.sort(slowCallSnapshots, SLOW_CALL_DESC);
        return new Report(sectionSnapshots, slowCallSnapshots);
    }

    private synchronized void recordInternal(String section, ItemStack stack, String languageCode,
            long durationNs, boolean hit) {
        if (section == null || section.trim().isEmpty()) {
            section = "unknown";
        }

        SectionStats stats = sections.get(section);
        if (stats == null) {
            stats = new SectionStats();
            sections.put(section, stats);
        }
        stats.calls++;
        stats.totalNanos += durationNs;
        if (hit) {
            stats.hits++;
        }
        if (durationNs > stats.maxNanos) {
            stats.maxNanos = durationNs;
        }

        if (slowCalls.size() >= MAX_SLOW_CALLS) {
            SlowCallSnapshot currentSlowest = slowCalls.get(slowCalls.size() - 1);
            if (durationNs <= currentSlowest.durationNanos) {
                return;
            }
        }

        slowCalls.add(createSlowCall(section, stack, languageCode, durationNs, hit));
        Collections.sort(slowCalls, SLOW_CALL_DESC);
        while (slowCalls.size() > MAX_SLOW_CALLS) {
            slowCalls.remove(slowCalls.size() - 1);
        }
    }

    private static SlowCallSnapshot createSlowCall(String section, ItemStack stack, String languageCode,
            long durationNs, boolean hit) {
        String registryName = "";
        String itemClass = "";
        int damage = 0;

        if (stack != null && stack.getItem() != null) {
            Item item = stack.getItem();
            damage = stack.getItemDamage();
            itemClass = item.getClass().getName();
            try {
                Object name = Item.itemRegistry.getNameForObject(item);
                registryName = name == null ? "" : String.valueOf(name);
            } catch (Throwable ignored) {
                registryName = "";
            }
        }

        return new SlowCallSnapshot(
            section,
            languageCode == null ? "" : languageCode,
            registryName,
            damage,
            itemClass,
            durationNs,
            hit);
    }

    private static final class SectionStats {
        private int calls;
        private int hits;
        private long totalNanos;
        private long maxNanos;

        private SectionSnapshot snapshot(String section) {
            return new SectionSnapshot(section, calls, hits, totalNanos, maxNanos);
        }
    }

    public static final class Scope implements AutoCloseable {
        private final BuildProfiler previous;
        private boolean closed;

        private Scope(BuildProfiler previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;

            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
    }

    public static final class Report {
        public final List<SectionSnapshot> sections;
        public final List<SlowCallSnapshot> slowCalls;

        private Report(List<SectionSnapshot> sections, List<SlowCallSnapshot> slowCalls) {
            this.sections = Collections.unmodifiableList(new ArrayList<SectionSnapshot>(sections));
            this.slowCalls = Collections.unmodifiableList(new ArrayList<SlowCallSnapshot>(slowCalls));
        }

        public boolean isEmpty() {
            return sections.isEmpty() && slowCalls.isEmpty();
        }
    }

    public static final class SectionSnapshot {
        public final String section;
        public final int calls;
        public final int hits;
        public final long totalNanos;
        public final long maxNanos;

        private SectionSnapshot(String section, int calls, int hits, long totalNanos, long maxNanos) {
            this.section = section == null ? "" : section;
            this.calls = calls;
            this.hits = hits;
            this.totalNanos = totalNanos;
            this.maxNanos = maxNanos;
        }
    }

    public static final class SlowCallSnapshot {
        public final String section;
        public final String languageCode;
        public final String registryName;
        public final int damage;
        public final String itemClass;
        public final long durationNanos;
        public final boolean hit;

        private SlowCallSnapshot(String section, String languageCode, String registryName,
                int damage, String itemClass, long durationNanos, boolean hit) {
            this.section = section == null ? "" : section;
            this.languageCode = languageCode == null ? "" : languageCode;
            this.registryName = registryName == null ? "" : registryName;
            this.damage = damage;
            this.itemClass = itemClass == null ? "" : itemClass;
            this.durationNanos = durationNanos;
            this.hit = hit;
        }
    }
}
