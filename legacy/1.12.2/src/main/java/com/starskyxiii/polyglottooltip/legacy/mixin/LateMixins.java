package com.starskyxiii.polyglottooltip.legacy.mixin;

import java.util.Arrays;
import java.util.List;

import zone.rong.mixinbooter.ILateMixinLoader;

public final class LateMixins implements ILateMixinLoader {
    @Override
    public List<String> getMixinConfigs() {
        return Arrays.asList("polyglottooltip.jei.mixins.json", "polyglottooltip.top.mixins.json",
                "polyglottooltip.hwyla.mixins.json", "polyglottooltip.ae2.mixins.json", "polyglottooltip.rs.mixins.json",
                "polyglottooltip.carbonconfig.mixins.json", "polyglottooltip.mekanism.mixins.json",
                "polyglottooltip.extracells.mixins.json", "polyglottooltip.betterp2p.mixins.json",
                "polyglottooltip.lazyae2.mixins.json", "polyglottooltip.thaumicenergistics.mixins.json",
                "polyglottooltip.mekanismenergistics.mixins.json", "polyglottooltip.notenoughenergistics.mixins.json",
                "polyglottooltip.ae2fc.mixins.json", "polyglottooltip.controlling.mixins.json",
                "polyglottooltip.projecte.mixins.json", "polyglottooltip.integratedterminals.mixins.json",
                "polyglottooltip.modularui.mixins.json", "polyglottooltip.gtceu.mixins.json");
    }
}
