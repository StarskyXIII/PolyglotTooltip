package com.starskyxiii.polyglottooltip.mixin.jec;

import com.starskyxiii.polyglottooltip.search.ChineseScriptVariantIndexer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "me.towdium.jecharacters.JechSearchStorage", remap = false)
public abstract class JecSearchStorageMixin {
    @Shadow
    public abstract void put(String key, Object value);

    @Unique
    private boolean polyglot$inVariantInsertion;

    @Inject(method = "put(Ljava/lang/String;Ljava/lang/Object;)V", at = @At("HEAD"), remap = false)
    private void putChineseVariants(String key, Object value, CallbackInfo ci) {
        if (polyglot$inVariantInsertion) {
            return;
        }
        polyglot$inVariantInsertion = true;
        try {
            ChineseScriptVariantIndexer.putVariants(key, value, this::put);
        } finally {
            polyglot$inVariantInsertion = false;
        }
    }
}
