package com.starskyxiii.polyglottooltip.mixin.carbonconfig;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "carbonconfiglib.gui.impl.forge.ForgeArray", remap = false)
public class LegacyCarbonForgeArrayRemoveMixin {
    @Shadow private List<?> values;
    @Shadow private List<String> currentValues;

    @Inject(method = "removeNode(I)V", at = @At("HEAD"), require = 0)
    private void polyglottooltip$trackRemainingRows(int removedIndex, CallbackInfo ci) {
        for (Object row : values) {
            ((LegacyCarbonForgeValueAccessor) row).polyglottooltip$setSaveCallback(value -> {
                for (int index = 0; index < values.size(); index++) {
                    if (values.get(index) == row) {
                        currentValues.set(index, value);
                        return;
                    }
                }
            });
        }
    }
}
