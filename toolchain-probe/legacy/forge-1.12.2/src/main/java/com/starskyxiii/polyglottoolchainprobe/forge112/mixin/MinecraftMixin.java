package com.starskyxiii.polyglottoolchainprobe.forge112.mixin;

import com.starskyxiii.polyglottoolchainprobe.ProbePolicy;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void polyglottoolchainprobe$afterConstruction(CallbackInfo callbackInfo) {
        ProbePolicy.mixinApplied("1.12.2-forge-14.23.5.2864");
        Minecraft.func_71410_x();
        ProbePolicy.mappedCallOk("1.12.2-forge-14.23.5.2864");
    }
}
