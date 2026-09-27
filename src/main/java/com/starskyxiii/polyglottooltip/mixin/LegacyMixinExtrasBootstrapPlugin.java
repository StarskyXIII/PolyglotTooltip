package com.starskyxiii.polyglottooltip.mixin;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;

public final class LegacyMixinExtrasBootstrapPlugin extends OptionalIntegrationMixinPlugin {
    @Override
    public void onLoad(String mixinPackage) {
        MixinExtrasBootstrap.init();
    }
}
