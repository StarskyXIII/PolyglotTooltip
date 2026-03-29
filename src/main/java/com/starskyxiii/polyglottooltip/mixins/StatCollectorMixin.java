package com.starskyxiii.polyglottooltip.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.starskyxiii.polyglottooltip.i18n.TranslationOverrideContext;

import net.minecraft.util.StatCollector;

@Mixin(StatCollector.class)
public abstract class StatCollectorMixin {

    @Inject(method = "translateToLocal", at = @At("HEAD"), cancellable = true)
    private static void polyglot$overrideTranslateToLocal(String key, CallbackInfoReturnable<String> cir) {
        String translated = TranslationOverrideContext.translate(key);
        if (translated != null) {
            cir.setReturnValue(translated);
        }
    }

    @Inject(method = "translateToLocalFormatted", at = @At("HEAD"), cancellable = true)
    private static void polyglot$overrideTranslateToLocalFormatted(
        String key,
        Object[] args,
        CallbackInfoReturnable<String> cir) {
        String translated = TranslationOverrideContext.format(key, args);
        if (translated != null) {
            cir.setReturnValue(translated);
        }
    }

    @Inject(method = "canTranslate", at = @At("HEAD"), cancellable = true)
    private static void polyglot$overrideCanTranslate(String key, CallbackInfoReturnable<Boolean> cir) {
        if (TranslationOverrideContext.contains(key)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
