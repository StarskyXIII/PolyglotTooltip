package com.starskyxiii.polyglotnameaudit.mixin;

import com.starskyxiii.polyglotnameaudit.AuditProbe;
import org.cyclops.clientdevbridge.protocol.Dispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Dispatcher.class, remap = false)
public abstract class DispatcherMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void audit$register(CallbackInfo callback) { AuditProbe.register((Dispatcher) (Object) this); }
}
