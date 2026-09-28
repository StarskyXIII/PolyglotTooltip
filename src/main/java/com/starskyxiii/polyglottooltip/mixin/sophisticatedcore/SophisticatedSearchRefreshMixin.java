package com.starskyxiii.polyglottooltip.mixin.sophisticatedcore;

//? if neoforge && >=1.21.1 {
import com.starskyxiii.polyglottooltip.integration.SearchScreenRefresh;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase", remap = false)
public abstract class SophisticatedSearchRefreshMixin implements SearchScreenRefresh.Refreshable {
    @Unique
    private String polyglot$query = "";

    @Shadow
    private void onSearchPhraseChange(String query) {throw new AssertionError();}

    @Inject(method = "addSearchBox", at = @At("HEAD"))
    private void polyglot$resetQuery(CallbackInfo callback) {
        polyglot$query = "";
    }

    @Inject(method = "onSearchPhraseChange", at = @At("HEAD"))
    private void polyglot$captureQuery(String query, CallbackInfo callback) {
        polyglot$query = query;
    }

    @Override
    public void polyglot$refreshSearch() {onSearchPhraseChange(polyglot$query);}
}
//?}
