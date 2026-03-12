package com.starskyxiii.polyglottooltip.mixin.rs2;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.starskyxiii.polyglottooltip.integration.rs2.Rs2SearchUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.refinedmods.refinedstorage.common.autocrafting.autocraftermanager.AutocrafterManagerContainerMenu$SubViewGroup", remap = false)
public class Rs2AutocrafterManagerSubViewGroupMixin {

    @ModifyReturnValue(
            method = "hasResource(Ljava/lang/String;Lcom/refinedmods/refinedstorage/api/resource/ResourceKey;)Z",
            at = @At("RETURN"),
            remap = false
    )
    private static boolean expandResourceSearch(boolean original, String normalizedQuery, ResourceKey key) {
        return original || Rs2SearchUtil.matchesAutocrafterResource(normalizedQuery, key);
    }
}
