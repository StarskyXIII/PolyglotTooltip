package com.starskyxiii.polyglottooltip.mixin.ae2;

import appeng.menu.me.common.GridInventoryEntry;
import com.starskyxiii.polyglottooltip.integration.ae2.Ae2SearchPredicate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.gtocore.integration.ae.MultiLangNameSearchPredicate", remap = false)
public abstract class GtoCoreMultiLangNameSearchPredicateMixin {

    @Shadow
    @Final
    private String term;

    @Inject(method = "test(Lappeng/menu/me/common/GridInventoryEntry;)Z", at = @At("RETURN"), cancellable = true)
    private void polyglottooltip$matchPolyglotSecondaryNames(GridInventoryEntry entry,
                                                             CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }

        if (new Ae2SearchPredicate(this.term, ignored -> false).test(entry)) {
            cir.setReturnValue(true);
        }
    }
}
