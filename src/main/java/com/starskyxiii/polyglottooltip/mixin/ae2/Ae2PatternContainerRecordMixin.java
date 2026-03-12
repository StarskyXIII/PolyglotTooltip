package com.starskyxiii.polyglottooltip.mixin.ae2;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.starskyxiii.polyglottooltip.integration.ae2.PatternAccessSearchUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "appeng.client.gui.me.patternaccess.PatternContainerRecord", remap = false)
public class Ae2PatternContainerRecordMixin {

    @Unique
    private String polyglot$searchName;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cacheSearchName(long serverId, int slots, long order, PatternContainerGroup group, CallbackInfo ci) {
        this.polyglot$searchName = PatternAccessSearchUtil.buildProviderSearchText(group.name());
    }

    @ModifyReturnValue(method = "getSearchName", at = @At("RETURN"))
    private String useExpandedSearchName(String original) {
        return polyglot$searchName != null ? polyglot$searchName : original;
    }
}
