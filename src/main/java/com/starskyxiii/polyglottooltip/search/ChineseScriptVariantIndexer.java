package com.starskyxiii.polyglottooltip.search;

import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Shared logic for indexing simplified/traditional Chinese script variants
 * into search trees that use a {@code put(String key, Object value)} API.
 *
 * <p>Used by both {@code JeiGeneralizedSuffixTreeMixin} and
 * {@code JecFakeTreeMixin}, which target different classes but need the
 * same recursion-guarded variant-insertion behaviour.
 */
public final class ChineseScriptVariantIndexer {

    private ChineseScriptVariantIndexer() {
    }

    /**
     * Indexes all Chinese script variants of {@code key} via {@code putter}.
     *
     * <p>The caller is responsible for the recursion guard — call this method
     * only when the guard flag is {@code false}, and set it to {@code true}
     * for the duration of this call.
     *
     * @param key    the string being indexed (may be null)
     * @param value  the value to associate with each variant
     * @param putter the {@code put(key, value)} method reference of the tree
     */
    public static void putVariants(String key, Object value, BiConsumer<String, Object> putter) {
        Set<String> variants = ChineseScriptSearchMatcher.getSearchVariants(key);
        if (variants.size() <= 1) return;

        String normalizedKey = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        for (String variant : variants) {
            if (!variant.equals(normalizedKey)) {
                putter.accept(variant, value);
            }
        }
    }
}
