package com.starskyxiii.polyglottooltip.legacy.mixin.projecte;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.legacy.integration.projecte.ProjectESearch;

import net.minecraft.item.ItemStack;

@Pseudo
@Mixin(targets = "moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory", remap = false)
public abstract class ProjectESearchMixin {
    @WrapOperation(method = "doesItemMatchFilter(Lnet/minecraft/item/ItemStack;)Z", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;func_82833_r()Ljava/lang/String;"))
    private String polyglot$searchName(ItemStack stack, Operation<String> original) {
        return ProjectESearch.searchText(stack, original.call(stack));
    }
}
