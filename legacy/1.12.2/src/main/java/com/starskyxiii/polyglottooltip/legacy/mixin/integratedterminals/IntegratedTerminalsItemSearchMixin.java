package com.starskyxiii.polyglottooltip.legacy.mixin.integratedterminals;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.starskyxiii.polyglottooltip.legacy.integration.integratedterminals.IntegratedTerminalsSearch;

import net.minecraft.item.ItemStack;

@Pseudo
@Mixin(targets = "org.cyclops.integratedterminals.capability.ingredient.IngredientComponentTerminalStorageHandlerItemStack",
        remap = false)
public abstract class IntegratedTerminalsItemSearchMixin {
    @WrapOperation(method = "lambda$getInstanceFilterPredicate$6(Ljava/lang/String;Lnet/minecraft/item/ItemStack;)Z",
            require = 0, at = @At(value = "INVOKE", target = "Ljava/lang/String;matches(Ljava/lang/String;)Z"))
    private static boolean polyglot$matches(String name, String pattern, Operation<Boolean> original,
                                            @Local(argsOnly = true) ItemStack stack) {
        return original.call(name, pattern)
                || IntegratedTerminalsSearch.itemMatches(stack, token -> original.call(token, pattern));
    }

    @WrapOperation(method = "lambda$getInstanceFilterPredicate$6(Ljava/lang/String;Lnet/minecraft/item/ItemStack;)Z",
            require = 0, at = @At(value = "INVOKE",
                    target = "Lme/towdium/jecharacters/util/Match;matches(Ljava/lang/String;Ljava/lang/CharSequence;)Z"))
    private static boolean polyglot$matchMatches(String name, CharSequence pattern, Operation<Boolean> original,
                                                 @Local(argsOnly = true) ItemStack stack) {
        return original.call(name, pattern)
                || IntegratedTerminalsSearch.itemMatches(stack, token -> original.call(token, pattern));
    }
}
