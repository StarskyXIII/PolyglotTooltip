package com.starskyxiii.polyglottooltip.legacy.mixin.jei;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "mezz.jei.config.Config", remap = false)
public interface HeiConfigInvoker {
    @Invoker("doesSearchTreeNeedReload")
    static boolean polyglot$searchTreeNeedsReload() {
        throw new AssertionError();
    }
}
