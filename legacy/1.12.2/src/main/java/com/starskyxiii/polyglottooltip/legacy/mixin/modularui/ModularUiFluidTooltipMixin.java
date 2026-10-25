package com.starskyxiii.polyglottooltip.legacy.mixin.modularui;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cleanroommc.modularui.screen.RichTooltip;
import com.llamalad7.mixinextras.sugar.Local;
import com.starskyxiii.polyglottooltip.legacy.integration.modularui.ModularUiTooltips;

import net.minecraftforge.fluids.FluidStack;

@Pseudo
@Mixin(targets = "com.cleanroommc.modularui.widgets.slot.FluidSlot", remap = false)
public abstract class ModularUiFluidTooltipMixin {
    @Inject(method = {
            "addToolTip(Lcom/cleanroommc/modularui/screen/RichTooltip;)V",
            "lambda$new$0(Lcom/cleanroommc/modularui/screen/RichTooltip;)V"
    }, require = 0, at = @At(value = "INVOKE", ordinal = 0, shift = At.Shift.AFTER,
            target = "Lcom/cleanroommc/modularui/screen/RichTooltip;addLine(Lcom/cleanroommc/modularui/api/drawable/IDrawable;)Lcom/cleanroommc/modularui/api/drawable/IRichTextBuilder;"))
    private void polyglot$appendNames(CallbackInfo ci, @Local(argsOnly = true) RichTooltip tooltip, @Local FluidStack fluid) {
        ModularUiTooltips.appendFluidNames(tooltip, fluid);
    }
}
