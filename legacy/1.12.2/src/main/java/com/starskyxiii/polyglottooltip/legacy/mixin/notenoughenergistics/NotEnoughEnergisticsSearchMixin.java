package com.starskyxiii.polyglottooltip.legacy.mixin.notenoughenergistics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.legacy.integration.notenoughenergistics.NotEnoughEnergisticsIntegration;

import net.minecraft.item.ItemStack;

@Pseudo
@Mixin(targets = "com.github.vfyjxf.nee.client.gui.IngredientSwitcherWidget", remap = false)
public abstract class NotEnoughEnergisticsSearchMixin {
    @WrapOperation(method = "lambda$updateViews$1", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;func_82833_r()Ljava/lang/String;"))
    private static String polyglot$searchName(ItemStack stack, Operation<String> original) {
        return NotEnoughEnergisticsIntegration.searchText(stack, original.call(stack));
    }
}
