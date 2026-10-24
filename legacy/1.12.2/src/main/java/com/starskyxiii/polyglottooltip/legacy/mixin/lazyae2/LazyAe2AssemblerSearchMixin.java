package com.starskyxiii.polyglottooltip.legacy.mixin.lazyae2;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.legacy.integration.lazyae2.LazyAe2Search;

@Pseudo
@Mixin(targets = "io.github.phantamanta44.threng.inventory.ContainerBigAssembler$Page$SlotPage", remap = false)
public abstract class LazyAe2AssemblerSearchMixin {
    @Shadow private List<String> resultNames;

    @Unique private int polyglot$generation;

    @Inject(method = "matchesQuery()Z", at = @At("HEAD"), require = 0)
    private void polyglot$invalidate(CallbackInfoReturnable<Boolean> info) {
        int generation = LazyAe2Search.generation();
        if (polyglot$generation != generation && LazyAe2Search.enabled()) {
            resultNames = null;
            polyglot$generation = generation;
        }
    }

    @WrapOperation(method = "lambda$matchesQuery$0", require = 0, at = @At(value = "INVOKE",
            target = "Lappeng/util/Platform;getItemDisplayName(Ljava/lang/Object;)Ljava/lang/String;"))
    private static String polyglot$searchName(Object stack, Operation<String> original) {
        return LazyAe2Search.searchText(stack, original.call(stack));
    }
}
