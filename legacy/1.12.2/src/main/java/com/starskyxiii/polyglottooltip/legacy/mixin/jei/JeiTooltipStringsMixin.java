package com.starskyxiii.polyglottooltip.legacy.mixin.jei;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.starskyxiii.polyglottooltip.legacy.integration.jei.JeiSearch;

import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;

@Pseudo
@Mixin(targets = "mezz.jei.ingredients.IngredientInformation", remap = false)
public abstract class JeiTooltipStringsMixin {
    @Redirect(method = "getTooltipStrings", at = @At(value = "INVOKE",
            target = "Lmezz/jei/api/ingredients/IIngredientRenderer;getTooltip(Lnet/minecraft/client/Minecraft;Ljava/lang/Object;Lnet/minecraft/client/util/ITooltipFlag;)Ljava/util/List;"))
    private static List<String> polyglot$searchTooltip(IIngredientRenderer<Object> renderer, Minecraft minecraft,
            Object ingredient, ITooltipFlag flag) {
        return JeiSearch.searchTooltip(() -> renderer.getTooltip(minecraft, ingredient, flag));
    }
}
