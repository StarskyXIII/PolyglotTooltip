package com.starskyxiii.polyglottooltip;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(PolyglotTooltip.MODID)
public class PolyglotTooltip {
    public static final String MODID = "polyglottooltip";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PolyglotTooltip() {
        registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> PolyglotTooltipClient::init);
    }

    private static void registerConfig(ModConfig.Type type, IConfigSpec<?> spec) {
        ModContainer modContainer = ModList.get()
                .getModContainerById(MODID)
                .orElseThrow(() -> new IllegalStateException("Missing mod container for " + MODID));
        modContainer.addConfig(new ModConfig(type, spec, modContainer));
    }
}
