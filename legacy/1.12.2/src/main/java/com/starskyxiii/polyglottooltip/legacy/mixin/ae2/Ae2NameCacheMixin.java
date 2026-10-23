package com.starskyxiii.polyglottooltip.legacy.mixin.ae2;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.starskyxiii.polyglottooltip.legacy.integration.ae2.Ae2Search;

@Pseudo
@Mixin(targets = "appeng.util.item.AEItemStack", remap = false)
public abstract class Ae2NameCacheMixin {
    @Shadow private String displayName;
    @Shadow private List<String> tooltip;

    @Unique private int polyglot$generation;

    @Inject(method = {"getDisplayName", "getToolTip"}, at = @At("HEAD"), require = 0)
    private void polyglot$invalidate(CallbackInfoReturnable<?> cir) {
        int generation = Ae2Search.generation();
        if (polyglot$generation != generation && Ae2Search.enabled()) {
            displayName = null;
            tooltip = null;
            polyglot$generation = generation;
        }
    }
}
