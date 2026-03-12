package com.starskyxiii.polyglottooltip.mixin.rs2;

import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.starskyxiii.polyglottooltip.integration.rs2.Rs2SearchUtil;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "com.refinedmods.refinedstorage.mekanism.ChemicalResourceRendering", remap = false)
public class Rs2MekanismChemicalResourceRenderingMixin {

    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
    private void onGetTooltip(ResourceKey resourceKey, CallbackInfoReturnable<List<Component>> cir) {
        Rs2SearchUtil.getRs2MekanismChemicalDisplayName(resourceKey).ifPresent(displayName -> {
            List<Component> tooltip = new ArrayList<>(cir.getReturnValue());
            SecondaryTooltipUtil.insertSecondaryName(tooltip, displayName);
            cir.setReturnValue(tooltip);
        });
    }
}
