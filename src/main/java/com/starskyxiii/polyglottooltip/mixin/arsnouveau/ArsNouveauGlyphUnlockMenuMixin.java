package com.starskyxiii.polyglottooltip.mixin.arsnouveau;

import com.starskyxiii.polyglottooltip.integration.arsnouveau.ArsNouveauGlyphSearchHelper;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.hollingsworth.arsnouveau.client.gui.book.GlyphUnlockMenu", remap = false)
public abstract class ArsNouveauGlyphUnlockMenuMixin {

    @Redirect(
            method = "onSearchChanged",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/hollingsworth/arsnouveau/api/spell/AbstractSpellPart;getLocaleName()Ljava/lang/String;"
            )
    )
    private String polyglottooltip$expandGlyphSearchText(@Coerce Object spellPart) {
        return ArsNouveauGlyphSearchHelper.buildSearchText(spellPart);
    }

    @Redirect(
            method = "onSearchChanged",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"
            )
    )
    private boolean polyglottooltip$matchGlyphSearch(String candidate, CharSequence query) {
        return ChineseScriptSearchMatcher.containsMatch(String.valueOf(query), candidate);
    }
}
