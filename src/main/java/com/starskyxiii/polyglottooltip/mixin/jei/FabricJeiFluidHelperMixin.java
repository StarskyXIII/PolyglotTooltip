package com.starskyxiii.polyglottooltip.mixin.jei;
//~ minecraft_116_packages

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.starskyxiii.polyglottooltip.integration.jei.FabricJeiFluidNameHelper;
import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import net.minecraft.network.chat.Component;
//? if >=26 {
/*import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
*///?}
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26 {
/*import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

import java.util.List;

@Pseudo
@Mixin(targets = "mezz.jei.fabric.platform.FluidHelper", remap = false)
public class FabricJeiFluidHelperMixin {
    //? if <26 {
    @Inject(
            method = "getTooltip(Ljava/util/List;Lmezz/jei/api/fabric/ingredients/fluids/IJeiFluidIngredient;Lnet/minecraft/world/item/TooltipFlag;)V",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private void polyglot$appendSecondaryFluidName(List<Component> tooltip,
                                                    IJeiFluidIngredient ingredient,
                                                    TooltipFlag tooltipFlag,
                                                    CallbackInfo ci) {
        if (!FabricJeiFluidNameHelper.isEmpty(ingredient)) {
            SecondaryTooltipUtil.insertSecondaryName(
                    tooltip,
                    FabricJeiFluidNameHelper.getDisplayName(ingredient));
        }
    }
    //?} else {
    /*@Inject(
            method = "getTooltip(Lmezz/jei/api/fabric/ingredients/fluids/IJeiFluidIngredient;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void polyglot$appendSecondaryFluidName(IJeiFluidIngredient ingredient,
                                                    Player player,
                                                    Item.TooltipContext tooltipContext,
                                                    TooltipFlag tooltipFlag,
                                                    CallbackInfoReturnable<List<Component>> cir) {
        if (FabricJeiFluidNameHelper.isEmpty(ingredient)) {
            return;
        }
        List<Component> tooltip = new java.util.ArrayList<>(cir.getReturnValue());
        SecondaryTooltipUtil.insertSecondaryName(
                tooltip,
                FabricJeiFluidNameHelper.getDisplayName(ingredient));
        cir.setReturnValue(tooltip);
    }
    *///?}
}
