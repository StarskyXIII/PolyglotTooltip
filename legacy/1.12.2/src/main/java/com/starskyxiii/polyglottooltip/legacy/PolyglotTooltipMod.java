package com.starskyxiii.polyglottooltip.legacy;

import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryLanguages;
import com.starskyxiii.polyglottooltip.legacy.minecraft.TooltipLines;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(
        modid = PolyglotTooltipMod.MOD_ID,
        name = "Polyglot Tooltip",
        version = "2.0",
        acceptedMinecraftVersions = "[1.12.2]",
        clientSideOnly = true)
public final class PolyglotTooltipMod {
    public static final String MOD_ID = "polyglottooltip";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Config.initialize(event.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        SecondaryLanguages.load();
        MinecraftForge.EVENT_BUS.register(new TooltipLines());
    }
}
