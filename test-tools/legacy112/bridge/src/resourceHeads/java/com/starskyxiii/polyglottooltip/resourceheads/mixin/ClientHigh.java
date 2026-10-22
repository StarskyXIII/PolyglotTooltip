package com.starskyxiii.polyglottooltip.resourceheads.mixin;

import com.starskyxiii.polyglottooltip.resourceheads.HeadEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.Minecraft", priority = 2000, remap = false)
public abstract class ClientHigh {
    @Inject(method = "func_110436_a()V", at = @At("HEAD"), require = 1)
    private void head(CallbackInfo callback) { HeadEvents.record("client-high"); }
}
