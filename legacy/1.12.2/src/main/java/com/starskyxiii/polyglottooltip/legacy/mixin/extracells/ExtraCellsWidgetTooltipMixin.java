package com.starskyxiii.polyglottooltip.legacy.mixin.extracells;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.starskyxiii.polyglottooltip.legacy.integration.extracells.ExtraCellsFluids;

import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

@Pseudo
@Mixin(targets = "extracells.gui.widget.fluid.AbstractFluidWidget", remap = false)
public abstract class ExtraCellsWidgetTooltipMixin {
    @Shadow protected Fluid fluid;

    @Inject(method = "drawHoveringText(Ljava/util/List;IILnet/minecraft/client/gui/FontRenderer;)V", at = @At("HEAD"),
            require = 0)
    private void polyglot$appendNames(List<String> lines, int x, int y, FontRenderer font, CallbackInfo info) {
        if (fluid != null) {
            ExtraCellsFluids.appendNames(lines, new FluidStack(fluid, 1000));
        }
    }
}
