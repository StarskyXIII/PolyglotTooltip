package com.starskyxiii.polyglottooltip.integration.jei;

import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.network.chat.Component;

public final class FabricJeiFluidNameHelper {
    private FabricJeiFluidNameHelper() {
    }

    public static boolean isEmpty(IJeiFluidIngredient ingredient) {
        return ingredient.getAmount() <= 0 || variant(ingredient).isBlank();
    }

    public static Component getDisplayName(IJeiFluidIngredient ingredient) {
        return FluidVariantAttributes.getName(variant(ingredient));
    }

    private static FluidVariant variant(IJeiFluidIngredient ingredient) {
        //? if <=1.20.1 {
        /*return FluidVariant.of(ingredient.getFluid(), ingredient.getTag().orElse(null));
        *///?} else {
        return ingredient.getFluidVariant();
        //?}
    }
}
