package com.starskyxiii.polyglottooltip.mixins.ae2.unofficial;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.starskyxiii.polyglottooltip.Ae2UnofficialInterfaceSearchHelper;

@Pseudo
@Mixin(targets = "appeng.client.gui.implementations.GuiInterfaceTerminal", remap = false)
public abstract class InterfaceTerminalSectionSearchMixin {

    @Inject(
        method = "interfaceSectionMatchesSearchTerm",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false)
    private static void polyglot$matchMultilingualSectionNames(
        @Coerce Object section,
        String searchTerm,
        CallbackInfoReturnable<Boolean> cir) {
        Boolean match = Ae2UnofficialInterfaceSearchHelper.tryMatchSectionSearchTerm(section, searchTerm);
        if (match != null) {
            cir.setReturnValue(match);
        }
    }
}
