package com.starskyxiii.polyglottooltip.mixin.ae2;

import appeng.api.stacks.AEKey;
import appeng.menu.me.common.GridInventoryEntry;
import com.llamalad7.mixinextras.sugar.Local;
import com.starskyxiii.polyglottooltip.integration.ae2.Ae2SearchPredicate;
import com.starskyxiii.polyglottooltip.integration.ae2.Ae2TooltipSearchPredicate;
import com.starskyxiii.polyglottooltip.integration.ae2.ForgeAe2RepoSearchLayout;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Pseudo
@Mixin(targets = "appeng.client.gui.me.search.RepoSearch", remap = false)
public abstract class ForgeAe2RepoSearchReturnMixin {
    private static final String NAME = "appeng.client.gui.me.search.NameSearchPredicate";
    private static final String TOOLTIP = "appeng.client.gui.me.search.TooltipsSearchPredicate";

    @Shadow @Final
    private Map<AEKey, String> tooltipCache;

    @Inject(method = "getPredicates(Ljava/lang/String;)Ljava/util/List;", at = @At("RETURN"),
            cancellable = true, require = 1, remap = false)
    private void polyglottooltip$extendPredicates(String clause,
            CallbackInfoReturnable<List<Predicate<GridInventoryEntry>>> callback,
            @Local(index = 2) String[] terms) {
        List<Predicate<GridInventoryEntry>> original = callback.getReturnValue();
        if (original == null || terms == null || original.size() != terms.length) {
            return;
        }
        List<Predicate<GridInventoryEntry>> updated = null;
        for (int index = 0; index < terms.length; index++) {
            String term = terms[index];
            Predicate<GridInventoryEntry> predicate = original.get(index);
            if (term == null || predicate == null) {
                continue;
            }
            String className = predicate.getClass().getName();
            Predicate<GridInventoryEntry> replacement;
            if (TOOLTIP.equals(className) && term.startsWith("#")) {
                replacement = new Ae2TooltipSearchPredicate(term.substring(1), tooltipCache);
            } else if ((term.isEmpty() || "@#$*".indexOf(term.charAt(0)) < 0)
                    && (NAME.equals(className) || ForgeAe2RepoSearchLayout.isGtoNamePredicate(className))) {
                replacement = new Ae2SearchPredicate(term, predicate);
            } else {
                continue;
            }
            if (updated == null) {
                updated = new ArrayList<>(original);
            }
            updated.set(index, replacement);
        }
        if (updated != null) {
            callback.setReturnValue(updated);
        }
    }
}
