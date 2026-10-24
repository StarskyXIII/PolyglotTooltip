package com.starskyxiii.polyglottooltip.legacy.mixin.mekanismenergistics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.legacy.integration.mekanismenergistics.MekanismEnergisticsGases;

import mekanism.api.gas.Gas;

@Pseudo
@Mixin(targets = {"com.mekeng.github.client.slots.SlotGas", "com.mekeng.github.client.slots.SlotGasTank"}, remap = false)
public abstract class MekanismEnergisticsSlotTooltipMixin {
    @WrapOperation(method = "getMessage()Ljava/lang/String;", require = 0,
            at = @At(value = "INVOKE", target = "Lmekanism/api/gas/Gas;getLocalizedName()Ljava/lang/String;"))
    private String polyglot$appendNames(Gas gas, Operation<String> original) {
        return MekanismEnergisticsGases.message(gas, original.call(gas));
    }
}
