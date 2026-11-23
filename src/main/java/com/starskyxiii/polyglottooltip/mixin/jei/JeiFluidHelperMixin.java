package com.starskyxiii.polyglottooltip.mixin.jei;
//~ forge_neoforge_package minecraft_116_packages

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
//? if >=1.19.2 && <=1.20.1 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
*///?}
import net.minecraft.network.chat.Component;
//? if >=1.19.2 && <=1.20.1 {
/*import net.minecraft.network.chat.FormattedText;
import mezz.jei.api.gui.builder.ITooltipBuilder;
*///?}
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
//? if >=1.19.2 && <=1.20.1 {
/*
import org.spongepowered.asm.mixin.injection.At;
*///?}

import java.util.List;
//? if forge && 1.18.2 {
/*import java.util.ArrayList;
*///?}

/**
 * Optional JEI integration — appends the secondary-language fluid name to every
 * fluid tooltip in JEI's ingredient panel.
 *
 * <p>Targets {@code mezz.jei.neoforge.platform.FluidHelper#getTooltip} which
 * is the NeoForge-platform method JEI calls to populate a fluid ingredient's
 * tooltip list. The {@code targets} string form (rather than a {@code value}
 * class reference) means this Mixin compiles and loads without JEI on the
 * classpath; when JEI is absent the Mixin system simply skips the injection.
 */
@Pseudo
//? if <=1.20.1 {
/*@Mixin(targets = "mezz.jei.forge.platform.FluidHelper", remap = false)
*///?} else {
@Mixin(targets = "mezz.jei.neoforge.platform.FluidHelper", remap = false)
//?}
public class JeiFluidHelperMixin {

    @WrapMethod(
            //? if <=1.20.1 {
            /*method = "getTooltip(Ljava/util/List;Lnet/minecraftforge/fluids/FluidStack;Lnet/minecraft/world/item/TooltipFlag;)V",
            *///?} else {
            method = "getTooltip(Ljava/util/List;Lnet/neoforged/neoforge/fluids/FluidStack;Lnet/minecraft/world/item/TooltipFlag;)V",
            //?}
            remap = false,
            require = 0
    )
    private void onGetTooltip(List<Component> tooltip,
                               FluidStack ingredient,
                               TooltipFlag tooltipFlag,
                               Operation<Void> original) {
        original.call(tooltip, ingredient, tooltipFlag);
        if (ingredient.isEmpty()) {
            return;
        }
        //? if <=1.20.1 {
        /*SecondaryTooltipUtil.insertSecondaryName(tooltip, ingredient.getDisplayName());
        *///?} else {
        SecondaryTooltipUtil.insertSecondaryName(tooltip, ingredient.getHoverName());
        //?}
    }

    //? if forge && 1.18.2 {
    /*@WrapMethod(
            method = "getTooltip(Lnet/minecraftforge/fluids/FluidStack;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;",
            remap = false,
            require = 0
    )
    private List<Component> onGetLegacyListTooltip(FluidStack ingredient,
                                                   TooltipFlag tooltipFlag,
                                                   Operation<List<Component>> original) {
        List<Component> tooltip = original.call(ingredient, tooltipFlag);
        if (ingredient.isEmpty()) {
            return tooltip;
        }
        List<Component> translated = new ArrayList<>(tooltip);
        SecondaryTooltipUtil.insertSecondaryName(translated, ingredient.getDisplayName());
        return translated;
    }
    *///?}

    //? if >=1.19.2 && <=1.20.1 {
    /*@WrapOperation(
            method = "getTooltip(Lmezz/jei/api/gui/builder/ITooltipBuilder;Lnet/minecraftforge/fluids/FluidStack;Lnet/minecraft/world/item/TooltipFlag;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/api/gui/builder/ITooltipBuilder;add(Lnet/minecraft/network/chat/FormattedText;)V",
                    ordinal = 0,
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private void onLegacyTooltipName(ITooltipBuilder builder,
                                     FormattedText primaryName,
                                     Operation<Void> original) {
        original.call(builder, primaryName);
        if (primaryName instanceof Component component) {
            for (Component secondaryName : SecondaryTooltipUtil.getSecondaryNameLines(component)) {
                original.call(builder, secondaryName);
            }
        }
    }
    *///?}
}
