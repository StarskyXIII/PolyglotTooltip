package com.starskyxiii.polyglottooltip.legacy.mixin.integratedterminals;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.starskyxiii.polyglottooltip.legacy.integration.integratedterminals.IntegratedTerminalsSearch;

import net.minecraftforge.fluids.FluidStack;

@Pseudo
@Mixin(targets = "org.cyclops.integratedterminals.capability.ingredient.IngredientComponentTerminalStorageHandlerFluidStack",
        remap = false)
public abstract class IntegratedTerminalsFluidSearchMixin {
    @WrapOperation(method = "lambda$getInstanceFilterPredicate$4(Ljava/lang/String;Lnet/minecraftforge/fluids/FluidStack;)Z",
            require = 0, at = @At(value = "INVOKE", target = "Ljava/lang/String;matches(Ljava/lang/String;)Z"))
    private static boolean polyglot$matches(String name, String pattern, Operation<Boolean> original,
                                            @Local(argsOnly = true) FluidStack stack) {
        return original.call(name, pattern)
                || IntegratedTerminalsSearch.fluidMatches(stack, token -> original.call(token, pattern));
    }

    @WrapOperation(method = "lambda$getInstanceFilterPredicate$4(Ljava/lang/String;Lnet/minecraftforge/fluids/FluidStack;)Z",
            require = 0, at = @At(value = "INVOKE",
                    target = "Lme/towdium/jecharacters/util/Match;matches(Ljava/lang/String;Ljava/lang/CharSequence;)Z"))
    private static boolean polyglot$matchMatches(String name, CharSequence pattern, Operation<Boolean> original,
                                                 @Local(argsOnly = true) FluidStack stack) {
        return original.call(name, pattern)
                || IntegratedTerminalsSearch.fluidMatches(stack, token -> original.call(token, pattern));
    }
}
