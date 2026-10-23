package com.starskyxiii.polyglottooltip.legacy.mixin.rs;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.starskyxiii.polyglottooltip.legacy.integration.rs.RsSearch;

@Pseudo
@Mixin(targets = "com.raoulvdberge.refinedstorage.gui.grid.stack.GridStackItem", remap = false)
public abstract class RsNameCacheMixin {
    @Shadow private String cachedName;
    @Shadow private String tooltip;

    @Unique private int polyglot$generation;

    @Inject(method = {"getName", "getTooltip"}, at = @At("HEAD"), require = 0)
    private void polyglot$invalidate(CallbackInfoReturnable<String> cir) {
        int generation = RsSearch.generation();
        if (polyglot$generation != generation && RsSearch.enabled()) {
            cachedName = null;
            tooltip = null;
            polyglot$generation = generation;
        }
    }
}
