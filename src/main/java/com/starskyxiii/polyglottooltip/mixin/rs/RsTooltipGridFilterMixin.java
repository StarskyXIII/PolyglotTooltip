package com.starskyxiii.polyglottooltip.mixin.rs;

import com.starskyxiii.polyglottooltip.integration.rs.RsSearchUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.screen.grid.filtering.TooltipGridFilter", remap = false)
public abstract class RsTooltipGridFilterMixin {

    @Shadow
    @Final
    private String tooltip;

    @Inject(
            method = "test(Lcom/refinedmods/refinedstorage/screen/grid/stack/IGridStack;)Z",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void polyglot$expandTooltipSearch(@Coerce Object stack, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            cir.setReturnValue(RsSearchUtil.matchesTooltipSearch(tooltip, stack));
        }
    }
}
