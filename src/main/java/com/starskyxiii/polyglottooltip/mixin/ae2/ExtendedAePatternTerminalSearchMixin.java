package com.starskyxiii.polyglottooltip.mixin.ae2;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.llamalad7.mixinextras.sugar.Local;
import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Pseudo
@Mixin(targets = "com.github.glodblock.extendedae.client.gui.GuiExPatternTerminal", remap = false)
public class ExtendedAePatternTerminalSearchMixin {
    @Redirect(
            method = "itemStackMatchesSearchTerm(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;Z)Z",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"),
            remap = false,
            require = 0
    )
    private boolean polyglot$matchSecondaryCandidateName(String primaryName,
                                                         CharSequence searchTerm,
                                                         @Local(ordinal = 0) GenericStack candidate) {
        if (primaryName.contains(searchTerm)) {
            return true;
        }
        String query = searchTerm.toString();
        if (ChineseScriptSearchMatcher.containsMatch(query, primaryName)) {
            return true;
        }

        List<String> names;
        if (candidate.what() instanceof AEItemKey itemKey) {
            names = LanguageCache.getInstance().resolveSearchNamesForAll(itemKey.toStack());
        } else {
            names = LanguageCache.getInstance().resolveComponentsForAll(candidate.what().getDisplayName());
        }
        return ChineseScriptSearchMatcher.containsMatch(query, names);
    }
}
