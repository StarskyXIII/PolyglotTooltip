package com.starskyxiii.polyglottooltip.mixin.mekanism;

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Keeps JEMI's Mekanism chemical amount line in the first two tooltip entries.
 *
 * <p>EMI's JEMI bridge recognizes chemical amounts by scanning only tooltip
 * indexes 0 and 1 for this exact mB pattern. The broader ChemicalStack mixin
 * may already have inserted secondary names at index 1, so this hook removes
 * and reinserts those names after the amount line when present.
 */
@Mixin(targets = "mekanism.client.recipe_viewer.jei.ChemicalStackRenderer", remap = false)
public class MekanismJeiChemicalStackRendererMixin {

    private static final Pattern JEMI_MILLIBUCKET_AMOUNT = Pattern.compile("(^|\\s)([\\d,]+)\\s*mB$");

    @Inject(
            method = "collectTooltips(Lmekanism/api/chemical/ChemicalStack;Ljava/util/List;Lnet/minecraft/world/item/TooltipFlag;)V",
            at = @At("RETURN"),
            remap = false
    )
    private void onCollectTooltips(@Coerce Object stack,
                                   List<Component> tooltip,
                                   TooltipFlag tooltipFlag,
                                   CallbackInfo ci) {
        if (tooltip.isEmpty()) return;
        SecondaryTooltipUtil.insertSecondaryNameAfterFirstDetailIf(
                tooltip,
                tooltip.get(0),
                line -> JEMI_MILLIBUCKET_AMOUNT.matcher(line.getString()).find()
        );
    }
}
