package com.starskyxiii.polyglottoolchainprobe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? fabric {
/*import net.fabricmc.api.ClientModInitializer;
*///?} neoforge {
import net.neoforged.fml.common.Mod;
//?} forge {
/*import net.minecraftforge.fml.common.Mod;
*///?}

//? fabric {
/*public final class ToolchainProbe implements ClientModInitializer {
*///?} neoforge {
@Mod("polyglottoolchainprobe")
public final class ToolchainProbe {
//?} forge {
/*@Mod("polyglottoolchainprobe")
public final class ToolchainProbe {
*///?}
    public static final String MINECRAFT_VERSION = /*$ minecraft_version*/ "1.21.1";
    public static final String LOADER = /*$ loader_name*/ "neoforge";
    public static final Logger LOGGER = LoggerFactory.getLogger("PolyglotToolchainProbe");

    public ToolchainProbe() {
        //? !fabric {
        initialize();
        //?}
    }

    //? fabric {
    /*@Override
    public void onInitializeClient() {
        initialize();
    }
    *///?}

    private static void initialize() {
        LOGGER.info("[PolyglotToolchainProbe] ENTRYPOINT " + LOADER + " " + MINECRAFT_VERSION);
        LOGGER.info(ProbePolicy.startupMarker(LOADER, MINECRAFT_VERSION));
    }
}
