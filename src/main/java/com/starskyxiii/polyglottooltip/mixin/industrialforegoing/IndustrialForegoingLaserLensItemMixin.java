package com.starskyxiii.polyglottooltip.mixin.industrialforegoing;

import com.starskyxiii.polyglottooltip.integration.industrialforegoing.IndustrialForegoingNameHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.buuz135.industrial.item.LaserLensItem", remap = false)
public class IndustrialForegoingLaserLensItemMixin {

    @Shadow private int color;

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void onGetName(ItemStack stack, CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(IndustrialForegoingNameHelper.createLaserLensName(DyeColor.byId(color)));
    }
}
