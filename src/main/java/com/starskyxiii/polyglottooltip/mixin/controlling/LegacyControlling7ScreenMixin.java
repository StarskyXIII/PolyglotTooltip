package com.starskyxiii.polyglottooltip.mixin.controlling;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.integration.controlling.LegacyControllingSearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Group;

@Pseudo
@Mixin(targets = "com.blamejared.controlling.client.gui.GuiNewControls", remap = false)
public abstract class LegacyControlling7ScreenMixin {
    @Group(name = "polyglot_name", min = 1, max = 1)
    @WrapOperation(method = "lambda$filterKeys$7(Lcom/blamejared/controlling/client/gui/GuiNewKeyBindingList$KeyEntry;)Z",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"),
            remap = false, require = 0)
    private boolean polyglot$matchNameVanilla(String text, CharSequence query, Operation<Boolean> original, @Coerce Object entry) {
        return LegacyControllingSearch.matchesName(text, query, entry, (candidate, needle) -> original.call(candidate, needle));
    }

    @Group(name = "polyglot_name", min = 1, max = 1)
    @WrapOperation(method = "lambda$filterKeys$7(Lcom/blamejared/controlling/client/gui/GuiNewKeyBindingList$KeyEntry;)Z",
            at = @At(value = "INVOKE", target = "Lme/towdium/jecharacters/utils/Match;contains(Ljava/lang/String;Ljava/lang/CharSequence;)Z"),
            remap = false, require = 0)
    private boolean polyglot$matchNameJec(String text, CharSequence query, Operation<Boolean> original, @Coerce Object entry) {
        return LegacyControllingSearch.matchesName(text, query, entry, (candidate, needle) -> original.call(candidate, needle));
    }

    @Group(name = "polyglot_category", min = 1, max = 1)
    @WrapOperation(method = "lambda$filterKeys$8(Lcom/blamejared/controlling/client/gui/GuiNewKeyBindingList$KeyEntry;)Z",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"),
            remap = false, require = 0)
    private boolean polyglot$matchCategoryVanilla(String text, CharSequence query, Operation<Boolean> original, @Coerce Object entry) {
        return LegacyControllingSearch.matchesCategory(text, query, entry, (candidate, needle) -> original.call(candidate, needle));
    }

    @Group(name = "polyglot_category", min = 1, max = 1)
    @WrapOperation(method = "lambda$filterKeys$8(Lcom/blamejared/controlling/client/gui/GuiNewKeyBindingList$KeyEntry;)Z",
            at = @At(value = "INVOKE", target = "Lme/towdium/jecharacters/utils/Match;contains(Ljava/lang/String;Ljava/lang/CharSequence;)Z"),
            remap = false, require = 0)
    private boolean polyglot$matchCategoryJec(String text, CharSequence query, Operation<Boolean> original, @Coerce Object entry) {
        return LegacyControllingSearch.matchesCategory(text, query, entry, (candidate, needle) -> original.call(candidate, needle));
    }

    @Group(name = "polyglot_key", min = 1, max = 1)
    @WrapOperation(method = "lambda$filterKeys$9(Lcom/blamejared/controlling/client/gui/GuiNewKeyBindingList$KeyEntry;)Z",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"),
            remap = false, require = 0)
    private boolean polyglot$matchKeyVanilla(String text, CharSequence query, Operation<Boolean> original, @Coerce Object entry) {
        return LegacyControllingSearch.matchesKey(text, query, entry, (candidate, needle) -> original.call(candidate, needle));
    }

    @Group(name = "polyglot_key", min = 1, max = 1)
    @WrapOperation(method = "lambda$filterKeys$9(Lcom/blamejared/controlling/client/gui/GuiNewKeyBindingList$KeyEntry;)Z",
            at = @At(value = "INVOKE", target = "Lme/towdium/jecharacters/utils/Match;contains(Ljava/lang/String;Ljava/lang/CharSequence;)Z"),
            remap = false, require = 0)
    private boolean polyglot$matchKeyJec(String text, CharSequence query, Operation<Boolean> original, @Coerce Object entry) {
        return LegacyControllingSearch.matchesKey(text, query, entry, (candidate, needle) -> original.call(candidate, needle));
    }
}
