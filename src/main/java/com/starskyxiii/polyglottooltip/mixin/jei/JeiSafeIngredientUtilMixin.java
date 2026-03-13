package com.starskyxiii.polyglottooltip.mixin.jei;

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.starskyxiii.polyglottooltip.integration.productivebees.ProductiveBeesNameHelper;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class JeiSafeIngredientUtilMixin {

    @Mixin(targets = "mezz.jei.common.util.SafeIngredientUtil", remap = false)
    public static class TooltipMixin {

        @Inject(
                method = "getTooltip(Lmezz/jei/api/gui/builder/ITooltipBuilder;Lmezz/jei/api/runtime/IIngredientManager;Lmezz/jei/api/ingredients/IIngredientRenderer;Lmezz/jei/api/ingredients/ITypedIngredient;Lnet/minecraft/world/item/TooltipFlag$Default;)V",
                at = @At("RETURN"),
                remap = false
        )
        private static <T> void onGetTooltip(ITooltipBuilder tooltip,
                                             IIngredientManager ingredientManager,
                                             IIngredientRenderer<T> ingredientRenderer,
                                             ITypedIngredient<T> typedIngredient,
                                             TooltipFlag.Default tooltipFlag,
                                             CallbackInfo ci) {
            ProductiveBeesNameHelper.tryCreateBeeIngredientName(typedIngredient.getIngredient())
                    .ifPresent(name -> {
                        for (Component line : SecondaryTooltipUtil.getSecondaryNameLines(name)) {
                            tooltip.add(line);
                        }
                    });
        }
    }
}
