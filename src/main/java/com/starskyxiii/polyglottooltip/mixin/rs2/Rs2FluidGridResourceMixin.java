package com.starskyxiii.polyglottooltip.mixin.rs2;

import com.refinedmods.refinedstorage.common.Platform;
import com.refinedmods.refinedstorage.common.grid.view.FluidGridResource;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "com.refinedmods.refinedstorage.common.grid.view.FluidGridResource", remap = false)
public abstract class Rs2FluidGridResourceMixin {

    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
    private void onGetTooltip(CallbackInfoReturnable<List<Component>> cir) {
        FluidResource resource = (FluidResource) ((FluidGridResource) (Object) this).getResourceForRecipeMods();
        if (resource == null) {
            return;
        }

        List<Component> tooltip = new ArrayList<>(cir.getReturnValue());
        SecondaryTooltipUtil.insertSecondaryName(tooltip, Platform.INSTANCE.getFluidRenderer().getDisplayName(resource));
        cir.setReturnValue(tooltip);
    }
}
