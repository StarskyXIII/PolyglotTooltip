package com.starskyxiii.polyglottooltip.mixin.emi;

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional EMI integration - inserts the secondary-language fluid name into
 * FluidEmiStack tooltips shown by EMI.
 *
 * <p>Injecting into {@code getTooltipText} lets EMI continue building its own
 * client tooltip wrappers while we only adjust the underlying text lines.
 */
@Mixin(targets = "dev.emi.emi.api.stack.FluidEmiStack", remap = false)
public class EmiFluidEmiStackMixin {

    @Inject(method = "getTooltipText", at = @At("RETURN"), cancellable = true, remap = false)
    private void onGetTooltipText(CallbackInfoReturnable<List<Component>> cir) {
        List<Component> tooltip = new ArrayList<>(cir.getReturnValue());
        if (tooltip.isEmpty()) {
            return;
        }

        SecondaryTooltipUtil.insertSecondaryName(tooltip, tooltip.get(0));
        cir.setReturnValue(tooltip);
    }
}
