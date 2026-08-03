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
 * <p>JEMI scans only tooltip indexes 0 and 1 for an mB amount. At RETURN this
 * hook repairs any earlier secondary-name insertion so those names follow it.
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
