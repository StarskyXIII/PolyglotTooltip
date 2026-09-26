package com.starskyxiii.polyglottooltip.mixin.ae2;
//~ forge_neoforge_package

import appeng.client.gui.me.search.RepoSearch;
import appeng.menu.me.common.GridInventoryEntry;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.starskyxiii.polyglottooltip.integration.ae2.LegacyAe2RegexSearch;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;
import java.util.regex.Pattern;

@OnlyIn(Dist.CLIENT)
@Pseudo
@Mixin(targets = "appeng.client.gui.me.search.SearchPredicates", remap = false)
public abstract class LegacyAe2SearchPredicatesMixin {
    @ModifyReturnValue(method = "createNamePredicate", at = @At("RETURN"), remap = false)
    private static Predicate<GridInventoryEntry> polyglot$wrapNamePredicate(
            Predicate<GridInventoryEntry> original, Pattern pattern) {
        return LegacyAe2RegexSearch.wrapName(pattern, original);
    }

    @ModifyReturnValue(method = "createTooltipPredicate", at = @At("RETURN"), remap = false)
    private static Predicate<GridInventoryEntry> polyglot$wrapTooltipPredicate(
            Predicate<GridInventoryEntry> original, Pattern pattern, RepoSearch repoSearch) {
        return LegacyAe2RegexSearch.wrapTooltip(pattern, repoSearch, original);
    }
}
