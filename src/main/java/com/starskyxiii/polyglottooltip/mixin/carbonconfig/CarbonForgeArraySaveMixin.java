package com.starskyxiii.polyglottooltip.mixin.carbonconfig;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "carbonconfiglib.gui.impl.forge.ForgeArray", remap = false)
public class CarbonForgeArraySaveMixin {
    @ModifyConstant(
            method = "save(Ljava/lang/String;Lcarbonconfiglib/gui/impl/forge/ForgeValue;)V",
            constant = @Constant(intValue = 0),
            require = 0
    )
    private int polyglottooltip$useEntryIndex(int original, @Local(index = 3) int index) {
        return index;
    }
}
