package com.starskyxiii.polyglottooltip.legacy.mixin.extracells;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.legacy.integration.extracells.ExtraCellsFluids;

import net.minecraftforge.fluids.FluidStack;

@Pseudo
@Mixin(targets = "extracells.gui.GuiTerminal", remap = false)
public abstract class ExtraCellsSearchMixin {
    @WrapOperation(method = "updateFluids()V", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fluids/FluidStack;getLocalizedName()Ljava/lang/String;"))
    private String polyglot$searchName(FluidStack stack, Operation<String> original) {
        return ExtraCellsFluids.searchText(stack, original.call(stack));
    }
}
