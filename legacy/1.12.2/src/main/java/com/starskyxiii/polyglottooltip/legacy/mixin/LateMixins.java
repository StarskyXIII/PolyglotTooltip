package com.starskyxiii.polyglottooltip.legacy.mixin;

import java.util.Collections;
import java.util.List;

import zone.rong.mixinbooter.ILateMixinLoader;

public final class LateMixins implements ILateMixinLoader {
    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("polyglottooltip.jei.mixins.json");
    }
}
