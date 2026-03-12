package com.starskyxiii.polyglottooltip.mixin.ae2;

import appeng.api.stacks.AEKey;
import appeng.menu.me.common.GridInventoryEntry;
import com.llamalad7.mixinextras.sugar.Local;
import com.starskyxiii.polyglottooltip.integration.ae2.Ae2SearchPredicate;
import com.starskyxiii.polyglottooltip.integration.ae2.Ae2TooltipSearchPredicate;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.function.Predicate;

@OnlyIn(Dist.CLIENT)
@Mixin(targets = "appeng.client.gui.me.search.RepoSearch", remap = false)
public class Ae2RepoSearchMixin {

    private static final String NAME_SEARCH_PREDICATE_CLASS = "appeng.client.gui.me.search.NameSearchPredicate";
    private static final String TOOLTIPS_SEARCH_PREDICATE_CLASS = "appeng.client.gui.me.search.TooltipsSearchPredicate";

    @Shadow
    @Final
    private Map<AEKey, String> tooltipCache;

    @Redirect(
            method = "getPredicates",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/ArrayList;add(Ljava/lang/Object;)Z"
            ),
            remap = false
    )
    @SuppressWarnings("unchecked")
    private boolean replaceSearchPredicate(java.util.ArrayList<Object> instance, Object original, @Local(name = "part") String part) {
        String className = original.getClass().getName();
        if (NAME_SEARCH_PREDICATE_CLASS.equals(className)) {
            return instance.add(new Ae2SearchPredicate(part, (Predicate<GridInventoryEntry>) original));
        }
        if (TOOLTIPS_SEARCH_PREDICATE_CLASS.equals(className)) {
            return instance.add(new Ae2TooltipSearchPredicate(part.substring(1), tooltipCache));
        }
        return instance.add(original);
    }
}
