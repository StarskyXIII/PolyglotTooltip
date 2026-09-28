package com.starskyxiii.polyglottooltip.mixin.ae2;

import appeng.api.stacks.GenericStack;
import com.llamalad7.mixinextras.sugar.Local;
import com.starskyxiii.polyglottooltip.integration.ae2.PatternAccessSearchUtil;
//? if (forge && >1.19.2 && <=1.20.1) || (neoforge && >=1.21.1) {
import com.starskyxiii.polyglottooltip.integration.ae2.Ae2SearchRefresh;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.HashMap;
import java.util.Map;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Pseudo
@Mixin(targets = "com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal", remap = false)
public class Ae2ExtendedPatternTerminalMixin
        //? if (forge && >1.19.2 && <=1.20.1) || (neoforge && >=1.21.1)
        implements Ae2SearchRefresh.Refreshable
{

    //? if (forge && >1.19.2 && <=1.20.1) || (neoforge && >=1.21.1) {
    @Shadow @Final
    private Map<String, ?> cachedSearches;

    @Shadow @Final
    private HashMap<?, ?> patternSearchCache;

    @Shadow
    private void refreshList() {
        throw new AssertionError();
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void polyglot$registerSearchRefresh(CallbackInfo ci) {
        Ae2SearchRefresh.registerScreen(this);
    }

    @Override
    public void polyglot$refreshSearch() {
        cachedSearches.clear();
        patternSearchCache.clear();
        if ((Object) this == Minecraft.getInstance().screen) {
            refreshList();
        }
    }
    //?}

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
