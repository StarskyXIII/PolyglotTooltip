package com.starskyxiii.polyglottooltip.mixin.apothicenchanting;

//? if neoforge && >=1.21.1 {
import com.starskyxiii.polyglottooltip.integration.SearchScreenRefresh;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "dev.shadowsoffire.apothic_enchanting.library.EnchLibraryScreen", remap = false)
public abstract class ApothicLibrarySearchRefreshMixin implements SearchScreenRefresh.Refreshable {
    @Shadow
    private void containerChanged() {throw new AssertionError();}

    @Override
    public void polyglot$refreshSearch() {containerChanged();}
}
//?}
