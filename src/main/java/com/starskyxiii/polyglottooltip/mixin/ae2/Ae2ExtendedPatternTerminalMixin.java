package com.starskyxiii.polyglottooltip.mixin.ae2;

import appeng.api.stacks.GenericStack;
import com.llamalad7.mixinextras.sugar.Local;
import com.starskyxiii.polyglottooltip.integration.ae2.PatternAccessSearchUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(targets = "com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal", remap = false)
public class Ae2ExtendedPatternTerminalMixin {

    @Redirect(
            method = "getOrComputePatternSearchData",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/glodblock/github/extendedae/util/FCUtil;tokenize(Ljava/lang/String;)Ljava/util/List;",
                    ordinal = 0
            ),
            remap = false
    )
    private List<String> expandOutputTokens(String originalText, @Local(name = "output") GenericStack output) {
        return PatternAccessSearchUtil.buildPatternTokenList(originalText, output);
    }

    @Redirect(
            method = "getOrComputePatternSearchData",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/glodblock/github/extendedae/util/FCUtil;tokenize(Ljava/lang/String;)Ljava/util/List;",
                    ordinal = 1
            ),
            remap = false
    )
    private List<String> expandInputTokens(String originalText, @Local(name = "possibleInputs") GenericStack[] possibleInputs) {
        GenericStack primaryInput = possibleInputs.length > 0 ? possibleInputs[0] : null;
        return PatternAccessSearchUtil.buildPatternTokenList(originalText, primaryInput);
    }
}
