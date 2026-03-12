package com.starskyxiii.polyglottooltip.mixin.jei;

import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "mezz.jei.gui.search.ElementSearchLowMem", remap = false)
public class JeiElementSearchLowMemMixin {

    @Redirect(
            method = "matches",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"
            ),
            remap = false
    )
    private static boolean useChineseScriptAwareContains(String candidate, CharSequence query) {
        return ChineseScriptSearchMatcher.containsMatch(String.valueOf(query), candidate);
    }
}
