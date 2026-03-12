package com.starskyxiii.polyglottooltip.mixin.jec;

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
 * JEC compatibility: injects Chinese script variant indexing into JEC's FakeTree.
 *
 * <p>When JEC (Just Enough Characters) is installed, it replaces JEI's GeneralizedSuffixTree
 * instances in ElementPrefixParser with FakeTree instances via ASM. FakeTree.put() overrides
 * and does NOT call super.put(), so our JeiGeneralizedSuffixTreeMixin is bypassed for item
 * name search (NO_PREFIX) and tooltip search ($).
 *
 * <p>This mixin mirrors JeiGeneralizedSuffixTreeMixin but targets FakeTree directly.
 * Variant puts also go through FakeTree.put(), so they are correctly added to JEC's
 * internal TreeSearcher. JEC's own pinyin search is unaffected.
 *
 * <p>This mixin is silently skipped when JEC is not installed (defaultRequire=0 in mixin JSON).
 */
@Mixin(targets = "me.towdium.jecharacters.utils.FakeTree", remap = false)
public abstract class JecFakeTreeMixin {

    @Shadow
    public abstract void put(String key, Object value);

    @Unique
    private boolean polyglot$inVariantInsertion = false;

    @Inject(method = "put", at = @At("HEAD"), remap = false)
    private void putChineseVariants(String key, Object value, CallbackInfo ci) {
        if (polyglot$inVariantInsertion) return;

        Set<String> variants = ChineseScriptSearchMatcher.getSearchVariants(key);
        if (variants.size() <= 1) return;

        String normalizedKey = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        polyglot$inVariantInsertion = true;
        try {
            for (String variant : variants) {
                if (!variant.equals(normalizedKey)) {
                    this.put(variant, value);
                }
            }
        } finally {
            polyglot$inVariantInsertion = false;
        }
    }
}
