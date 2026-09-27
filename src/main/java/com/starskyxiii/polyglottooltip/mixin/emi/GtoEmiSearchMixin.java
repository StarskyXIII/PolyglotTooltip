package com.starskyxiii.polyglottooltip.mixin.emi;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.starskyxiii.polyglottooltip.integration.jei.EmiSearchRefreshScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.emi.emi.search.EmiSearch", priority = 1500, remap = false)
public class GtoEmiSearchMixin {
    @TargetHandler(mixin = "com.gtolib.mixin.emi.screen.EmiSearchMixin",
            name = "bake", prefix = "handler")
    @WrapWithCondition(method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE", target = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;cancel()V"),
            require = 1, expect = 1, allow = 1, remap = false)
    private static boolean polyglot$allowGtoBakeCancellation(CallbackInfo callback) {
        return !EmiSearchRefreshScope.isActive();
    }
}
