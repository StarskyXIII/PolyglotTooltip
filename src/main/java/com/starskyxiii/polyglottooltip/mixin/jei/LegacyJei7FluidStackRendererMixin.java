package com.starskyxiii.polyglottooltip.mixin.jei;
//~ minecraft_116_packages

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Pseudo
@Mixin(targets = "mezz.jei.plugins.vanilla.ingredients.fluid.FluidStackRenderer", remap = false)
public class LegacyJei7FluidStackRendererMixin {
    @Inject(
            method = "getTooltip(Lnet/minecraftforge/fluids/FluidStack;Lnet/minecraft/client/util/TooltipFlag;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 1
    )
    private void polyglot$appendSecondaryFluidName(FluidStack ingredient,
                                                 TooltipFlag tooltipFlag,
                                                 CallbackInfoReturnable<List<Component>> cir) {
        if (ingredient.isEmpty()) {
            return;
        }
        List<Component> tooltip = new ArrayList<>(cir.getReturnValue());
        SecondaryTooltipUtil.insertSecondaryName(tooltip, ingredient.getDisplayName());
        cir.setReturnValue(tooltip);
    }
}
