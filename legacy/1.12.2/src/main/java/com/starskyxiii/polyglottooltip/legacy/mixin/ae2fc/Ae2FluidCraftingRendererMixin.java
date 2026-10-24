package com.starskyxiii.polyglottooltip.legacy.mixin.ae2fc;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.llamalad7.mixinextras.sugar.Local;
import com.starskyxiii.polyglottooltip.legacy.integration.ae2fc.Ae2FluidCraftingTooltips;

import appeng.api.storage.data.IAEItemStack;

@Pseudo
@Mixin(targets = "com.glodblock.github.util.UtilClient", remap = false)
public abstract class Ae2FluidCraftingRendererMixin {
    @ModifyArg(method = {
            "rendererFluid(Lnet/minecraft/client/gui/inventory/GuiContainer;Lappeng/api/storage/data/IAEItemStack;IIZ)Z",
            "rendererGas(Lnet/minecraft/client/gui/inventory/GuiContainer;Lappeng/api/storage/data/IAEItemStack;IIZ)Z"
    }, index = 0, require = 0, at = @At(value = "INVOKE", target = "func_146283_a(Ljava/util/List;II)V"))
    private static List<String> polyglot$appendNames(List<String> lines, @Local(argsOnly = true) IAEItemStack stack) {
        return Ae2FluidCraftingTooltips.drop(lines, stack);
    }
}
