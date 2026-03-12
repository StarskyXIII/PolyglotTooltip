package com.starskyxiii.polyglottooltip.mixin.emi;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.integration.emi.EmiTagPrefixTooltipHelper;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.List;

@Mixin(targets = "dev.emi.emi.screen.EmiScreenManager", remap = false)
public abstract class EmiScreenManagerTooltipMixin {

    @WrapOperation(
            method = "renderCurrentTooltip",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/emi/emi/api/stack/EmiIngredient;getTooltip()Ljava/util/List;"
            ),
            remap = false
    )
    private static List<ClientTooltipComponent> wrapHoveredTooltip(@Coerce Object ingredient,
                                                                   Operation<List<ClientTooltipComponent>> original) {
        return EmiTagPrefixTooltipHelper.appendSecondaryName(ingredient, original.call(ingredient));
    }
}
