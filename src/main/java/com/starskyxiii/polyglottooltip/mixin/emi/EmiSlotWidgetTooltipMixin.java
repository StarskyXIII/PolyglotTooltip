package com.starskyxiii.polyglottooltip.mixin.emi;

import com.starskyxiii.polyglottooltip.integration.emi.EmiTagPrefixTooltipHelper;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(targets = "dev.emi.emi.api.widget.SlotWidget", remap = false)
public abstract class EmiSlotWidgetTooltipMixin {

    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true, remap = false)
    private void onGetTooltip(int mouseX,
                              int mouseY,
                              CallbackInfoReturnable<List<ClientTooltipComponent>> cir) {
        cir.setReturnValue(EmiTagPrefixTooltipHelper.appendSecondaryName(
                EmiTagPrefixTooltipHelper.getSlotIngredient(this),
                cir.getReturnValue()
        ));
    }
}
