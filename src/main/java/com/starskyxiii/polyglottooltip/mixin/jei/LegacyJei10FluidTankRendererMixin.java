package com.starskyxiii.polyglottooltip.mixin.jei;

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Pseudo
@Mixin(targets = "mezz.jei.common.render.FluidTankRenderer", remap = false)
public class LegacyJei10FluidTankRendererMixin {
    @Inject(
            method = "getTooltip(Ljava/lang/Object;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 1
    )
    private void polyglot$appendSecondaryFluidName(Object ingredient,
                                                   TooltipFlag tooltipFlag,
                                                   CallbackInfoReturnable<List<Component>> cir) {
        if (!(ingredient instanceof FluidStack fluidStack) || fluidStack.isEmpty()) {
            return;
        }
        List<Component> tooltip = new ArrayList<>(cir.getReturnValue());
        SecondaryTooltipUtil.insertSecondaryName(tooltip, fluidStack.getDisplayName());
        cir.setReturnValue(tooltip);
    }
}
