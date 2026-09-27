package com.starskyxiii.polyglottooltip;
//~ forge_neoforge_package

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

//? !fabric {
//? if <=1.20.1 {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.DistExecutor;
import net.neoforged.fml.ModList;
import net.minecraftforge.common.ForgeConfigSpec;
*///?} else {
import net.neoforged.bus.api.IEventBus;
//?}
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
//?}

//? !fabric {
@Mod(PolyglotTooltip.MODID)
//?}
public class PolyglotTooltip {
    public static final String MODID = "polyglottooltip";
    public static final Logger LOGGER = LogManager.getLogger();

    //? !fabric {
    //? if <=1.20.1 {
    /*public PolyglotTooltip() {
        registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> PolyglotTooltipClient::init);
    }

    private static void registerConfig(ModConfig.Type type, ForgeConfigSpec spec) {
        ModContainer modContainer = ModList.get()
                .getModContainerById(MODID)
                .orElseThrow(() -> new IllegalStateException("Missing mod container for " + MODID));
        modContainer.addConfig(new ModConfig(type, spec, modContainer));
    }
    *///?} else {
    public PolyglotTooltip(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
    }
    //?}
    //?}
}
