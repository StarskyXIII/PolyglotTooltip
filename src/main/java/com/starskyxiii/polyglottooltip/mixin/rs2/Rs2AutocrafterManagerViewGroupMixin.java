package com.starskyxiii.polyglottooltip.mixin.rs2;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.starskyxiii.polyglottooltip.integration.rs2.Rs2SearchUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.refinedmods.refinedstorage.common.autocrafting.autocraftermanager.AutocrafterManagerContainerMenu$ViewGroup", remap = false)
public abstract class Rs2AutocrafterManagerViewGroupMixin {

    @Shadow
    @Final
    private String name;

    @ModifyReturnValue(method = "nameContains", at = @At("RETURN"))
    private boolean expandNameSearch(boolean original, String normalizedQuery) {
        return original || Rs2SearchUtil.matchesAutocrafterName(normalizedQuery, name);
    }
}
