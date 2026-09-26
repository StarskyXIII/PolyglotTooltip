package com.starskyxiii.polyglottooltip.mixin.mekanism;

import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Pseudo
@Mixin(targets = "mekanism.common.content.qio.SearchQueryParser$QueryType", remap = false)
public class LegacyMekanismQioNameQueryMixin {

    @Inject(method = "lambda$static$0", at = @At("HEAD"), cancellable = true, remap = false)
    private static void polyglottooltip$matchSecondaryLanguageNames(String query,
                                                                    ItemStack stack,
                                                                    CallbackInfoReturnable<Boolean> cir) {
        List<String> candidates = new ArrayList<>();
        candidates.add(stack.getHoverName().getString());
        if (SecondaryTooltipUtil.shouldShowSecondaryLanguage()) {
            candidates.addAll(LanguageCache.getInstance().resolveSearchNamesForAll(stack));
        }
        if (ChineseScriptSearchMatcher.containsMatch(query, candidates)) {
            cir.setReturnValue(true);
        }
    }
}
