package com.starskyxiii.polyglottooltip.mixin.ae2;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.crafting.pattern.EncodedPatternItem;
import com.starskyxiii.polyglottooltip.integration.ae2.PatternAccessSearchUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;

@Mixin(targets = "com.glodblock.github.extendedae.client.gui.GuiAssemblerMatrix", remap = false)
public class Ae2AssemblerMatrixMixin {

    @Shadow
    private Set<ItemStack> matchedStack;

    @Inject(method = "itemStackMatchesSearchTerm", at = @At("HEAD"), cancellable = true)
    private void usePolyglotSearchTokens(
            ItemStack itemStack,
            List<String> searchTokens,
            CallbackInfoReturnable<Boolean> cir
    ) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            cir.setReturnValue(false);
            return;
        }

        var item = itemStack.getItem();
        if (!(item instanceof EncodedPatternItem<?>)) {
            cir.setReturnValue(false);
            return;
        }

        var pattern = PatternDetailsHelper.decodePattern(itemStack, minecraft.level);
        if (pattern == null) {
            cir.setReturnValue(false);
            return;
        }

        for (var output : pattern.getOutputs()) {
            if (output == null || output.what() == null) {
                continue;
            }

            var nameTokens = PatternAccessSearchUtil.buildPatternTokenList(
                    output.what().getDisplayName().getString(),
                    output
            );
            if (matchesAllTokens(searchTokens, nameTokens)) {
                this.matchedStack.add(itemStack);
                cir.setReturnValue(true);
                return;
            }
        }

        for (var input : pattern.getInputs()) {
            if (input == null) {
                continue;
            }

            var possibleInputs = input.getPossibleInputs();
            var primaryInput = possibleInputs.length > 0 ? possibleInputs[0] : null;
            if (primaryInput == null || primaryInput.what() == null) {
                continue;
            }

            var nameTokens = PatternAccessSearchUtil.buildPatternTokenList(
                    primaryInput.what().getDisplayName().getString(),
                    primaryInput
            );
            if (matchesAllTokens(searchTokens, nameTokens)) {
                this.matchedStack.add(itemStack);
                cir.setReturnValue(true);
                return;
            }
        }

        cir.setReturnValue(false);
    }

    private static boolean matchesAllTokens(List<String> searchTokens, List<String> nameTokens) {
        if (searchTokens.isEmpty()) {
            return true;
        }
        if (nameTokens.isEmpty()) {
            return false;
        }

        for (String searchToken : searchTokens) {
            boolean matched = false;
            for (String nameToken : nameTokens) {
                if (nameToken.contains(searchToken)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }

        return true;
    }
}
