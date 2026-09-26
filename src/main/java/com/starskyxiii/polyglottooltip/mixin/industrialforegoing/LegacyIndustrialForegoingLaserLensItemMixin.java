package com.starskyxiii.polyglottooltip.mixin.industrialforegoing;

import com.starskyxiii.polyglottooltip.integration.industrialforegoing.IndustrialForegoingNameHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.buuz135.industrial.item.LaserLensItem", remap = false)
public abstract class LegacyIndustrialForegoingLaserLensItemMixin {

    @Shadow private int color;

    @Inject(method = {"getName", "m_7626_"}, at = @At("RETURN"), cancellable = true, remap = false)
    private void polyglottooltip$preserveTranslatableName(ItemStack stack,
                                                           CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(IndustrialForegoingNameHelper.createLaserLensName(DyeColor.byId(color)));
    }
}
