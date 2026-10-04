package com.starskyxiii.polyglotnameaudit.mixin;

import com.starskyxiii.polyglotnameaudit.AuditScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientTextTooltip.class)
public abstract class ClientTextTooltipMixin {
    @Shadow @Final private FormattedCharSequence text;
    @Inject(method = "renderText", at = @At("RETURN"))
    private void audit$render(CallbackInfo callback) { AuditScreen.capture(text); }
}
