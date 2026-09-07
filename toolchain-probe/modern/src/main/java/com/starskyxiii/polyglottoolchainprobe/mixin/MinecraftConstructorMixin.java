package com.starskyxiii.polyglottoolchainprobe.mixin;

import com.starskyxiii.polyglottoolchainprobe.ToolchainProbe;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftConstructorMixin {
    private static boolean polyglottoolchainprobe$reported;
    private static boolean polyglottoolchainprobe$mappedMethodReported;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void polyglottoolchainprobe$afterConstruction(CallbackInfo callbackInfo) {
        if (!polyglottoolchainprobe$reported) {
            polyglottoolchainprobe$reported = true;
            ToolchainProbe.LOGGER.info("[PolyglotToolchainProbe] MIXIN_APPLIED");
        }
    }

    @Inject(method = "getInstance", at = @At("HEAD"))
    private static void polyglottoolchainprobe$onGetInstance(CallbackInfoReturnable<Minecraft> callbackInfo) {
        if (!polyglottoolchainprobe$mappedMethodReported) {
            polyglottoolchainprobe$mappedMethodReported = true;
            ToolchainProbe.LOGGER.info("[PolyglotToolchainProbe] MAPPED_METHOD_APPLIED");
        }
    }
}
