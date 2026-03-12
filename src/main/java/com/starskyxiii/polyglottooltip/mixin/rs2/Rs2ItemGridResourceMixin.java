package com.starskyxiii.polyglottooltip.mixin.rs2;

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "com.refinedmods.refinedstorage.common.grid.view.ItemGridResource", remap = false)
public class Rs2ItemGridResourceMixin {

    @Shadow @Final private ItemStack itemStack;

    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
    private void onGetTooltip(CallbackInfoReturnable<List<Component>> cir) {
        List<Component> tooltip = new ArrayList<>(cir.getReturnValue());
        SecondaryTooltipUtil.insertSecondaryName(tooltip, itemStack);
        cir.setReturnValue(tooltip);
    }
}
