package com.starskyxiii.polyglottooltip.legacy.mixin.carbonconfig;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.starskyxiii.polyglottooltip.legacy.integration.carbonconfig.CarbonConfigIntegration;

@Pseudo
@Mixin(targets = "carbonconfiglib.gui.impl.forge.ForgeArray", remap = false)
public abstract class CarbonConfigArraySaveMixin {
    @Shadow private List<?> values;

    @Redirect(method = "save(Ljava/lang/String;Lcarbonconfiglib/gui/impl/forge/ForgeValue;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/List;set(ILjava/lang/Object;)Ljava/lang/Object;"),
            allow = 1)
    private Object polyglot$save(List<Object> current, int index, Object value, String text, @Coerce Object node) {
        return current.set(CarbonConfigIntegration.savedIndex(values, node, index), value);
    }
}
