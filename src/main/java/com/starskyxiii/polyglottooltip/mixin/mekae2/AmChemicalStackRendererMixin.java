package com.starskyxiii.polyglottooltip.mixin.mekae2;

import com.starskyxiii.polyglottooltip.integration.mekanism.MekanismTooltipHelper;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Applied Mekanistics already puts the chemical name on line 0 in 1.20.1.
 */
@Pseudo
@Mixin(targets = "me.ramidzkh.mekae2.ae2.AMChemicalStackRenderer", remap = false)
public class AmChemicalStackRendererMixin {

    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
    private void onGetTooltip(CallbackInfoReturnable<List<Component>> cir) {
        List<Component> tooltip = cir.getReturnValue();
        if (tooltip == null || tooltip.isEmpty()) {
            return;
        }
        cir.setReturnValue(MekanismTooltipHelper.withSecondaryName(tooltip, tooltip.get(0)));
    }
}
