package com.starskyxiii.polyglottooltip.integration.prettypipes;

import com.starskyxiii.polyglottooltip.core.ChineseScriptPolicy;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public final class PrettyPipesSearchPolicy {
    private final boolean scriptMatching;
    private final Map<String, Set<String>> candidates = new LinkedHashMap<String, Set<String>>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Set<String>> eldest) {
            return size() > 2048;
        }
    };

    public PrettyPipesSearchPolicy(boolean scriptMatching) {
        this.scriptMatching = scriptMatching;
    }

    public <T> Predicate<T> extend(Predicate<T> nativeRemoval, String query,
                                   boolean tooltipPrefix, boolean secondaryEnabled,
                                   Function<T, String> primaryName,
                                   Function<T, ? extends Iterable<String>> secondaryNames) {
        if (query == null || query.isEmpty() || query.startsWith("@")
                || (tooltipPrefix && query.startsWith("#"))) {
            return nativeRemoval;
        }
        Set<String> queries = variants(query);
        return entry -> {
            if (!nativeRemoval.test(entry)) {
                return false;
            }
            if (scriptMatching && matches(queries, primaryName.apply(entry))) {
                return false;
            }
            if (secondaryEnabled) {
                for (String candidate : secondaryNames.apply(entry)) {
                    if (matches(queries, candidate)) {
                        return false;
                    }
                }
            }
            return true;
        };
    }

    private boolean matches(Set<String> queries, String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        Set<String> names = candidates.computeIfAbsent(candidate, this::variants);
        for (String name : names) {
            for (String query : queries) {
                if (name.contains(query)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> variants(String value) {
        return ChineseScriptPolicy.scriptVariants(value.toLowerCase(Locale.ROOT), scriptMatching);
    }

    int cachedCandidateCount() {
        return candidates.size();
    }
}
