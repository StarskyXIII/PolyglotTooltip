package com.starskyxiii.polyglottooltip.mixin.ae2;
//~ forge_neoforge_package

import appeng.api.stacks.AEKey;
import com.starskyxiii.polyglottooltip.integration.ae2.LegacyAe2RegexSearch;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@OnlyIn(Dist.CLIENT)
@Pseudo
@Mixin(targets = "appeng.client.gui.me.search.RepoSearch", remap = false)
public abstract class LegacyAe2RepoSearchCacheMixin {
    @Shadow
    @Final
    private Long2BooleanMap cache;

    @Shadow
    @Final
    private Map<AEKey, String> tooltipCache;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void polyglot$registerCache(CallbackInfo ci) {
        LegacyAe2RegexSearch.registerCache(cache);
        LegacyAe2RegexSearch.registerCache(tooltipCache);
    }
}
