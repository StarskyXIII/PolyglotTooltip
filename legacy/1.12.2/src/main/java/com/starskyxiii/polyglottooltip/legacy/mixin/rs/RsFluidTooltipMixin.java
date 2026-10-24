package com.starskyxiii.polyglottooltip.legacy.mixin.rs;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.legacy.integration.rs.RsSearch;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

@Pseudo
@Mixin(targets = "com.raoulvdberge.refinedstorage.gui.grid.stack.GridStackFluid", remap = false)
public abstract class RsFluidTooltipMixin {
    @WrapOperation(method = {"getTooltip()Ljava/lang/String;", "getTooltip(Z)Ljava/lang/String;"}, require = 0,
            at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/fluids/Fluid;getLocalizedName(Lnet/minecraftforge/fluids/FluidStack;)Ljava/lang/String;"))
    private String polyglot$appendNames(Fluid fluid, FluidStack stack, Operation<String> original) {
        return RsSearch.fluidTooltip(stack, original.call(fluid, stack));
    }
}
