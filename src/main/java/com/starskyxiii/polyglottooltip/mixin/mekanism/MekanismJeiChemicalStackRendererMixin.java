package com.starskyxiii.polyglottooltip.mixin.mekanism;

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Injects into {@code mekanism.client.recipe_viewer.jei.ChemicalStackRenderer#collectTooltips}
 * at RETURN to insert the secondary-language chemical name into JEI's ingredient tooltip list.
 *
 * <p>{@code collectTooltips} builds the tooltip by calling
 * {@code ChemicalStack.appendHoverText}, so by RETURN the first element of {@code tooltip}
 * is the chemical's translatable name component — no Mekanism API import required.
 *
 * <p>The {@code MekanismChemicalStackMixin} on {@code appendHoverText} also fires during
 * this call; {@link SecondaryTooltipUtil#insertSecondaryName}'s duplicate-line guard
 * ensures the secondary name is never added twice.
 */
@Mixin(targets = "mekanism.client.recipe_viewer.jei.ChemicalStackRenderer", remap = false)
public class MekanismJeiChemicalStackRendererMixin {

    @Inject(method = "collectTooltips", at = @At("RETURN"))
    private void onCollectTooltips(Object stack,
                                   List<Component> tooltip,
                                   TooltipFlag tooltipFlag,
                                   CallbackInfo ci) {
        if (tooltip.isEmpty()) return;
        SecondaryTooltipUtil.insertSecondaryName(tooltip, tooltip.get(0));
    }
}
