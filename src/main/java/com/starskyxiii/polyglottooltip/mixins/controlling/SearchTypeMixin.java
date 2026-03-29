package com.starskyxiii.polyglottooltip.mixins.controlling;

import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.starskyxiii.polyglottooltip.integration.controlling.ControllingSearchUtil;

@Pseudo
@Mixin(targets = "com.blamejared.controlling.client.gui.SearchType", remap = false)
public abstract class SearchTypeMixin {

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Inject(method = "getPredicate", at = @At("HEAD"), cancellable = true, remap = false)
    private void polyglot$expandCategorySearch(String searchText, CallbackInfoReturnable<Predicate> cir) {
        if (!"CATEGORY_NAME".equals(((Enum) (Object) this).name())) {
            return;
        }

        cir.setReturnValue((Predicate) ControllingSearchUtil.createCategoryPredicate(searchText));
    }
}
