package com.starskyxiii.polyglotnameaudit.mixin;

import com.starskyxiii.polyglotnameaudit.AuditProbe;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.starskyxiii.polyglottooltip.SecondaryTooltipUtil", remap = false)
public abstract class SecondaryLineMixin {
    @Inject(method = "createTooltipSecondaryLine", at = @At("RETURN"))
    private static void audit$line(String secondary, CallbackInfoReturnable<Component> callback) {
        AuditProbe.capture(callback.getReturnValue());
    }
}
