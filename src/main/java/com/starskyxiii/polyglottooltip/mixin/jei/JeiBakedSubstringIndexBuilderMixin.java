package com.starskyxiii.polyglottooltip.mixin.jei;

import com.starskyxiii.polyglottooltip.search.ChineseScriptVariantIndexer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds simplified/traditional variants to JEI 29.21's initial baked index. */
@Mixin(targets = "mezz.jei.common.search.BakedSubstringIndexBuilder", remap = false)
public abstract class JeiBakedSubstringIndexBuilderMixin {

    @Shadow
    public abstract void put(String key, Object value);

    @Unique
    private static final ThreadLocal<Boolean> polyglot$inVariantInsertion =
            ThreadLocal.withInitial(() -> false);

    @Inject(method = "put(Ljava/lang/String;Ljava/lang/Object;)V", at = @At("HEAD"), remap = false)
    private void putChineseVariants(String key, Object value, CallbackInfo ci) {
        if (polyglot$inVariantInsertion.get()) return;
        polyglot$inVariantInsertion.set(true);
        try {
            ChineseScriptVariantIndexer.putVariants(key, value, this::put);
        } finally {
            polyglot$inVariantInsertion.remove();
        }
    }
}
