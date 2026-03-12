package com.starskyxiii.polyglottooltip.mixin.jei;

import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;
import java.util.Set;

/**
 * Injects Chinese script variant indexing into JEI's GeneralizedSuffixTree.
 *
 * <p>JEI's name search (NO_PREFIX) and tooltip search ($) both use GeneralizedSuffixTree
 * directly as their ISearchStorage. LimitedStringStorage (used for @, #, %, ^ prefixes)
 * also delegates to an internal GeneralizedSuffixTree. By injecting here we cover all paths.
 *
 * <p>When a Chinese string is indexed, we also index its simplified/traditional counterpart
 * so that users can search with either script and get results regardless of which locale
 * the item names were loaded from.
 */
@Mixin(targets = "mezz.jei.core.search.suffixtree.GeneralizedSuffixTree", remap = false)
public abstract class JeiGeneralizedSuffixTreeMixin {

    @Shadow
    public abstract void put(String key, Object value);

    @Unique
    private boolean polyglot$inVariantInsertion = false;

    @Inject(method = "put", at = @At("HEAD"), remap = false)
    private void putChineseVariants(String key, Object value, CallbackInfo ci) {
        if (polyglot$inVariantInsertion) return;

        Set<String> variants = ChineseScriptSearchMatcher.getSearchVariants(key);
        if (variants.size() <= 1) return;

        // normalizedKey is what getSearchVariants() treats as the "original"
        String normalizedKey = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        polyglot$inVariantInsertion = true;
        try {
            for (String variant : variants) {
                if (!variant.equals(normalizedKey)) {
                    // put() always resets activeLeaf = root as its first line,
                    // so recursive calls do not corrupt the suffix tree state.
                    this.put(variant, value);
                }
            }
        } finally {
            polyglot$inVariantInsertion = false;
        }
    }
}
