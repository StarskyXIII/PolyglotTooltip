package com.starskyxiii.polyglottooltip.mixin.ae2;

import appeng.api.stacks.AEKey;
import appeng.menu.me.common.GridInventoryEntry;
import com.starskyxiii.polyglottooltip.integration.ae2.Ae2SearchPredicate;
import com.starskyxiii.polyglottooltip.integration.ae2.Ae2TooltipSearchPredicate;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@OnlyIn(Dist.CLIENT)
@Mixin(targets = "appeng.client.gui.me.search.RepoSearch", remap = false)
public class Ae2RepoSearchMixin {

    private static final String NAME_SEARCH_PREDICATE_CLASS = "appeng.client.gui.me.search.NameSearchPredicate";
    private static final String TOOLTIPS_SEARCH_PREDICATE_CLASS = "appeng.client.gui.me.search.TooltipsSearchPredicate";
    private static final String GTOCORE_MULTI_LANG_NAME_SEARCH_PREDICATE_CLASS =
            "com.gtocore.integration.ae.MultiLangNameSearchPredicate";

    @Shadow
    @Final
    private Map<AEKey, String> tooltipCache;

    @Inject(method = "getPredicates", at = @At("RETURN"), cancellable = true, remap = false)
    private void polyglottooltip$wrapSearchPredicates(String query,
                                                      CallbackInfoReturnable<List<Predicate<GridInventoryEntry>>> cir) {
        List<Predicate<GridInventoryEntry>> predicates = cir.getReturnValue();
        if (predicates == null || predicates.isEmpty()) {
            return;
        }

        String[] parts = query.toLowerCase().trim().split("\\s+");
        List<Predicate<GridInventoryEntry>> wrappedPredicates = new ArrayList<>(predicates.size());
        boolean changed = false;

        for (int i = 0; i < predicates.size(); i++) {
            Predicate<GridInventoryEntry> original = predicates.get(i);
            String part = i < parts.length ? parts[i] : "";
            Predicate<GridInventoryEntry> wrapped = polyglottooltip$wrapSearchPredicate(part, original);
            wrappedPredicates.add(wrapped);
            changed |= wrapped != original;
        }

        if (changed) {
            cir.setReturnValue(wrappedPredicates);
        }
    }

    private Predicate<GridInventoryEntry> polyglottooltip$wrapSearchPredicate(String part,
                                                                             Predicate<GridInventoryEntry> original) {
        String className = original.getClass().getName();
        if (NAME_SEARCH_PREDICATE_CLASS.equals(className)
                || GTOCORE_MULTI_LANG_NAME_SEARCH_PREDICATE_CLASS.equals(className)) {
            return new Ae2SearchPredicate(part, original);
        }
        if (TOOLTIPS_SEARCH_PREDICATE_CLASS.equals(className) && part.startsWith("#")) {
            return new Ae2TooltipSearchPredicate(part.substring(1), tooltipCache);
        }
        return original;
    }
}
