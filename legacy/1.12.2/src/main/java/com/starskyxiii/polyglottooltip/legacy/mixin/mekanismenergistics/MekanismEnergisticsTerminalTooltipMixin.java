package com.starskyxiii.polyglottooltip.legacy.mixin.mekanismenergistics;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.legacy.integration.mekanismenergistics.MekanismEnergisticsGases;

import mekanism.api.gas.Gas;

@Pseudo
@Mixin(targets = "com.mekeng.github.client.gui.GuiGasTerminal", remap = false)
public abstract class MekanismEnergisticsTerminalTooltipMixin {
    @Unique private Gas polyglot$gas;

    @WrapOperation(method = "func_191948_b(II)V", require = 0,
            at = @At(value = "INVOKE", target = "Lmekanism/api/gas/Gas;getLocalizedName()Ljava/lang/String;"))
    private String polyglot$captureGas(Gas gas, Operation<String> original) {
        polyglot$gas = gas;
        return original.call(gas);
    }

    @ModifyArg(method = "func_191948_b(II)V", index = 0, require = 0,
            at = @At(value = "INVOKE", target = "func_146283_a(Ljava/util/List;II)V"))
    private List<String> polyglot$appendNames(List<String> lines) {
        Gas gas = polyglot$gas;
        polyglot$gas = null;
        return MekanismEnergisticsGases.withNames(lines, gas);
    }
}
