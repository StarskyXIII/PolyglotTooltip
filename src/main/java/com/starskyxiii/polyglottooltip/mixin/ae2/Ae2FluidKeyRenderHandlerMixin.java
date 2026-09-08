package com.starskyxiii.polyglottooltip.mixin.ae2;
//~ ae_fluid_renderer

import appeng.api.stacks.AEFluidKey;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(targets = "appeng.init.client.InitStackRenderHandlers$FluidKeyRenderHandler", remap = false)
public class Ae2FluidKeyRenderHandlerMixin {

    @Inject(
            method = "getTooltip(Lappeng/api/stacks/AEFluidKey;)Ljava/util/List;",
            at = @At("RETURN")
    )
    private void onGetTooltip(AEFluidKey ingredient, CallbackInfoReturnable<List<Component>> cir) {
        //? if <=1.20.1 {
        /*SecondaryTooltipUtil.insertSecondaryName(cir.getReturnValue(), ingredient.toStack(1).getDisplayName());
        *///?} else {
        SecondaryTooltipUtil.insertSecondaryName(cir.getReturnValue(), ingredient.toStack(1).getHoverName());
        //?}
    }
}
