package com.starskyxiii.polyglottooltip.mixin.mekanism;

import com.llamalad7.mixinextras.sugar.Local;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.starskyxiii.polyglottooltip.integration.mekanism.MekanismTooltipHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "mekanism.common.util.StorageUtils", remap = false)
public class MekanismStorageUtilsMixin {

    @Inject(
            method = "addStoredChemical",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", shift = At.Shift.AFTER)
    )
    private static void onAddStoredChemical(ItemStack stack,
                                            List<Component> tooltip,
                                            CallbackInfo ci,
                                            @Local(name = "chemicalInTank") Object chemicalInTank) {
        MekanismTooltipHelper.getChemicalNameFromStack(chemicalInTank)
                .ifPresent(name -> SecondaryTooltipUtil.insertSecondaryName(tooltip, name));
    }
}
