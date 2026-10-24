package com.starskyxiii.polyglottooltip.legacy.mixin.mekanismenergistics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.legacy.integration.mekanismenergistics.MekanismEnergisticsGases;

import mekanism.api.gas.GasStack;

@Pseudo
@Mixin(targets = "com.mekeng.github.client.gui.GuiGasInterfaceConfigurationTerminal", remap = false)
public abstract class MekanismEnergisticsConfigSearchMixin {
    @WrapOperation(method = "gasStackMatchesSearchTerm", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/mekeng/github/util/Utils;getGasDisplayName(Lmekanism/api/gas/GasStack;)Ljava/lang/String;"))
    private String polyglot$searchName(GasStack stack, Operation<String> original) {
        return MekanismEnergisticsGases.searchText(stack, original.call(stack));
    }
}
