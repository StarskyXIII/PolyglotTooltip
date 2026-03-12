package com.starskyxiii.polyglottooltip.mixin.rs2;

import com.refinedmods.refinedstorage.common.api.grid.view.GridResource;
import com.starskyxiii.polyglottooltip.integration.rs2.Rs2SearchUtil;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "com.refinedmods.refinedstorage.mekanism.grid.ChemicalGridResource", remap = false)
public class Rs2MekanismChemicalGridResourceMixin {

    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
    private void onGetTooltip(CallbackInfoReturnable<List<Component>> cir) {
        Object resource = ((GridResource) this).getResourceForRecipeMods();
        Rs2SearchUtil.getRs2MekanismChemicalDisplayName(resource).ifPresent(displayName -> {
            List<Component> tooltip = new ArrayList<>(cir.getReturnValue());
            SecondaryTooltipUtil.insertSecondaryName(tooltip, displayName);
            cir.setReturnValue(tooltip);
        });
    }
}
