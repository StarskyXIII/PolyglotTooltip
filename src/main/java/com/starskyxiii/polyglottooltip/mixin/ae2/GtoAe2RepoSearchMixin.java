package com.starskyxiii.polyglottooltip.mixin.ae2;

import appeng.menu.me.common.GridInventoryEntry;
import com.starskyxiii.polyglottooltip.integration.ae2.Ae2SearchPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Pseudo
@Mixin(targets = "appeng.client.gui.me.search.RepoSearch", remap = false)
public class GtoAe2RepoSearchMixin {
    @Inject(method = "getPredicates(Ljava/lang/String;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true, require = 1, remap = false)
    private void polyglot$wrapGtoPredicates(String clause,
            CallbackInfoReturnable<List<Predicate<GridInventoryEntry>>> cir) {
        List<Predicate<GridInventoryEntry>> original = cir.getReturnValue();
        String[] parts = clause.toLowerCase().trim().split("\\s+");
        if (parts.length != original.size()) return;
        List<Predicate<GridInventoryEntry>> updated = null;
        for (int index = 0; index < original.size(); index++) {
            Predicate<GridInventoryEntry> predicate = original.get(index);
            if (predicate != null && predicate.getClass().getName()
                    .equals("com.gtocore.integration.ae.MultiLangNameSearchPredicate")) {
                if (updated == null) updated = new ArrayList<>(original);
                updated.set(index, new Ae2SearchPredicate(parts[index], predicate));
            }
        }
        if (updated != null) cir.setReturnValue(updated);
    }
}
