package com.starskyxiii.polyglottooltip.mixin.mekanism;

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Injects into {@code mekanism.api.chemical.ChemicalStack#appendHoverText} at RETURN
 * to insert the secondary-language chemical name into any tooltip that is built directly
 * from a ChemicalStack (e.g., custom GUI hover text, tanks, etc.).
 *
 * <p>At RETURN the tooltip list is already populated; {@code tooltip.get(0)} is always
 * the chemical's translatable name component, which is forwarded to
 * {@link SecondaryTooltipUtil#insertSecondaryName} for translation and insertion.
 *
 * <p>If a more specific renderer Mixin (JEI, EMI, AE2) already inserted the secondary
 * name, {@code insertSecondaryName}'s duplicate-line check silently no-ops.
 */
@Mixin(targets = "mekanism.api.chemical.ChemicalStack", remap = false)
public class MekanismChemicalStackMixin {

    @Inject(method = "appendHoverText", at = @At("RETURN"))
    private void onAppendHoverText(Item.TooltipContext tooltipContext,
                                   List<Component> tooltip,
                                   TooltipFlag tooltipFlag,
                                   CallbackInfo ci) {
        if (tooltip.isEmpty()) return;
        Component first = tooltip.get(0);
        // Guard against index-0 not being the chemical name if Mekanism changes tooltip structure.
        if (!(first.getContents() instanceof TranslatableContents)) return;
        SecondaryTooltipUtil.insertSecondaryName(tooltip, first);
    }
}
