package com.starskyxiii.polyglottooltip.mixin.rs2;

import com.google.common.base.Supplier;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.integration.rs2.Rs2TooltipCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.refinedmods.refinedstorage.common.grid.view.AbstractItemGridResourceRepositoryMapper", remap = false)
public class Rs2ItemTooltipCacheMixin {
    @WrapOperation(
            method = "apply(Lcom/refinedmods/refinedstorage/api/resource/ResourceKey;)Lcom/refinedmods/refinedstorage/common/api/grid/view/GridResource;",
            at = @At(value = "INVOKE", target = "Lcom/google/common/base/Suppliers;memoize(Lcom/google/common/base/Supplier;)Lcom/google/common/base/Supplier;", ordinal = 1),
            require = 1
    )
    private <T> Supplier<T> polyglot$refreshableTooltip(Supplier<T> delegate, Operation<Supplier<T>> original) {
        return Rs2TooltipCache.memoize(delegate);
    }
}
