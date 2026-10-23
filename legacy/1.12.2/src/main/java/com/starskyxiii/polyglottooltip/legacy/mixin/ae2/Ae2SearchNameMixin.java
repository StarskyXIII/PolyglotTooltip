package com.starskyxiii.polyglottooltip.legacy.mixin.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.legacy.integration.ae2.Ae2Search;

@Pseudo
@Mixin(targets = {
        "appeng.client.me.ItemRepo",
        "appeng.client.gui.implementations.GuiInterfaceTerminal",
        "appeng.client.gui.implementations.GuiInterfaceConfigurationTerminal"
}, remap = false)
public abstract class Ae2SearchNameMixin {
    @WrapOperation(method = {"updateView", "addIAE", "itemStackMatchesSearchTerm"}, require = 0, at = @At(value = "INVOKE",
            target = "Lappeng/util/Platform;getItemDisplayName(Ljava/lang/Object;)Ljava/lang/String;"))
    private String polyglot$searchName(Object stack, Operation<String> original) {
        return Ae2Search.searchText(stack, original.call(stack));
    }
}
