package com.starskyxiii.polyglottooltip.mixin.ae2;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import com.starskyxiii.polyglottooltip.integration.ae2.PatternAccessSearchUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "appeng.client.gui.me.patternaccess.PatternContainerRecord", remap = false)
public class Ae2PatternContainerRecordMixin {

    @Unique
    private String polyglot$searchName;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cacheSearchName(long serverId, int slots, long order, PatternContainerGroup group, CallbackInfo ci) {
        this.polyglot$searchName = PatternAccessSearchUtil.buildProviderSearchText(group.name());
    }

    @Inject(method = "getSearchName", at = @At("RETURN"), cancellable = true)
    private void polyglot$extendSearchName(CallbackInfoReturnable<String> cir) {
        if (polyglot$searchName != null) {
            cir.setReturnValue(polyglot$searchName);
        }
    }
}
