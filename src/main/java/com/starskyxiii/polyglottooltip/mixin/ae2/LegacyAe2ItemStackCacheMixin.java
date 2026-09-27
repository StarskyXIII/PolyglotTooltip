package com.starskyxiii.polyglottooltip.mixin.ae2;
//~ minecraft_116_packages

import com.starskyxiii.polyglottooltip.integration.ae2.LegacyAe2RepoRefresh;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(targets = "appeng.util.item.AEItemStack", remap = false)
public abstract class LegacyAe2ItemStackCacheMixin implements LegacyAe2RepoRefresh.CachedItemStack {
    @Shadow
    private Component displayName;

    @Shadow
    private List<Component> tooltip;

    @Override
    public void polyglottooltip$clearLanguageCaches() {
        displayName = null;
        tooltip = null;
    }
}
