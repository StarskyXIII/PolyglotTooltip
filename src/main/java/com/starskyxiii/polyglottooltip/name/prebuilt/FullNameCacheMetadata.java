package com.starskyxiii.polyglottooltip.name.prebuilt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Persistent metadata describing what the on-disk full-name cache currently covers.
 *
 * <p>This lets the runtime answer simple lifecycle questions such as:
 * <ul>
 *   <li>Which languages are already present in the cache?</li>
 *   <li>Was this cache produced from a full-coverage build or only a partial refresh?</li>
 *   <li>Can we incrementally supplement missing languages instead of rebuilding everything?</li>
 * </ul>
 */
public final class FullNameCacheMetadata {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final int schemaVersion;
    private final List<String> languages;
    private final String coverageFilter;
    private final boolean complete;
    private final String modVersion;
    private final long builtAtMillis;
    private final boolean inferredFromLegacyCache;

    public FullNameCacheMetadata(int schemaVersion, List<String> languages, String coverageFilter,
            boolean complete, String modVersion, long builtAtMillis, boolean inferredFromLegacyCache) {
        this.schemaVersion = schemaVersion;
        this.languages = Collections.unmodifiableList(normalizeLanguages(languages));
        this.coverageFilter = normalizeCoverageFilter(coverageFilter);
        this.complete = complete;
        this.modVersion = modVersion == null ? "" : modVersion.trim();
        this.builtAtMillis = builtAtMillis;
        this.inferredFromLegacyCache = inferredFromLegacyCache;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public String getCoverageFilter() {
        return coverageFilter;
    }

    public boolean isComplete() {
        return complete;
    }

    public String getModVersion() {
        return modVersion;
    }

    public long getBuiltAtMillis() {
        return builtAtMillis;
    }

    public boolean isInferredFromLegacyCache() {
        return inferredFromLegacyCache;
    }

    public boolean hasLanguage(String languageCode) {
        if (languageCode == null || languageCode.trim().isEmpty()) {
            return false;
        }

        String normalized = languageCode.trim();
        for (String existing : languages) {
            if (normalized.equalsIgnoreCase(existing)) {
                return true;
            }
        }
        return false;
    }

    public List<String> findMissingLanguages(List<String> configuredLanguages) {
        List<String> missing = new ArrayList<String>();
        for (String languageCode : normalizeLanguages(configuredLanguages)) {
            if (!hasLanguage(languageCode)) {
                missing.add(languageCode);
            }
        }
        return missing;
    }

    public FullNameCacheMetadata withLanguages(List<String> updatedLanguages) {
        return new FullNameCacheMetadata(
            schemaVersion,
            updatedLanguages,
            coverageFilter,
            complete,
            modVersion,
            builtAtMillis,
            inferredFromLegacyCache);
    }

    public static FullNameCacheMetadata createForBuild(List<String> languages, String coverageFilter,
            boolean complete, String modVersion, long builtAtMillis) {
        return new FullNameCacheMetadata(
            CURRENT_SCHEMA_VERSION,
            languages,
            coverageFilter,
            complete,
            modVersion,
            builtAtMillis,
            false);
    }

    public static FullNameCacheMetadata inferFromLegacyCache(Set<String> languages, String modVersion,
            long inferredAtMillis) {
        return new FullNameCacheMetadata(
            CURRENT_SCHEMA_VERSION,
            new ArrayList<String>(languages),
            "all",
            true,
            modVersion,
            inferredAtMillis,
            true);
    }

    public static List<String> normalizeLanguages(List<String> languages) {
        LinkedHashSet<String> normalized = new LinkedHashSet<String>();
        if (languages == null) {
            return new ArrayList<String>(normalized);
        }

        for (String languageCode : languages) {
            if (languageCode == null) {
                continue;
            }

            String trimmed = languageCode.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }

        return new ArrayList<String>(normalized);
    }

    private static String normalizeCoverageFilter(String coverageFilter) {
        if (coverageFilter == null) {
            return "all";
        }

        String normalized = coverageFilter.trim();
        if (normalized.isEmpty()) {
            return "all";
        }

        return normalized;
    }
}
