package com.starskyxiii.polyglottooltip.legacy.mixin.thaumicenergistics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.legacy.integration.thaumicenergistics.ThaumicEnergisticsSearch;

@Pseudo
@Mixin(targets = "thaumicenergistics.client.gui.helpers.MERepo", remap = false)
public abstract class ThaumicEnergisticsSearchMixin {
    @WrapOperation(method = "searchName", require = 0, at = @At(value = "INVOKE",
            target = "Lappeng/util/Platform;getItemDisplayName(Ljava/lang/Object;)Ljava/lang/String;"))
    private String polyglot$searchName(Object stack, Operation<String> original) {
        return ThaumicEnergisticsSearch.searchText(stack, original.call(stack));
    }
}
