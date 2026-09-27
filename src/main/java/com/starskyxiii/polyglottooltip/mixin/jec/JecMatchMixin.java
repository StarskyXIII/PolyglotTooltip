package com.starskyxiii.polyglottooltip.mixin.jec;
//~ minecraft_116_packages

import com.starskyxiii.polyglottooltip.integration.jei.JeiSearchRefreshBridge;
import com.starskyxiii.polyglottooltip.minecraft.MinecraftLanguageAdapter;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "me.towdium.jecharacters.utils.Match", remap = false)
public class JecMatchMixin {
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
