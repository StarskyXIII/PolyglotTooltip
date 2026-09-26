package com.starskyxiii.polyglottooltip.mixin.projecte;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.integration.projecte.LegacyProjectESearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Group;

@Pseudo
@Mixin(targets = "moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory", remap = false)
public abstract class LegacyProjectETransmutationInventoryMixin {
    @Group(name = "polyglot_contains", min = 1, max = 1)
    @WrapOperation(method = "doesItemMatchFilter(Lmoze_intel/projecte/api/ItemInfo;)Z",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"),
            remap = false, require = 0)
    private boolean polyglot$matchVanilla(String text, CharSequence query, Operation<Boolean> original, @Coerce Object itemInfo) throws ReflectiveOperationException {
        return LegacyProjectESearch.matches(text, query, itemInfo, (candidate, needle) -> original.call(candidate, needle));
    }

    @Group(name = "polyglot_contains", min = 1, max = 1)
    @WrapOperation(method = "doesItemMatchFilter(Lmoze_intel/projecte/api/ItemInfo;)Z",
            at = @At(value = "INVOKE", target = "Lme/towdium/jecharacters/utils/Match;contains(Ljava/lang/String;Ljava/lang/CharSequence;)Z"),
            remap = false, require = 0)
    private boolean polyglot$matchJec(String text, CharSequence query, Operation<Boolean> original, @Coerce Object itemInfo) throws ReflectiveOperationException {
        return LegacyProjectESearch.matches(text, query, itemInfo, (candidate, needle) -> original.call(candidate, needle));
    }
}
