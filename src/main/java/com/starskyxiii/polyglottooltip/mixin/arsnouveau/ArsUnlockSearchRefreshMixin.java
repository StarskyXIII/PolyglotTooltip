package com.starskyxiii.polyglottooltip.mixin.arsnouveau;

//? if neoforge && >=1.21.1 && <=1.21.1 {
import com.starskyxiii.polyglottooltip.integration.SearchScreenRefresh;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "com.hollingsworth.arsnouveau.client.gui.book.GlyphUnlockMenu", remap = false)
public abstract class ArsUnlockSearchRefreshMixin implements SearchScreenRefresh.Refreshable {
    @Shadow public String previousString;
    @Shadow public abstract void onSearchChanged(String query);

    @Override
    public void polyglot$refreshSearch() {
        if (previousString != null) {
            onSearchChanged(previousString);
        }
    }
}
//?}

