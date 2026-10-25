package com.starskyxiii.polyglottooltip.legacy.mixin.controlling;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.legacy.integration.controlling.ControllingSearch;

import us.getfluxed.controlsearch.client.gui.GuiNewControls;

@Pseudo
@Mixin(targets = "us.getfluxed.controlsearch.client.gui.GuiNewControls", remap = false)
public abstract class ControllingSearchMixin {
    @WrapOperation(method = "refreshKeys()V", require = 0, at = @At(value = "INVOKE",
            target = "Lus/getfluxed/controlsearch/client/gui/GuiNewControls;translate(Ljava/lang/String;)Ljava/lang/String;"))
    private String polyglot$searchText(GuiNewControls screen, String key, Operation<String> original) {
        return ControllingSearch.searchText(key, original.call(screen, key));
    }
}
