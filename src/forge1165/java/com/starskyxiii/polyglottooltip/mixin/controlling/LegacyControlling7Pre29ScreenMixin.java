package com.starskyxiii.polyglottooltip.mixin.controlling;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.integration.controlling.LegacyControllingSearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "com.blamejared.controlling.client.gui.GuiNewControls", remap = false)
public abstract class LegacyControlling7Pre29ScreenMixin {
    private static final String FILTER = "(Lcom/blamejared/controlling/client/gui/GuiNewKeyBindingList$KeyEntry;)Z";
    private static final String VANILLA_CONTAINS = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z";
    private static final String JEC_CONTAINS =
            "Lme/towdium/jecharacters/utils/Match;contains(Ljava/lang/String;Ljava/lang/CharSequence;)Z";

    @WrapOperation(method = "lambda$filterKeys$9" + FILTER,
            at = {@At(value = "INVOKE", target = VANILLA_CONTAINS), @At(value = "INVOKE", target = JEC_CONTAINS)},
            remap = false, require = 1, allow = 1)
    private boolean polyglot$matchName(String text, CharSequence query, Operation<Boolean> original, @Coerce Object entry) {
        return LegacyControllingSearch.matchesName(text, query, entry, (candidate, needle) -> original.call(candidate, needle));
    }

    @WrapOperation(method = "lambda$filterKeys$10" + FILTER,
            at = {@At(value = "INVOKE", target = VANILLA_CONTAINS), @At(value = "INVOKE", target = JEC_CONTAINS)},
            remap = false, require = 1, allow = 1)
    private boolean polyglot$matchCategory(String text, CharSequence query, Operation<Boolean> original, @Coerce Object entry) {
        return LegacyControllingSearch.matchesCategory(text, query, entry, (candidate, needle) -> original.call(candidate, needle));
    }

    @WrapOperation(method = "lambda$filterKeys$11" + FILTER,
            at = {@At(value = "INVOKE", target = VANILLA_CONTAINS), @At(value = "INVOKE", target = JEC_CONTAINS)},
            remap = false, require = 1, allow = 1)
    private boolean polyglot$matchKey(String text, CharSequence query, Operation<Boolean> original, @Coerce Object entry) {
        return LegacyControllingSearch.matchesKey(text, query, entry, (candidate, needle) -> original.call(candidate, needle));
    }
}
