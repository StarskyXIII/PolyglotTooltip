package com.starskyxiii.polyglottooltip.legacy.mixin.rs;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.raoulvdberge.refinedstorage.gui.grid.stack.IGridStack;
import com.starskyxiii.polyglottooltip.legacy.integration.rs.RsSearch;

@Pseudo
@Mixin(targets = "com.raoulvdberge.refinedstorage.gui.grid.filtering.GridFilterName", remap = false)
public abstract class RsSearchNameMixin {
    @WrapOperation(method = "test(Lcom/raoulvdberge/refinedstorage/gui/grid/stack/IGridStack;)Z", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lcom/raoulvdberge/refinedstorage/gui/grid/stack/IGridStack;getName()Ljava/lang/String;"))
    private String polyglot$searchName(IGridStack stack, Operation<String> original) {
        return RsSearch.searchText(stack, original.call(stack));
    }
}
