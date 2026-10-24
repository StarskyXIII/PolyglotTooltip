package com.starskyxiii.polyglottooltip.legacy.mixin.mekanism;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.starskyxiii.polyglottooltip.legacy.integration.mekanism.MekanismGasTooltip;

import mekanism.api.gas.GasStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;

@Pseudo
@Mixin(targets = "mekanism.client.jei.gas.GasStackRenderer", remap = false)
public abstract class MekanismGasTooltipMixin {
    @Inject(method = "getTooltip(Lnet/minecraft/client/Minecraft;Lmekanism/api/gas/GasStack;"
            + "Lnet/minecraft/client/util/ITooltipFlag;)Ljava/util/List;", at = @At("RETURN"))
    private void polyglot$appendNames(Minecraft minecraft, GasStack stack, ITooltipFlag flag,
                                      CallbackInfoReturnable<List<String>> info) {
        MekanismGasTooltip.append(stack, info.getReturnValue());
    }
}
