package com.starskyxiii.polyglottooltip.legacy.mixin.mekanismenergistics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mekeng.github.common.me.data.IAEGasStack;
import com.starskyxiii.polyglottooltip.legacy.integration.mekanismenergistics.MekanismEnergisticsGases;

@Pseudo
@Mixin(targets = "com.mekeng.github.common.me.client.GasRepo", remap = false)
public abstract class MekanismEnergisticsRepoSearchMixin {
    @WrapOperation(method = "updateView()V", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/mekeng/github/util/Utils;getGasDisplayName(Lcom/mekeng/github/common/me/data/IAEGasStack;)Ljava/lang/String;"))
    private String polyglot$searchName(IAEGasStack stack, Operation<String> original) {
        return MekanismEnergisticsGases.searchText(stack, original.call(stack));
    }
}
