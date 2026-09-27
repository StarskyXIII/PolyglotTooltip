package com.starskyxiii.polyglottooltip.mixin.naturescompass;
//~ minecraft_identifier minecraft_116_packages

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.starskyxiii.polyglottooltip.integration.compass.CompassNameHelper;
//? if <26 {
//? if <=1.19.2 {
/*import net.minecraft.core.Registry;
*///?} else {
import net.minecraft.core.registries.Registries;
//?}
//?}
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
//? if <26 {
import net.minecraft.world.level.biome.Biome;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
//? if fabric && <26 {
/*@Mixin(targets = "com.chaosthedude.naturescompass.utils.BiomeUtils", remap = false)
*///?} else {
@Mixin(targets = "com.chaosthedude.naturescompass.util.BiomeUtils", remap = false)
//?}
public abstract class NaturesCompassBiomeUtilsMixin {

    @ModifyReturnValue(
            //? if fabric && >=1.21.1 && <26 {
            /*method = "getBiomeNameForDisplay(Lnet/minecraft/world/level/Level;Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;",
            *///?} else if fabric {
            /*method = "getBiomeNameForDisplay",
            *///?} else if >=26 {
            /*method = "getBiomeNameForDisplay(Lnet/minecraft/world/level/Level;Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;",
            *///?} else {
            method = "getBiomeNameForDisplay(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/biome/Biome;)Ljava/lang/String;",
            //?}
            at = @At("RETURN")
    )
    //? if fabric {
    /*private static String polyglottooltip$appendSecondaryBiomeNames(String original, Level level, ResourceLocation biomeId) {
        if (level == null || biomeId == null) {
            return original;
        }
        return CompassNameHelper.appendBiomeSecondaryNames(biomeId, original);
    *///?} else if >=26 {
    /*private static String polyglottooltip$appendSecondaryBiomeNames(String original, Level level, ResourceLocation biomeId) {
        if (level == null || biomeId == null) {
            return original;
        }
        return CompassNameHelper.appendBiomeSecondaryNames(biomeId, original);
    *///?} else {
    private static String polyglottooltip$appendSecondaryBiomeNames(String original, Level level, Biome biome) {
        if (level == null || biome == null) {
            return original;
        }

        ResourceLocation biomeKey = level.registryAccess()
                //? if <=1.19.2 {
                /*.registry(Registry.BIOME_REGISTRY)
                *///?} else {
                .registry(Registries.BIOME)
                //?}
                .map(registry -> registry.getKey(biome))
                .orElse(null);
        return CompassNameHelper.appendBiomeSecondaryNames(biomeKey, original);
    //?}
    }
}
