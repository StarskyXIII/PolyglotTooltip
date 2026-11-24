package com.starskyxiii.polyglottooltip.mixin;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;

public class LegacyMixinExtrasBootstrapPlugin extends OptionalIntegrationMixinPlugin {
    @Override
    public void onLoad(String mixinPackage) {
        MixinExtrasBootstrap.init();
    }
}
