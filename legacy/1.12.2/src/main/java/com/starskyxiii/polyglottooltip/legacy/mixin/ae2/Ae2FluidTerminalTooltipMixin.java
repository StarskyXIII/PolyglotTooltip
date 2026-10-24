package com.starskyxiii.polyglottooltip.legacy.mixin.ae2;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.legacy.integration.ae2.Ae2Tooltips;

import net.minecraftforge.fluids.FluidStack;

@Pseudo
@Mixin(targets = {"appeng.fluids.client.gui.GuiFluidTerminal", "appeng.fluids.client.gui.GuiMEPortableFluidCell"},
        remap = false)
public abstract class Ae2FluidTerminalTooltipMixin {
    @Unique private FluidStack polyglot$fluid;

    @WrapOperation(method = "func_191948_b(II)V", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fluids/FluidStack;getLocalizedName()Ljava/lang/String;"))
    private String polyglot$captureFluid(FluidStack stack, Operation<String> original) {
        polyglot$fluid = stack;
        return original.call(stack);
    }

    @ModifyArg(method = "func_191948_b(II)V", index = 0, require = 0,
            at = @At(value = "INVOKE", target = "func_146283_a(Ljava/util/List;II)V"))
    private List<String> polyglot$appendNames(List<String> lines) {
        FluidStack stack = polyglot$fluid;
        polyglot$fluid = null;
        return Ae2Tooltips.fluidLines(lines, stack);
    }
}
