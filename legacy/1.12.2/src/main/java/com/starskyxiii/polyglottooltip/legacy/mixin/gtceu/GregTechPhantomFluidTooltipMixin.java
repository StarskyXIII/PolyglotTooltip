package com.starskyxiii.polyglottooltip.legacy.mixin.gtceu;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.starskyxiii.polyglottooltip.legacy.integration.gtceu.GregTechIntegration;

import net.minecraftforge.fluids.FluidStack;

@Pseudo
@Mixin(targets = "gregtech.api.gui.widgets.PhantomFluidWidget", remap = false)
public abstract class GregTechPhantomFluidTooltipMixin {
    @Shadow protected FluidStack lastFluidStack;

    @ModifyArg(method = "drawInForeground", index = 1, require = 0, at = @At(value = "INVOKE",
            target = "Lgregtech/api/gui/widgets/PhantomFluidWidget;drawHoveringText(Lnet/minecraft/item/ItemStack;Ljava/util/List;III)V"))
    private List<String> polyglot$appendNames(List<String> lines) {
        return GregTechIntegration.fluidTooltip(lines, lastFluidStack);
    }
}
