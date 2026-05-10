package com.starskyxiii.polyglottooltip.mixin.ldlib;

import com.starskyxiii.polyglottooltip.integration.FluidTooltipHelper;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Adds secondary-language fluid names to LDLib tank tooltips. GTM 1.4.4 recipe
 * fluid slots use this widget, so this intentionally applies LDLib-wide.
 */
@Pseudo
@Mixin(targets = "com.lowdragmc.lowdraglib.gui.widget.TankWidget", remap = false)
public class LdlibTankWidgetFluidTooltipMixin {

    @Inject(method = "getFullTooltipTexts", at = @At("RETURN"), cancellable = true, remap = false)
    private void polyglot$appendSecondaryFluidName(CallbackInfoReturnable<List<Component>> cir) {
        List<Component> original = cir.getReturnValue();
        List<Component> updated = FluidTooltipHelper.withSecondaryFluidName(original);
        if (updated != original) {
            cir.setReturnValue(updated);
        }
    }
}
