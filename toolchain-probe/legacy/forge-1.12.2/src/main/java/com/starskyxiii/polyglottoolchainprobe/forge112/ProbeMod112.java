package com.starskyxiii.polyglottoolchainprobe.forge112;

import com.starskyxiii.polyglottoolchainprobe.ProbePolicy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = ProbeMod112.MOD_ID,
    name = "Polyglot Toolchain Probe",
    version = "0.1.0",
    clientSideOnly = true
)
public final class ProbeMod112 {
    public static final String MOD_ID = "polyglottoolchainprobe";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ProbePolicy.entrypoint("1.12.2-forge-14.23.5.2864");
        ProbePolicy.verifyPolicy("1.12.2-forge-14.23.5.2864");
    }
}
