package com.starskyxiii.polyglottooltip.legacy.mixin.jei;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.starskyxiii.polyglottooltip.legacy.integration.jei.JeiIntegration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraftforge.fluids.FluidStack;

@Pseudo
@Mixin(targets = "mezz.jei.plugins.vanilla.ingredients.fluid.FluidStackRenderer", remap = false)
public abstract class JeiFluidTooltipMixin {
    @Inject(method = "getTooltip(Lnet/minecraft/client/Minecraft;Lnet/minecraftforge/fluids/FluidStack;"
            + "Lnet/minecraft/client/util/ITooltipFlag;)Ljava/util/List;", at = @At("RETURN"))
    private void polyglot$appendNames(Minecraft minecraft, FluidStack stack, ITooltipFlag flag,
                                      CallbackInfoReturnable<List<String>> info) {
        JeiIntegration.fluidTooltip(stack, info.getReturnValue());
    }
}
