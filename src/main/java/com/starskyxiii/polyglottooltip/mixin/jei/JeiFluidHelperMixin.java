package com.starskyxiii.polyglottooltip.mixin.jei;
//~ forge_neoforge_package minecraft_116_packages

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
//? if <=1.20.1 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
*///?}
import net.minecraft.network.chat.Component;
//? if <=1.20.1 {
/*import net.minecraft.network.chat.FormattedText;
import mezz.jei.api.gui.builder.ITooltipBuilder;
*///?}
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

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

    @Inject(
            //? if <=1.20.1 {
            /*method = "getTooltip(Ljava/util/List;Lnet/minecraftforge/fluids/FluidStack;Lnet/minecraft/world/item/TooltipFlag;)V",
            *///?} else {
            method = "getTooltip(Ljava/util/List;Lnet/neoforged/neoforge/fluids/FluidStack;Lnet/minecraft/world/item/TooltipFlag;)V",
            //?}
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private void onGetTooltip(List<Component> tooltip,
                               FluidStack ingredient,
                               TooltipFlag tooltipFlag,
                               CallbackInfo ci) {
        if (ingredient.isEmpty()) {
            return;
        }
        //? if <=1.20.1 {
        /*SecondaryTooltipUtil.insertSecondaryName(tooltip, ingredient.getDisplayName());
        *///?} else {
        SecondaryTooltipUtil.insertSecondaryName(tooltip, ingredient.getHoverName());
        //?}
    }

    //? if <=1.20.1 {
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
