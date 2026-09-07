package com.starskyxiii.polyglottoolchainprobe.forge116;

import com.starskyxiii.polyglottoolchainprobe.ProbePolicy;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(ProbeMod116.MOD_ID)
public final class ProbeMod116 {
    public static final String MOD_ID = "polyglottoolchainprobe";

    public ProbeMod116() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ProbePolicy.entrypoint("1.16.5-forge-36.2.42");
            ProbePolicy.verifyPolicy("1.16.5-forge-36.2.42");
        }
    }
}

