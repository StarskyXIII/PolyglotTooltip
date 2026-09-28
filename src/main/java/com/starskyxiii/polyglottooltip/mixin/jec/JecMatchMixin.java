package com.starskyxiii.polyglottooltip.mixin.jec;
//~ minecraft_116_packages

import com.starskyxiii.polyglottooltip.integration.jei.JeiSearchRefreshBridge;
import com.starskyxiii.polyglottooltip.integration.jec.JecAcceleratorCacheGuard;
import com.starskyxiii.polyglottooltip.minecraft.MinecraftLanguageAdapter;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.towdium.jecharacters.utils.Match", remap = false)
public class JecMatchMixin {
    @Inject(
            method = {
                    "contains(Ljava/lang/String;Ljava/lang/CharSequence;)Z",
                    "equals(Ljava/lang/String;Ljava/lang/Object;)Z",
                    "startsWith(Ljava/lang/String;Ljava/lang/String;)Z",
                    "startsWith(Ljava/lang/String;Ljava/lang/String;I)Z"
            },
            at = @At("HEAD"), remap = false, require = 0
    )
    private static void invalidateAccelerator(CallbackInfoReturnable<Boolean> cir) {
        JecAcceleratorCacheGuard.beforeMatch();
    }

    @Inject(method = "onConfigChange()V", at = @At("RETURN"), remap = false, require = 0)
    private static void refreshJeiSearchResults(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            JeiSearchRefreshBridge.refreshResults();
            return;
        }
        minecraft.execute(() -> {
            MinecraftLanguageAdapter.getInstance().invalidateDependentCaches();
            JeiSearchRefreshBridge.refreshResults();
        });
    }
}
