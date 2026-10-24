package com.starskyxiii.polyglottooltip.legacy.mixin.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.legacy.integration.ae2.Ae2Tooltips;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

@Pseudo
@Mixin(targets = {"appeng.fluids.client.gui.widgets.GuiFluidTank", "appeng.fluids.client.gui.widgets.GuiFluidSlot"},
        remap = false)
public abstract class Ae2FluidWidgetTooltipMixin {
    @WrapOperation(method = "getMessage()Ljava/lang/String;", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/fluids/Fluid;getLocalizedName(Lnet/minecraftforge/fluids/FluidStack;)Ljava/lang/String;"))
    private String polyglot$tankName(Fluid fluid, FluidStack stack, Operation<String> original) {
        return Ae2Tooltips.fluidMessage(stack, original.call(fluid, stack));
    }

    @WrapOperation(method = "getMessage()Ljava/lang/String;", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fluids/FluidStack;getLocalizedName()Ljava/lang/String;"))
    private String polyglot$slotName(FluidStack stack, Operation<String> original) {
        return Ae2Tooltips.fluidMessage(stack, original.call(stack));
    }
}
