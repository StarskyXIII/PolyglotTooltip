package com.starskyxiii.polyglottooltip.mixin.explorerscompass;

import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.chaosthedude.explorerscompass.gui.ExplorersCompassScreen", remap = false)
public abstract class ExplorersCompassScreenMixin {

    @Redirect(
            method = "processSearchTerm",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"
            )
    )
    private boolean polyglottooltip$matchSearchTerm(String candidate, CharSequence query) {
        return ChineseScriptSearchMatcher.containsMatch(String.valueOf(query), candidate);
    }
}
