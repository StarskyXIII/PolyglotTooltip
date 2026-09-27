package com.starskyxiii.polyglottooltip.mixin.ldlib;
//~ minecraft_116_packages

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.starskyxiii.polyglottooltip.integration.FluidTooltipHelper;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
@Pseudo
@Mixin(targets = "com.lowdragmc.lowdraglib.gui.widget.TankWidget", remap = false)
public class LdlibTankWidgetFluidTooltipMixin {

    @Inject(method = "getFullTooltipTexts", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void polyglot$appendSecondaryFluidName(CallbackInfoReturnable<List<Component>> cir) {
        List<Component> original = cir.getReturnValue();
        List<Component> updated = FluidTooltipHelper.withSecondaryFluidName(original);
        if (updated != original) {
            cir.setReturnValue(updated);
        }
    }

    @ModifyExpressionValue(
            method = "drawInForeground",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/lowdragmc/lowdraglib/gui/widget/TankWidget;getToolTips(Ljava/util/List;)Ljava/util/List;"
            ),
            require = 0,
            remap = false
    )
    private List<Component> polyglot$appendLegacySecondaryFluidName(List<Component> original) {
        return FluidTooltipHelper.withSecondaryFluidName(original);
    }
}
