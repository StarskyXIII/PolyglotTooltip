package com.starskyxiii.polyglottooltip.mixin.integratedterminals;

import com.llamalad7.mixinextras.sugar.Local;
import com.starskyxiii.polyglottooltip.search.IntegratedTerminalSearchHelper;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

@Mixin(targets = "org.cyclops.integratedterminals.capability.ingredient.IngredientComponentTerminalStorageHandlerItemStack", remap = false)
public class IntegratedTerminalsItemSearchMixin {

    @Inject(
            method = "getInstanceFilterPredicate(Lorg/cyclops/integratedterminals/core/terminalstorage/query/SearchMode;Ljava/lang/String;)Ljava/util/function/Predicate;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void polyglot$extendItemSearch(
            CallbackInfoReturnable<Predicate<ItemStack>> cir,
            @Local(argsOnly = true) Object searchMode,
            @Local(argsOnly = true) String query
    ) {
        cir.setReturnValue(stack -> IntegratedTerminalSearchHelper.matches(stack, searchMode, query));
    }
}
