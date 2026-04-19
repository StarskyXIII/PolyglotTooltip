package com.starskyxiii.polyglottooltip.mixin.mekanism;

import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.search.WrappedSearchMatcher;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

@Pseudo
@Mixin(targets = "mekanism.common.content.qio.SearchQueryParser$QueryType", remap = false)
public abstract class MekanismQioNameQueryMixin {

    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private void polyglottooltip$matchSecondaryLanguageNames(String key, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!"NAME".equals(((Enum<?>) (Object) this).name())) {
            return;
        }

        String primaryName = stack.getHoverName().getString();
        cir.setReturnValue(WrappedSearchMatcher.matches(
                key,
                primaryName,
                () -> primaryName.toLowerCase(Locale.ROOT).contains(key),
                () -> LanguageCache.getInstance().resolveSearchNamesForAll(stack)
        ));
    }
}
