package com.starskyxiii.polyglottooltip.mixin.ae2;
//~ minecraft_116_packages

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.starskyxiii.polyglottooltip.integration.ae2.PatternAccessSearchUtil;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "appeng.client.gui.me.interfaceterminal.InterfaceRecord", remap = false)
public class LegacyAe2InterfaceRecordMixin {
    @Unique
    private Component polyglot$providerName;

    @Inject(
            method = "<init>(JIJLnet/minecraft/network/chat/Component;)V",
            at = @At("RETURN"),
            remap = false,
            require = 1
    )
    private void polyglot$captureProviderName(long serverId, int slots, long order,
                                              Component name, CallbackInfo ci) {
        polyglot$providerName = name;
    }

    @ModifyReturnValue(method = "getSearchName", at = @At("RETURN"))
    private String polyglot$expandSearchName(String original) {
        return polyglot$providerName == null
                ? original
                : PatternAccessSearchUtil.buildProviderSearchText(polyglot$providerName);
    }
}
