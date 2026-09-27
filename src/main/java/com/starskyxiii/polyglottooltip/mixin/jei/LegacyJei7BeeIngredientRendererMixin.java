package com.starskyxiii.polyglottooltip.mixin.jei;
//~ minecraft_116_packages

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.starskyxiii.polyglottooltip.integration.productivebees.ProductiveBeesNameHelper;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Pseudo
@Mixin(targets = "cy.jdkdigital.productivebees.integrations.jei.ingredients.BeeIngredientRenderer", remap = false)
public class LegacyJei7BeeIngredientRendererMixin {
    @Inject(
            method = "getTooltip(Lcy/jdkdigital/productivebees/integrations/jei/ingredients/BeeIngredient;Lnet/minecraft/client/util/TooltipFlag;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 1
    )
    private void polyglot$appendSecondaryBeeName(@Coerce Object ingredient,
                                               TooltipFlag tooltipFlag,
                                               CallbackInfoReturnable<List<Component>> cir) {
        ProductiveBeesNameHelper.tryCreateBeeIngredientName(ingredient).ifPresent(name -> {
            List<Component> tooltip = new ArrayList<>(cir.getReturnValue());
            SecondaryTooltipUtil.insertSecondaryName(tooltip, name);
            cir.setReturnValue(tooltip);
        });
    }
}
