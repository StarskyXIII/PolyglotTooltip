package com.starskyxiii.polyglottooltip.mixin.explorerscompass;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.starskyxiii.polyglottooltip.integration.compass.CompassNameHelper;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.chaosthedude.explorerscompass.util.StructureUtils", remap = false)
public abstract class ExplorersCompassStructureUtilsMixin {

    @ModifyReturnValue(
            method = "getPrettyStructureName(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;",
            at = @At("RETURN")
    )
    private static String polyglottooltip$appendSecondaryStructureNames(String original, ResourceLocation key) {
        return CompassNameHelper.appendStructureSecondaryNames(key, original);
    }
}
