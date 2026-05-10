package com.starskyxiii.polyglottooltip.mixin.gtceu;

import com.starskyxiii.polyglottooltip.integration.FluidTooltipHelper;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Adds secondary-language fluid names to GTM's own tank widget used by newer
 * GTM versions such as 7.5.2.
 */
@Pseudo
@Mixin(targets = "com.gregtechceu.gtceu.api.gui.widget.TankWidget", remap = false)
public class GtceuTankWidgetFluidTooltipMixin {

    @Inject(method = "getFullTooltipTexts", at = @At("RETURN"), cancellable = true, remap = false)
    private void polyglot$appendSecondaryFluidName(CallbackInfoReturnable<List<Component>> cir) {
        List<Component> original = cir.getReturnValue();
        List<Component> updated = FluidTooltipHelper.withSecondaryFluidName(original);
        if (updated != original) {
            cir.setReturnValue(updated);
        }
    }
}
