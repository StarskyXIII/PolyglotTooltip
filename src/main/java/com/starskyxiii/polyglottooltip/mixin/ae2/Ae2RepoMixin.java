package com.starskyxiii.polyglottooltip.mixin.ae2;

import appeng.client.gui.me.common.Repo;
import com.starskyxiii.polyglottooltip.integration.ae2.Ae2SearchRefresh;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "appeng.client.gui.me.common.Repo", remap = false)
public abstract class Ae2RepoMixin {
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void polyglottooltip$registerRepo(CallbackInfo ci) {
        Ae2SearchRefresh.registerRepo((Repo) (Object) this);
    }
}
