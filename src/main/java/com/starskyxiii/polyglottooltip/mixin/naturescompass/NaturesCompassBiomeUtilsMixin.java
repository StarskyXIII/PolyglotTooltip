package com.starskyxiii.polyglottooltip.mixin.naturescompass;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.starskyxiii.polyglottooltip.integration.compass.CompassNameHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.chaosthedude.naturescompass.util.BiomeUtils", remap = false)
public abstract class NaturesCompassBiomeUtilsMixin {

    @ModifyReturnValue(
            method = "getBiomeNameForDisplay(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/biome/Biome;)Ljava/lang/String;",
            at = @At("RETURN")
    )
    private static String polyglottooltip$appendSecondaryBiomeNames(String original, Level level, Biome biome) {
        if (level == null || biome == null) {
            return original;
        }

        ResourceLocation biomeKey = level.registryAccess()
                .registry(Registries.BIOME)
                .map(registry -> registry.getKey(biome))
                .orElse(null);
        return CompassNameHelper.appendBiomeSecondaryNames(biomeKey, original);
    }
}
