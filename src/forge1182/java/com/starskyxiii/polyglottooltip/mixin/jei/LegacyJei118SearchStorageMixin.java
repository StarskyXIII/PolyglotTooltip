package com.starskyxiii.polyglottooltip.mixin.jei;

import com.starskyxiii.polyglottooltip.integration.jec.LegacyJecSearchStorage;
import mezz.jei.core.search.ISearchStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mezz.jei.core.search.PrefixInfo", remap = false)
public abstract class LegacyJei118SearchStorageMixin {
    @Shadow
    public abstract char getPrefix();

    @Inject(
            method = "createStorage()Lmezz/jei/core/search/ISearchStorage;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void polyglot$useJecStorage(CallbackInfoReturnable<ISearchStorage<?>> callback) {
        char prefix = getPrefix();
        if (prefix == 0 || prefix == '#') {
            ISearchStorage<?> original = callback.getReturnValue();
            ISearchStorage<?> replacement = LegacyJecSearchStorage.replaceLegacyStorage(ISearchStorage.class, original);
            if (replacement != original) {
                callback.setReturnValue(replacement);
            }
        }
    }
}
