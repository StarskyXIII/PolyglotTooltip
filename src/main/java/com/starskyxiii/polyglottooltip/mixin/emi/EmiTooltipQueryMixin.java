package com.starskyxiii.polyglottooltip.mixin.emi;

import com.starskyxiii.polyglottooltip.integration.emi.EmiSearchUtil;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Expands EMI tooltip search with simplified/traditional Chinese variants.
 */
@Mixin(targets = "dev.emi.emi.search.TooltipQuery", remap = false)
public abstract class EmiTooltipQueryMixin {

    @Shadow
    @Final
    private Set<Object> valid;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void appendChineseVariantMatches(String name, CallbackInfo ci) {
        EmiSearchUtil.appendTooltipMatches(valid, name);
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
