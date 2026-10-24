package com.starskyxiii.polyglottooltip.legacy.mixin.extracells;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.starskyxiii.polyglottooltip.legacy.integration.extracells.ExtraCellsFluids;

import net.minecraftforge.fluids.IFluidTank;

@Pseudo
@Mixin(targets = "extracells.gui.widget.WidgetFluidTank", remap = false)
public abstract class ExtraCellsTankTooltipMixin {
    @Shadow private IFluidTank tank;

    @Inject(method = "getToolTip(II)Ljava/util/List;", at = @At("RETURN"), require = 0)
    private void polyglot$appendNames(int mouseX, int mouseY, CallbackInfoReturnable<List<String>> info) {
        if (tank != null) {
            ExtraCellsFluids.appendNames(info.getReturnValue(), tank.getFluid());
        }
    }
}
