package com.starskyxiii.polyglottooltip.legacy.mixin.betterp2p;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.projecturanus.betterp2p.util.p2p.TunnelInfo;
import com.starskyxiii.polyglottooltip.legacy.integration.betterp2p.BetterP2PSearch;

@Pseudo
@Mixin(targets = "com.projecturanus.betterp2p.client.gui.Filter$5", remap = false)
public abstract class BetterP2PTypeSearchMixin {
    @WrapOperation(method = "invoke(Lcom/projecturanus/betterp2p/client/gui/InfoWrapper;Ljava/util/List;)Z", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lcom/projecturanus/betterp2p/util/p2p/TunnelInfo;getDispName()Ljava/lang/String;"))
    private String polyglot$searchName(TunnelInfo info, Operation<String> original) {
        return BetterP2PSearch.searchText(info.getStack(), original.call(info));
    }
}
