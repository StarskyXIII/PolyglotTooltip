package com.starskyxiii.polyglottooltip.mixin;

import com.starskyxiii.polyglottooltip.minecraft.MinecraftLanguageAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.resources.LanguageManager", remap = false)
public abstract class LegacyLanguageManagerMixin {
    @Inject(
            method = "func_195410_a(Lnet/minecraft/resources/IResourceManager;)V",
            at = @At("RETURN"),
            require = 1,
            remap = false
    )
    private void polyglottooltip$invalidateLanguageDependentCaches(CallbackInfo ci) {
        MinecraftLanguageAdapter.getInstance().invalidateDependentCaches();
    }
}
