package com.starskyxiii.polyglottooltip.mixin.rs;

import com.starskyxiii.polyglottooltip.integration.rs.RsSearchUtil;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.screen.grid.stack.FluidGridStack", remap = false)
public abstract class RsFluidGridStackMixin {

    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
    private void polyglot$appendSecondaryName(boolean bypassCache, CallbackInfoReturnable<List<Component>> cir) {
        Component displayName = RsSearchUtil.getIngredientDisplayName(this);
        if (displayName == null) {
            return;
        }

        List<Component> tooltip = new ArrayList<>(cir.getReturnValue());
        SecondaryTooltipUtil.insertSecondaryName(tooltip, displayName);
        cir.setReturnValue(tooltip);
    }
}
