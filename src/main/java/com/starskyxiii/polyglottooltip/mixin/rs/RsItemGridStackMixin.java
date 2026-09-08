package com.starskyxiii.polyglottooltip.mixin.rs;

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.screen.grid.stack.ItemGridStack", remap = false)
public abstract class RsItemGridStackMixin {

    @Shadow
    @Final
    private ItemStack stack;

    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
    private void polyglot$appendSecondaryName(boolean bypassCache, CallbackInfoReturnable<List<Component>> cir) {
        List<Component> tooltip = new ArrayList<>(cir.getReturnValue());
        SecondaryTooltipUtil.insertSecondaryName(tooltip, stack);
        cir.setReturnValue(tooltip);
    }
}
