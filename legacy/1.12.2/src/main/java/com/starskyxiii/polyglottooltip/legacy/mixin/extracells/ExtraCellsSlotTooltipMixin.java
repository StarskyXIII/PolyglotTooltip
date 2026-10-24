package com.starskyxiii.polyglottooltip.legacy.mixin.extracells;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.starskyxiii.polyglottooltip.legacy.integration.extracells.ExtraCellsFluids;

import net.minecraftforge.fluids.Fluid;

@Pseudo
@Mixin(targets = "extracells.gui.widget.fluid.WidgetFluidSlot", remap = false)
public abstract class ExtraCellsSlotTooltipMixin {
    @Shadow private Fluid fluid;

    @Inject(method = "getToolTip(II)Ljava/util/List;", at = @At("RETURN"), cancellable = true, require = 0)
    private void polyglot$appendNames(int mouseX, int mouseY, CallbackInfoReturnable<List<String>> info) {
        List<String> lines = info.getReturnValue();
        List<String> named = ExtraCellsFluids.withNames(lines, fluid);
        if (named != lines) {
            info.setReturnValue(named);
        }
    }
}
