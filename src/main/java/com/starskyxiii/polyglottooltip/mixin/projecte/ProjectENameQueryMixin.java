package com.starskyxiii.polyglottooltip.mixin.projecte;

import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.search.WrappedSearchMatcher;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

@Pseudo
@Mixin(targets = "moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory", remap = false)
public abstract class ProjectENameQueryMixin {

    @Shadow
    public String filter;

    @Inject(
            method = "doesItemMatchFilter",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void polyglottooltip$matchSecondaryLanguageNames(@Coerce Object info, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || filter == null || filter.isEmpty()) {
            return;
        }

        final ItemStack stack;
        try {
            stack = (ItemStack) info.getClass().getMethod("createStack").invoke(info);
        } catch (Exception ignored) {
            return;
        }

        String primaryName = stack.getHoverName().getString();
        cir.setReturnValue(WrappedSearchMatcher.matches(
                filter,
                primaryName,
                () -> primaryName.toLowerCase(Locale.ROOT).contains(filter),
                () -> LanguageCache.getInstance().resolveSearchNamesForAll(stack)
        ));
    }
}
