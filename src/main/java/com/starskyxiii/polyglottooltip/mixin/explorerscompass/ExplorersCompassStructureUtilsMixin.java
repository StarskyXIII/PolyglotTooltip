package com.starskyxiii.polyglottooltip.mixin.explorerscompass;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.starskyxiii.polyglottooltip.integration.compass.CompassNameHelper;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.chaosthedude.explorerscompass.util.StructureUtils", remap = false)
public abstract class ExplorersCompassStructureUtilsMixin {

    @ModifyReturnValue(
            method = "getStructureName(Lnet/minecraft/resources/Identifier;)Ljava/lang/String;",
            at = @At("RETURN")
    )
    private static String polyglottooltip$appendSecondaryStructureNames(String original, Identifier key) {
        return CompassNameHelper.appendStructureSecondaryNames(key, original);
    }
}
