package com.starskyxiii.polyglottooltip.mixin.rs2;

import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.Platform;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "com.refinedmods.refinedstorage.common.support.resource.FluidResourceRendering", remap = false)
public class Rs2FluidResourceRenderingMixin {

    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
    private void onGetTooltip(ResourceKey resource, CallbackInfoReturnable<List<Component>> cir) {
        if (!(resource instanceof FluidResource fluidResource)) {
            return;
        }

        List<Component> tooltip = new ArrayList<>(cir.getReturnValue());
        SecondaryTooltipUtil.insertSecondaryName(tooltip, Platform.INSTANCE.getFluidRenderer().getDisplayName(fluidResource));
        cir.setReturnValue(tooltip);
    }
}
