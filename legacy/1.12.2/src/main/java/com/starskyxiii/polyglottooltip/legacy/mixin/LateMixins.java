package com.starskyxiii.polyglottooltip.legacy.mixin;

import java.util.Arrays;
import java.util.List;

import zone.rong.mixinbooter.ILateMixinLoader;

public final class LateMixins implements ILateMixinLoader {
    @Override
    public List<String> getMixinConfigs() {
        return Arrays.asList("polyglottooltip.jei.mixins.json", "polyglottooltip.top.mixins.json",
                "polyglottooltip.hwyla.mixins.json");
    }
}
