package com.starskyxiii.polyglottooltip.legacy.mixin.ae2fc;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.starskyxiii.polyglottooltip.legacy.integration.ae2fc.Ae2FluidCraftingTooltips;

import appeng.fluids.util.IAEFluidTank;

@Pseudo
@Mixin(targets = "com.glodblock.github.handler.TankMouseHandler", remap = false)
public abstract class Ae2FluidCraftingTankMixin {
    @Shadow @Final private IAEFluidTank tank;
    @Shadow @Final private int index;

    @Inject(method = "getTooltip()Ljava/util/List;", at = @At("RETURN"), cancellable = true, require = 0)
    private void polyglot$appendNames(CallbackInfoReturnable<List<String>> info) {
        List<String> lines = info.getReturnValue();
        List<String> named = Ae2FluidCraftingTooltips.tank(lines, tank == null ? null : tank.getFluidInSlot(index));
        if (named != lines) {
            info.setReturnValue(named);
        }
    }
}
