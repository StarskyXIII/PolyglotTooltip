package com.starskyxiii.polyglottooltip.mixin.carbonconfig;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Consumer;

@Pseudo
@Mixin(targets = "carbonconfiglib.gui.impl.forge.ForgeValue", remap = false)
public interface LegacyCarbonForgeValueAccessor {
    @Accessor("saved")
    void polyglottooltip$setSaveCallback(Consumer<String> callback);
}
