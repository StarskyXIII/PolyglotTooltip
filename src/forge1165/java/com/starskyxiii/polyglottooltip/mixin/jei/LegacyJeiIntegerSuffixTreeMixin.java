package com.starskyxiii.polyglottooltip.mixin.jei;

import com.starskyxiii.polyglottooltip.search.ChineseScriptVariantIndexer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.search.suffixtree.GeneralizedSuffixTree", remap = false)
public abstract class LegacyJeiIntegerSuffixTreeMixin {
    @Shadow
    public abstract void put(String key, int index);

    @Unique
    private boolean polyglot$inVariantInsertion;

    @Inject(method = "put(Ljava/lang/String;I)V", at = @At("RETURN"), remap = false, require = 1)
    private void polyglot$putChineseVariants(String key, int index, CallbackInfo ci) {
        if (polyglot$inVariantInsertion) {
            return;
        }
        polyglot$inVariantInsertion = true;
        try {
            ChineseScriptVariantIndexer.putVariants(key, index, this::put);
        } finally {
            polyglot$inVariantInsertion = false;
        }
    }
}
