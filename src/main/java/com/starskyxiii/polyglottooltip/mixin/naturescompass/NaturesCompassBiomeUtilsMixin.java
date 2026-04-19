package com.starskyxiii.polyglottooltip.mixin.naturescompass;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.starskyxiii.polyglottooltip.integration.compass.CompassNameHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.chaosthedude.naturescompass.util.BiomeUtils", remap = false)
public abstract class NaturesCompassBiomeUtilsMixin {

    @ModifyReturnValue(
            method = "getBiomeNameForDisplay(Lnet/minecraft/world/level/Level;Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;",
            at = @At("RETURN")
    )
    private static String polyglottooltip$appendSecondaryBiomeNames(String original, Level level, ResourceLocation biomeKey) {
        return CompassNameHelper.appendBiomeSecondaryNames(biomeKey, original);
    }
}
