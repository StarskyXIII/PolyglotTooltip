package com.starskyxiii.polyglottooltip.resourceheads.mixin;

import com.starskyxiii.polyglottooltip.resourceheads.HeadEvents;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.resources.SimpleReloadableResourceManager", priority = 2000, remap = false)
public abstract class ManagerHigh {
    @Inject(method = "func_110541_a(Ljava/util/List;)V", at = @At("HEAD"), require = 1)
    private void full(List<?> packs, CallbackInfo callback) { HeadEvents.record("full-high"); }
    @Inject(method = "func_110542_a(Lnet/minecraft/client/resources/IResourceManagerReloadListener;)V", at = @At("HEAD"), require = 1)
    private void registration(IResourceManagerReloadListener listener, CallbackInfo callback) { HeadEvents.record("registration-high"); }
}
