package com.starskyxiii.polyglottooltip.mixin.emi;

import com.starskyxiii.polyglottooltip.integration.emi.EmiNameSearchUtil;
import com.starskyxiii.polyglottooltip.integration.emi.EmiSearchUtil;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Expands EMI name search with simplified/traditional Chinese variants.
 */
@Mixin(targets = "dev.emi.emi.search.NameQuery", remap = false)
public abstract class EmiNameQueryMixin {

    @Shadow
    @Final
    private Set<Object> valid;

    @Shadow
    @Final
    private String name;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void appendChineseVariantMatches(String name, CallbackInfo ci) {
        EmiSearchUtil.appendNameMatches(valid, name);
    }

    @Inject(method = "matches", at = @At("RETURN"), cancellable = true, remap = false)
    private void polyglottooltip$matchSecondaryName(@Coerce Object stack, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && EmiNameSearchUtil.matchesSecondaryName(name, stack)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "matchesUnbaked", at = @At("RETURN"), cancellable = true, remap = false)
    private void polyglottooltip$matchSecondaryNameUnbaked(@Coerce Object stack, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && EmiNameSearchUtil.matchesSecondaryName(name, stack)) {
            cir.setReturnValue(true);
        }
    }

    @Redirect(
            method = "matchesUnbaked",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"
            )
    )
    private boolean useChineseScriptAwareContains(String candidate, CharSequence query) {
        return ChineseScriptSearchMatcher.containsMatch(String.valueOf(query), candidate);
    }
}
