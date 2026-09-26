package com.starskyxiii.polyglottooltip.mixin.jei;
//~ jei10_internal_packages

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.core.ChineseScriptPolicy;
import com.starskyxiii.polyglottooltip.integration.jec.JecMatchBridge;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "mezz.jei.gui.search.ElementSearchLowMem", remap = false)
public class JeiElementSearchLowMemMixin {

    @WrapOperation(
            method = {"matches", "matchesStrings"},
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"
            ),
            remap = false,
            require = 0
    )
    private static boolean useStringContainsCompat(String candidate,
                                                    CharSequence query,
                                                    Operation<Boolean> original) {
        JecMatchBridge.Result jecResult = JecMatchBridge.contains(candidate, query);
        if (jecResult == JecMatchBridge.Result.MATCH) {
            return true;
        }
        if (jecResult == JecMatchBridge.Result.NO_MATCH) {
            return useJecForChineseScriptVariants(candidate, query);
        }
        if (jecResult == JecMatchBridge.Result.INVOCATION_FAILED) {
            return false;
        }
        return ChineseScriptSearchMatcher.containsMatch(String.valueOf(query), candidate);
    }

    @WrapOperation(
            method = {"matches", "matchesStrings"},
            at = @At(
                    value = "INVOKE",
                    target = "Lme/towdium/jecharacters/utils/Match;contains(Ljava/lang/String;Ljava/lang/CharSequence;)Z",
                    remap = false
            ),
            require = 0
    )
    private static boolean useJecForChineseScriptVariants(String candidate,
                                                           CharSequence query,
                                                           Operation<Boolean> original) {
        boolean originalMatch = original.call(candidate, query);
        if (originalMatch || !ChineseScriptSearchMatcher.isEnabled()) {
            return originalMatch;
        }
        return useChineseScriptVariants(candidate, query, original);
    }

    private static boolean useChineseScriptVariants(String candidate,
                                                     CharSequence query,
                                                     Operation<Boolean> original) {
        if (!ChineseScriptSearchMatcher.isEnabled()) {
            return false;
        }
        String normalizedCandidate = ChineseScriptPolicy.normalize(candidate);
        for (String variant : ChineseScriptSearchMatcher.getSearchVariants(candidate)) {
            if (!variant.equals(normalizedCandidate)) {
                if (original.call(variant, query)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean useJecForChineseScriptVariants(String candidate, CharSequence query) {
        if (!ChineseScriptSearchMatcher.isEnabled()) {
            return false;
        }
        String normalizedCandidate = ChineseScriptPolicy.normalize(candidate);
        for (String variant : ChineseScriptSearchMatcher.getSearchVariants(candidate)) {
            if (!variant.equals(normalizedCandidate)) {
                JecMatchBridge.Result result = JecMatchBridge.contains(variant, query);
                if (result == JecMatchBridge.Result.MATCH) {
                    return true;
                }
                if (result == JecMatchBridge.Result.INVOCATION_FAILED) {
                    return false;
                }
            }
        }
        return false;
    }
}
