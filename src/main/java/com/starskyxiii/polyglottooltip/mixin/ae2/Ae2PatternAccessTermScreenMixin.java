package com.starskyxiii.polyglottooltip.mixin.ae2;

import com.starskyxiii.polyglottooltip.integration.ae2.PatternAccessSearchUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends AE2's pattern access terminal search to also match on secondary-language
 * item names for the pattern outputs.
 *
 * <p>In AE2 15.x (1.20.1) the search entry-point is the private
 * {@code itemStackMatchesSearchTerm} method. We hook its return value:
 * if the primary match failed, we decode the pattern's outputs and check their
 * secondary-language names via {@link PatternAccessSearchUtil}.
 */
@Mixin(targets = "appeng.client.gui.me.patternaccess.PatternAccessTermScreen", remap = false)
public class Ae2PatternAccessTermScreenMixin {

    @Inject(method = "itemStackMatchesSearchTerm", at = @At("RETURN"), cancellable = true)
    private void polyglot$matchSecondaryPatternOutputs(ItemStack itemStack,
                                                        String searchTerm,
                                                        CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            Minecraft minecraft = Minecraft.getInstance();
            String extendedText = PatternAccessSearchUtil.extendPatternSearchText("", itemStack, minecraft.level);
            if (extendedText.contains(searchTerm)) {
                cir.setReturnValue(true);
            }
        }
    }
}
